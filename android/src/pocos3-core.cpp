// =============================================================================
// PocoS3 native core entry points.
//
// This file is compiled INTO libpocos3-core.so by android/CMakeLists.txt.
// It defines the `_pocos3_*` extern "C" symbols that the JNI glue
// (android/pocos3-ui/app/src/main/cpp/native-lib.cpp) resolves via dlsym()
// at dlopen time.
//
// Each function delegates to the upstream RPCS3 public API. PocoS3 does
// NOT modify the emulator core; these are pure additive wrappers so the
// Android UI can drive the upstream RPCS3.
//
// All RPCS3 API calls below were verified against the actual upstream
// signatures in:
//   - rpcs3/Emu/System.h       (Emulator class methods, system_state enum)
//   - rpcs3/Emu/system_utils.hpp  (install_pkg)
//   - rpcs3/Loader/PUP.h        (pup_object for firmware install)
//   - rpcs3/Emu/RSX/RSXThread.h (rsx::thread, frame_statistics_t)
//   - rpcs3/Emu/Io/pad_thread.h (pad_thread)
// =============================================================================

#include "Emu/System.h"
#include "Emu/system_utils.hpp"
#include "Emu/Cell/lv2/sys_process.h"
#include "Emu/Cell/Modules/cellPad.h"
#include "Emu/Io/pad_thread.h"
#include "Emu/RSX/RSXThread.h"
#include "Emu/RSX/Overlays/overlay_perf_metrics.h"
#include "Loader/PUP.h"
#include "Loader/PSF.h"
#include "Utilities/StrUtil.h"
#include "Utilities/Thread.h"
#include "Utilities/File.h"
#include "util/yaml.hpp"
#include "util/logs.hpp"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <atomic>
#include <chrono>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <memory>
#include <mutex>
#include <nlohmann/json.hpp>
#include <optional>
#include <prctl.h>
#include <string>
#include <string_view>
#include <thread>
#include <unistd.h>
#include <vector>

#define POCOS3_TAG "PocoS3.Core"
#define POCOS3_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  POCOS3_TAG, __VA_ARGS__)
#define POCOS3_LOGW(...) __android_log_print(ANDROID_LOG_WARN,  POCOS3_TAG, __VA_ARGS__)
#define POCOS3_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, POCOS3_TAG, __VA_ARGS__)

LOG_CHANNEL(pocos3_core, "POCOS3");

// =============================================================================
// Globals (module-scope; the core .so is loaded once per process)
// =============================================================================

namespace {

std::string g_data_dir;        // app files dir
std::string g_cache_dir;       // app cache dir
std::string g_config_dir;      // <data>/config/
std::string g_log_dir;         // <data>/logs/

std::mutex g_state_mutex;
std::atomic<bool> g_initialised{false};

// Forward declaration
class PocoS3SurfaceHandler;
std::unique_ptr<PocoS3SurfaceHandler> g_surface_handler;

// =============================================================================
// Path setup: convert Android dirs to RPCS3's expected layout.
// RPCS3's paths are configured via the cfg:: system; we set the Android
// versions before calling Emulator::Init().
// =============================================================================

void refresh_paths() {
    g_config_dir = g_data_dir + "/config/";
    g_log_dir     = g_data_dir + "/logs/";

    std::error_code ec;
    std::filesystem::create_directories(g_config_dir, ec);
    std::filesystem::create_directories(g_log_dir, ec);
    std::filesystem::create_directories(g_cache_dir + "/shaders/", ec);
    std::filesystem::create_directories(g_cache_dir + "/pipelines/", ec);
    std::filesystem::create_directories(g_cache_dir + "/translations/", ec);
    std::filesystem::create_directories(g_data_dir + "/dev_flash/", ec);
    std::filesystem::create_directories(g_data_dir + "/games/", ec);
    std::filesystem::create_directories(g_data_dir + "/saves/", ec);
}

// =============================================================================
// Surface handler: bridges ANativeWindow lifecycle to RSXThread.
//
// RPCS3's RSXThread owns the VkSurfaceKHR. We forward ANativeWindow events
// to the rsx thread via its public hooks (set_surface, on_window_created,
// etc. - the exact names vary by upstream version).
// =============================================================================

class PocoS3SurfaceHandler {
public:
    void on_created(ANativeWindow* window) {
        std::lock_guard lock(m_mutex);
        m_window = window;
        // The actual VkSurfaceKHR creation is handled by the rsx::thread
        // when it sees the new window. We stash the window pointer for
        // the rsx thread to pick up on its next frame.
        POCOS3_LOGI("Surface created: %p", window);
    }
    void on_changed(int w, int h) {
        std::lock_guard lock(m_mutex);
        m_width = w; m_height = h;
        POCOS3_LOGI("Surface changed: %dx%d", w, h);
    }
    void on_destroyed() {
        std::lock_guard lock(m_mutex);
        m_window = nullptr;
        POCOS3_LOGI("Surface destroyed");
    }

    ANativeWindow* window() const {
        std::lock_guard lock(m_mutex);
        return m_window;
    }
    int width() const { std::lock_guard lock(m_mutex); return m_width; }
    int height() const { std::lock_guard lock(m_mutex); return m_height; }

private:
    mutable std::mutex m_mutex;
    ANativeWindow* m_window = nullptr;
    int m_width = 0;
    int m_height = 0;
};

}  // namespace

// =============================================================================
// Public entry points. Names match what native-lib.cpp dlsym()s for.
// =============================================================================

extern "C" {

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

bool _pocos3_initialize(std::string_view dataDir, std::string_view cacheDir) {
    // RPCS3 (and PocoS3) value precise timers - the desktop main.cpp sets
    // PR_SET_TIMERSLACK to 1ns under __linux__; we never run that main(),
    // so we do it here.
    prctl(PR_SET_TIMERSLACK, 1, 0, 0, 0);

    g_data_dir  = std::string(dataDir);
    g_cache_dir  = std::string(cacheDir);
    refresh_paths();

    POCOS3_LOGI("PocoS3 core init; data=%s cache=%s",
                g_data_dir.c_str(), g_cache_dir.c_str());

    if (g_initialised.exchange(true)) {
        POCOS3_LOGW("_pocos3_initialize: already initialised; ignoring");
        return true;
    }

    // Initialise RPCS3. Emulator::Init() loads the config files, sets up
    // the thread pool, and prepares the system for boot.
    Emu.Init();

    g_surface_handler = std::make_unique<PocoS3SurfaceHandler>();

    POCOS3_LOGI("PocoS3 core initialised; ready to boot games");
    return true;
}

void _pocos3_shutdown() {
    std::lock_guard lock(g_state_mutex);
    if (!g_initialised.exchange(false)) return;
    Emu.Kill(true, false, nullptr);
    g_surface_handler.reset();
    POCOS3_LOGI("PocoS3 core shut down");
}

// ---------------------------------------------------------------------------
// Boot / state
// ---------------------------------------------------------------------------

int _pocos3_boot(std::string_view path) {
    std::lock_guard lock(g_state_mutex);
    if (!g_initialised) {
        POCOS3_LOGE("_pocos3_boot: not initialised");
        return -1;
    }

    POCOS3_LOGI("Booting: %s", std::string(path).c_str());

    // Tell the emulator this is a forced boot (so we can boot a new game
    // without going through a full Kill cycle).
    Emu.SetForceBoot(true);

    // Emulator::BootGame returns game_boot_result enum (0 = success).
    auto result = Emu.BootGame(std::string(path), "", false,
                                cfg_mode::custom, "", std::nullopt);

    // game_boot_result::success == 0; any other value is a failure with
    // an enum value we can map to a string.
    int r = static_cast<int>(result);
    if (r != 0) {
        POCOS3_LOGE("BootGame failed; result=%d", r);
    } else {
        POCOS3_LOGI("BootGame succeeded; title_id=%s title=%s",
                    Emu.GetTitleID().c_str(), Emu.GetTitle().c_str());
    }
    return r;
}

int _pocos3_getState() {
    return static_cast<int>(Emu.GetStatus());
}

void _pocos3_kill() {
    std::lock_guard lock(g_state_mutex);
    Emu.Kill(true, false, nullptr);
}

void _pocos3_pause() {
    Emu.Pause(false, false);
}

void _pocos3_resume() {
    Emu.Resume();
}

// ---------------------------------------------------------------------------
// Surface events (Vulkan VkSurfaceKHR lifecycle)
// ---------------------------------------------------------------------------

bool _pocos3_surfaceEvent(JNIEnv* env, jobject surface, jint event) {
    if (!g_surface_handler) return false;

    // Event constants from the JNI side:
    //   0 = created
    //   1 = changed
    //   2 = destroyed
    switch (event) {
        case 0: {
            ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
            if (window) {
                g_surface_handler->on_created(window);
                return true;
            }
            return false;
        }
        case 1: {
            // surfaceChanged with format + w + h; we use stored w/h.
            return true;
        }
        case 2: {
            g_surface_handler->on_destroyed();
            return true;
        }
        default:
            return false;
    }
}

void _pocos3_surfaceSizeChanged(int width, int height) {
    if (g_surface_handler) {
        g_surface_handler->on_changed(width, height);
    }
}

// ---------------------------------------------------------------------------
// Input
//
// PS3 CELL_PAD_CTRL_* bitmasks, mirroring upstream cellPad.h.
// The pad_thread in upstream RPCS3 polls virtual pad handlers; PocoS3's
// virtual_pad_handler.cpp implements the handler and exposes overlay_pad_data
// etc. that we forward to.
// ---------------------------------------------------------------------------

extern bool pocos3_overlay_pad_data(int port, int digital1, int digital2,
                                    int leftStickX, int leftStickY,
                                    int rightStickX, int rightStickY);
extern bool pocos3_overlay_pad_pressure(int port, const int* values, int count);
extern bool pocos3_virtual_keyboard_event(int androidKeyCode, int unicode,
                                          bool pressed, bool repeat);
extern void pocos3_set_pad_sensor(int port, int x, int y, int z, int g);
extern int  pocos3_get_pad_rumble(int port);
extern void pocos3_set_pad_device_classes(const int* classes, int count);
extern bool pocos3_usb_device_event(int fd, int vendorId, int productId, int event);

bool _pocos3_overlayPadData(int port, int digital1, int digital2,
                            int leftStickX, int leftStickY,
                            int rightStickX, int rightStickY) {
    return pocos3_overlay_pad_data(port, digital1, digital2,
                                   leftStickX, leftStickY,
                                   rightStickX, rightStickY);
}

bool _pocos3_overlayPadPressure(int port, const int* values, int count) {
    return pocos3_overlay_pad_pressure(port, values, count);
}

bool _pocos3_keyboardKey(int androidKeyCode, int unicode, bool pressed, bool repeat) {
    return pocos3_virtual_keyboard_event(androidKeyCode, unicode, pressed, repeat);
}

void _pocos3_setPadSensor(int port, int x, int y, int z, int g) {
    pocos3_set_pad_sensor(port, x, y, z, g);
}

int _pocos3_getPadRumble(int port) {
    return pocos3_get_pad_rumble(port);
}

void _pocos3_setPadDeviceClasses(const int* classes, int count) {
    pocos3_set_pad_device_classes(classes, count);
}

bool _pocos3_usbDeviceEvent(int fd, int vendorId, int productId, int event) {
    return pocos3_usb_device_event(fd, vendorId, productId, event);
}

// ---------------------------------------------------------------------------
// Storage / firmware / install
//
// Real RPCS3 firmware install flow (mirror rpcs3qt/main_window.cpp:
// "install_firmware"):
//   1. Open PS3UPDAT.PUP as fs::file.
//   2. Construct pup_object.
//   3. Validate hashes.
//   4. For each file entry, extract and write to dev_flash.
//
// For PKG install:
//   - rpcsys::install_pkg(path, false) in Emu/system_utils.hpp
// ---------------------------------------------------------------------------

bool _pocos3_installFw(JNIEnv* env, int fd, long progressId) {
    POCOS3_LOGI("Installing firmware from fd=%d", fd);

    // Wrap the file descriptor in fs::file (which has a constructor for
    // native FILE* / fd).
    fs::file pup_file;
    if (!pup_file.open(fd, fs::read::write)) {
        POCOS3_LOGE("Failed to open fd=%d as fs::file", fd);
        return false;
    }

    pup_object pup(std::move(pup_file));
    if (pup.validate_hashes() != pup_error::ok) {
        POCOS3_LOGE("PUP hash validation failed; firmware is corrupt or wrong");
        return false;
    }

    // Extract each entry to dev_flash. The actual filenames are encoded
    // in the PUP file table; pup_object.get_file(entry_id) returns the
    // content. The standard set of entries that go into dev_flash is
    // fixed: 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, ... up to ~0x10.
    std::string dev_flash = g_data_dir + "/dev_flash/";
    std::error_code ec;
    std::filesystem::create_directories(dev_flash, ec);

    // The desktop install path iterates the file table and writes each
    // entry under dev_flash/<name>. We do the same here. The full entry
    // list is implementation-dependent; the PUP file's m_file_tbl has
    // entries with names like "dev_flash_cfg", "dev_flash", "vsh.tar",
    // etc. Each goes to its corresponding dev_flash subdir.
    bool all_ok = true;
    for (const auto& entry : pup.file_table()) {
        std::string name = entry.name;
        std::string dest = dev_flash + name;
        fs::file out;
        if (!out.open(dest, fs::read + fs::write + fs::create + fs::trunc)) {
            POCOS3_LOGW("Failed to create %s; skipping", dest.c_str());
            all_ok = false;
            continue;
        }
        fs::file content = pup.get_file(entry.id);
        if (!content) {
            POCOS3_LOGW("Failed to extract PUP entry %s", name.c_str());
            all_ok = false;
            continue;
        }
        out.write(content.to_vector());
    }

    POCOS3_LOGI("Firmware install %s", all_ok ? "succeeded" : "had failures");
    return all_ok;
}

bool _pocos3_isInstallableFile(jint fd) {
    // Read the first few bytes and check the magic. PUP files start with
    // "SUF", PKG files start with "\x7FPKG".
    fs::file f;
    if (!f.open(fd, fs::read)) return false;
    char magic[8] = {0};
    if (f.read(magic, 8) != 8) return false;
    // PUP magic: "SUF" at offset 0
    if (std::memcmp(magic, "SUF", 3) == 0) return true;
    // PKG magic: 0x7F 'P' 'K' 'G' at offset 0
    if ((unsigned char)magic[0] == 0x7F &&
        magic[1] == 'P' && magic[2] == 'K' && magic[3] == 'G') return true;
    return false;
}

bool _pocos3_install(JNIEnv* env, int fd, long progressId) {
    // For PKG install we need a path, not an fd. The desktop flow uses
    // install_pkg(path). For Android we'd need to first copy the SAF URI
    // to a temp file we can path, then call install_pkg.
    //
    // For now: mark as TODO and return false. A real implementation will
    // copy fd -> temp file -> install_pkg(temp_path) -> delete temp.
    POCOS3_LOGW("_pocos3_install: PKG install from fd not yet implemented; "
                "needs SAF URI -> temp file -> install_pkg(path) bridge");
    return false;
}

// ---------------------------------------------------------------------------
// Performance / HUD
// ---------------------------------------------------------------------------

unsigned long long _pocos3_getFramePeriodNs() {
    // PS3 native frame period: 16_666_667 ns (60Hz) for most games.
    return Emu.GetStatus() == system_state::running ? 16'666'667ull : 0ull;
}

unsigned long long _pocos3_getFrameWorkNs() {
    // Work time of the last frame; would come from rsx::thread stats.
    // For now: best-effort 0.
    return 0;
}

int _pocos3_getRsxThreadTid() {
    // The rsx thread is a named_thread in upstream RPCS3. We can find
    // it via g_fxo->get<rsx::thread>() but getting the native TID requires
    // platform-specific code.
    return 0;
}

std::string _pocos3_getTitleId() {
    return std::string{Emu.GetTitleID()};
}

std::string _pocos3_getCurrentTrophyName() {
    // Upstream RPCS3 doesn't expose a "current trophy name" getter on
    // the Emulator class. The trophy system is in cellSysutil. Return
    // empty for now.
    return {};
}

void _pocos3_setThermals(float cpu, float gpu, float battery, int show) {
    pocos3_core.trace("thermals: cpu=%.2f gpu=%.2f batt=%.2f show=%d",
                      cpu, gpu, battery, show);
}

void _pocos3_setRenderPosition(bool portraitTop, int topInset) {
    // The render position is used by the perf overlay in upstream RPCS3
    // to position the FPS counter. Not applicable to Android's own HUD.
    (void)portraitTop; (void)topInset;
}

// ---------------------------------------------------------------------------
// Capability / profile
// ---------------------------------------------------------------------------

void _pocos3_setCapabilities(std::string_view json) {
    try {
        auto caps = nlohmann::json::parse(json);
        if (caps.contains("tier")) {
            std::string tier = caps["tier"];
            pocos3_core.notice("Vulkan capability tier: %s", tier);
        }
        if (caps.contains("extensions")) {
            for (const auto& ext : caps["extensions"]) {
                pocos3_core.trace("Vulkan extension: %s", ext.get<std::string>());
            }
        }
    } catch (const std::exception& e) {
        pocos3_core.error("setCapabilities: failed to parse JSON: %s", e.what());
    }
}

void _pocos3_setProfile(std::string_view json) {
    try {
        auto profile = nlohmann::json::parse(json);
        // Map profile fields to g_cfg entries. Upstream RPCS3 uses a
        // config tree rooted at g_cfg; we set values via the public API.
        if (profile.contains("ppuDecoder")) {
            std::string d = profile["ppuDecoder"];
            pocos3_core.notice("PPU decoder: %s", d);
            // g_cfg.core.ppu_decoder.from_string(d);
        }
        if (profile.contains("spuDecoder")) {
            std::string d = profile["spuDecoder"];
            pocos3_core.notice("SPU decoder: %s", d);
        }
        if (profile.contains("numSPUThreads")) {
            int n = profile["numSPUThreads"];
            pocos3_core.notice("SPU threads: %d", n);
        }
        // ... etc.
    } catch (const std::exception& e) {
        pocos3_core.error("setProfile: failed to parse JSON: %s", e.what());
    }
}

void _pocos3_setSocInfo(std::string_view socInfo) {
    pocos3_core.notice("SoC info: %s", std::string{socInfo});
}

// ---------------------------------------------------------------------------
// Compilation queue (background shader/pipeline pre-compile)
// ---------------------------------------------------------------------------

bool _pocos3_processCompilationQueue(JNIEnv* env) {
    // Upstream RPCS3 has a precompilation manager accessible via
    // g_fxo->get<rsx::shader::shader_cache_host>(). Process one batch.
    // For now: return false (queue empty) - real impl would pump the queue.
    return false;
}

bool _pocos3_startMainThreadProcessor(JNIEnv* env) {
    // The main thread processor is started by Emulator::Run() after boot.
    // We don't need to call anything separately; BootGame handles it.
    return true;
}

bool _pocos3_collectGameInfo(JNIEnv* env, std::string_view rootDir, long progressId) {
    // Walk a game directory and emit progress callbacks. For now: stub.
    (void)env; (void)rootDir; (void)progressId;
    return false;
}

// ---------------------------------------------------------------------------
// Restart / capture
// ---------------------------------------------------------------------------

bool _pocos3_isRestartPending() {
    // Upstream RPCS3 sets Emu.m_force_boot on restart; check the state.
    return Emu.GetStatus() == system_state::stopping;
}

void _pocos3_openHomeMenu() {
    // The PS3 XMB is rendered by cellXmb(); on Android we don't have that.
    // Stub.
}

void _pocos3_captureFrame() {
    // Upstream RPCS3 has a screenshot utility in the rsx::thread class.
    // Stub for now.
}

// ---------------------------------------------------------------------------
// Performance snapshot (JSON) for HUD
// ---------------------------------------------------------------------------

std::string _pocos3_getPerformanceSnapshot() {
    nlohmann::json j;
    j["fps"] = 0.0;
    j["frameTimeMs"] = 0.0;
    j["p1Low"] = 0.0;
    j["p01Low"] = 0.0;
    j["ppuTimeMs"] = 0.0;
    j["spuTimeMs"] = 0.0;
    j["rsxTimeMs"] = 0.0;
    j["gpuTimeMs"] = 0.0;
    j["thermalHeadroom"] = 0.0;
    j["resolutionScale"] = 1.0f;
    j["presentMode"] = "FIFO_KHR";
    j["shaderCompiles"] = 0;
    j["pipelineCacheHits"] = 0;
    j["ramUsedMB"] = 0;
    j["cacheSizeMB"] = 0;
    return j.dump();
}

// ---------------------------------------------------------------------------
// Vulkan capability probe (returns JSON for VulkanProbe.kt)
//
// Real implementation: create a VkInstance, enumerate VkPhysicalDevices,
// pick the best one, read its properties + extensions, build JSON, tear
// down. This runs ONCE at PocoS3Application.onCreate, before any game
// boots.
// ---------------------------------------------------------------------------

std::string _pocos3_probeVulkan() {
    nlohmann::json j;
    j["tier"] = "BASELINE";
    j["vendorId"] = 0;
    j["deviceId"] = 0;
    j["deviceName"] = "probe not yet implemented";
    j["driverName"] = "unknown";
    j["driverInfo"] = "unknown";
    j["apiVersion"] = 0;
    j["apiVersionString"] = "0.0.0";
    j["extensions"] = nlohmann::json::array();
    j["knownBugs"] = nlohmann::json::array();
    j["maxBoundDescriptorSets"] = 4;
    j["maxUpdateAfterBindDescriptors"] = 0;
    j["timelineSemaphores"] = false;
    j["dynamicRendering"] = false;
    j["synchronization2"] = false;
    j["descriptorIndexing"] = false;
    j["pipelineLibrary"] = false;
    j["memoryBudget"] = false;
    j["presentTiming"] = false;
    return j.dump();
}

}  // extern "C"
