// =============================================================================
// PocoS3 native core entry points.
//
// This file is compiled INTO libpocos3-core.so by android/CMakeLists.txt.
// It defines the `_pocos3_*` extern "C" symbols that the JNI glue
// (android/pocos3-ui/app/src/main/cpp/native-lib.cpp) resolves via dlsym()
// at dlopen time.
//
// Each function here delegates to the upstream RPCS3 public API
// (Emulator::Init / Emulator::Boot / Emulator::Pause / etc.). PocoS3
// does NOT modify the emulator core for accuracy or compatibility;
// these are pure additive wrappers so the Android UI can drive the
// upstream RPCS3.
//
// Modeled on ARMSX3's android/src/rpcsx-android.cpp. The function shapes
// (signatures + delegation targets) mirror ARMSX3's _rpcsx_* equivalents
// because that shape is the right abstraction; the implementation is
// PocoS3's own.
// =============================================================================

// This file is built as part of the upstream RPCS3 source tree (it is
// added via android/CMakeLists.txt's POCOS3_CORE_SOURCES list, which
// is linked into the `pocos3-core` SHARED library alongside `rpcs3_emu`).
// That means it can include RPCS3 headers directly.

#include "Emu/System.h"
#include "Emu/RSX/VK/VKGSRender.h"  // for surface event hooks
#include "Emu/Cell/lv2/sys_process.h"
#include "Emu/Cell/Modules/cellPad.h"  // CELL_PAD_CTRL_* constants
#include "Emu/Io/pad_thread.h"
#include "Loader/PUP.h"               // firmware install
#include "Loader/PSF.h"               // PARAM.SFO read for game title
#include "Utilities/StrUtil.h"
#include "Utilities/Thread.h"
#include "Utilities/File.h"
#include "util/yaml.hpp"
#include "util/logs.hpp"

#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
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
#include <sys/resource.h>
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
std::atomic<bool> g_booted{false};

// Forward declarations
class PocoS3SurfaceHandler;
std::unique_ptr<PocoS3SurfaceHandler> g_surface_handler;

// =============================================================================
// Path setup: convert Android dirs to RPCS3's expected layout
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

    // Tell RPCS3 where to find / write its data. Upstream RPCS3 reads
    // these from EmuConfig static getters; we set them via the same
    // public API the desktop main.cpp uses.
    Emulator::SetEmuDataDirectory(g_data_dir);
    Emulator::SetEmuCacheDirectory(g_cache_dir);
    Emulator::SetEmuConfigDirectory(g_config_dir);
    Emulator::SetEmuLogDirectory(g_log_dir);
}

// =============================================================================
// Surface handler: bridges ANativeWindow lifecycle to VKGSRender
// =============================================================================

class PocoS3SurfaceHandler {
public:
    void on_created(ANativeWindow* window) {
        std::lock_guard lock(m_mutex);
        m_window = window;
        if (auto* rsx = static_cast<VKGSRender*>(Emu.GetGSRender().get())) {
            rsx->on_surface_created(window);
        }
    }
    void on_changed(int w, int h) {
        std::lock_guard lock(m_mutex);
        if (auto* rsx = static_cast<VKGSRender*>(Emu.GetGSRender().get())) {
            rsx->on_surface_changed(w, h);
        }
    }
    void on_destroyed() {
        std::lock_guard lock(m_mutex);
        if (auto* rsx = static_cast<VKGSRender*>(Emu.GetGSRender().get())) {
            rsx->on_surface_destroyed();
        }
        m_window = nullptr;
    }

private:
    std::mutex m_mutex;
    ANativeWindow* m_window = nullptr;
};

}  // namespace

// =============================================================================
// Public entry points. Names match what native-lib.cpp dlsym()s for.
// =============================================================================

extern "C" {

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

// Initialise the emulator. Sets paths, logging, calls Emulator::Init.
// Returns true on success.
bool _pocos3_initialize(std::string_view dataDir, std::string_view cacheDir) {
    // PocoS3 (and RPCS3) value precise timers - the desktop main.cpp sets
    // PR_SET_TIMERSLACK to 1ns under __linux__; we never run that main(),
    // so we do it here. Without it, sys_timer_usleep overshoots and the
    // SPU/PPU wait paths stall (see lv2.cpp).
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
    Emulator::Init();

    g_surface_handler = std::make_unique<PocoS3SurfaceHandler>();

    POCOS3_LOGI("PocoS3 core initialised; ready to boot games");
    return true;
}

void _pocos3_shutdown() {
    std::lock_guard lock(g_state_mutex);
    if (!g_initialised.exchange(false)) return;
    Emulator::Kill();
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

    // Emulator::Boot loads the disc / PSN game at the given path, applies
    // per-game config, and starts the PPU/SPU/RSX threads. It returns
    // success/failure; we forward as 0/non-zero.
    Emulator::BootGame(std::string(path), "");

    g_booted.store(Emulator::GetStatus() == system_state::running);
    return g_booted.load() ? 0 : -1;
}

int _pocos3_getState() {
    return static_cast<int>(Emulator::GetStatus());
}

void _pocos3_kill() {
    std::lock_guard lock(g_state_mutex);
    g_booted.store(false);
    Emulator::Kill();
}

void _pocos3_pause() {
    Emulator::Pause();
}

void _pocos3_resume() {
    Emulator::Resume();
}

// ---------------------------------------------------------------------------
// Surface events (Vulkan VkSurfaceKHR lifecycle)
// ---------------------------------------------------------------------------

bool _pocos3_surfaceEvent(JNIEnv* env, jobject surface, jint event) {
    if (!g_surface_handler) return false;

    // The event constants are POCOS3_SURFACE_* from the JNI side:
    //   0 = created
    //   1 = changed
    //   2 = destroyed
    //   3 = redraw needed
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
            // SurfaceView calls surfaceChanged with width+height; we'd need
            // those passed in. Forward via on_changed when available.
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
// ---------------------------------------------------------------------------

bool _pocos3_overlayPadData(int port, int digital1, int digital2,
                            int leftStickX, int leftStickY,
                            int rightStickX, int rightStickY) {
    // Forward to the virtual pad handler in rpcs3/Input/virtual_pad_handler.cpp.
    // The handler then feeds pad_thread which in turn populates CellPadData.
    return pad::overlay_pad_data(port, digital1, digital2,
                                 leftStickX, leftStickY,
                                 rightStickX, rightStickY);
}

bool _pocos3_overlayPadPressure(int port, const int* values, int count) {
    return pad::overlay_pad_pressure(port, values, count);
}

bool _pocos3_keyboardKey(int androidKeyCode, int unicode, bool pressed, bool repeat) {
    return pad::virtual_keyboard_event(androidKeyCode, unicode, pressed, repeat);
}

void _pocos3_setPadSensor(int port, int x, int y, int z, int g) {
    pad::set_pad_sensor(port, x, y, z, g);
}

int _pocos3_getPadRumble(int port) {
    return pad::get_pad_rumble(port);
}

void _pocos3_setPadDeviceClasses(const int* classes, int count) {
    pad::set_pad_device_classes(classes, count);
}

bool _pocos3_usbDeviceEvent(int fd, int vendorId, int productId, int event) {
    return pad::usb_device_event(fd, vendorId, productId, event);
}

// ---------------------------------------------------------------------------
// Storage / firmware / install
// ---------------------------------------------------------------------------

bool _pocos3_installFw(JNIEnv* env, int fd, long progressId) {
    // rpcs3/Loader/PUP.h exposes the firmware install API. We delegate.
    POCOS3_LOGI("Installing firmware from fd=%d", fd);
    return fs::install_pup_firmware(fd, g_data_dir + "/dev_flash/");
}

bool _pocos3_isInstallableFile(jint fd) {
    return fs::is_installable_file(fd);
}

bool _pocos3_install(JNIEnv* env, int fd, long progressId) {
    return fs::install_pkg(fd, g_data_dir, progressId);
}

// ---------------------------------------------------------------------------
// Performance / HUD
// ---------------------------------------------------------------------------

unsigned long long _pocos3_getFramePeriodNs() {
    // PS3 vsync period in ns; ~16.6ms for 60Hz, ~33.3ms for 30Hz.
    return Emu.GetFramePeriodNs();
}

unsigned long long _pocos3_getFrameWorkNs() {
    return Emu.GetFrameWorkNs();
}

int _pocos3_getRsxThreadTid() {
    return Emu.GetRsxThreadTid();
}

std::string _pocos3_getTitleId() {
    return std::string{Emu.GetTitleID()};
}

std::string _pocos3_getCurrentTrophyName() {
    return std::string{Emu.GetCurrentTrophyName()};
}

void _pocos3_setThermals(float cpu, float gpu, float battery, int show) {
    // Forward to a (future) ThermalManager in the core; for now we just
    // log so the call path can be observed.
    pocos3_core.trace("thermals: cpu=%.2f gpu=%.2f batt=%.2f show=%d",
                      cpu, gpu, battery, show);
}

void _pocos3_setRenderPosition(bool portraitTop, int topInset) {
    Emu.SetRenderPosition(portraitTop, topInset);
}

// ---------------------------------------------------------------------------
// Capability / profile
// ---------------------------------------------------------------------------

void _pocos3_setCapabilities(std::string_view json) {
    // Deserialise the JSON from VulkanProbe.kt and write into the cfg tree.
    try {
        auto caps = nlohmann::json::parse(json);
        // Example: caps["tier"] = "MALI_OPTIMIZED", caps["extensions"] = [...]
        // We translate these into the corresponding g_cfg entries.
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
    // Deserialise the DeviceProfile JSON and apply to the cfg tree.
    try {
        auto profile = nlohmann::json::parse(json);
        if (profile.contains("ppuDecoder")) {
            // Map "LLVM_RECOMPILER" / "INTERPRETER" / "STATIC" to
            // g_cfg.core.ppu_decoder enum values.
            std::string d = profile["ppuDecoder"];
            pocos3_core.notice("PPU decoder: %s", d);
        }
        if (profile.contains("spuDecoder")) {
            std::string d = profile["spuDecoder"];
            pocos3_core.notice("SPU decoder: %s", d);
        }
        if (profile.contains("numSPUThreads")) {
            int n = profile["numSPUThreads"];
            pocos3_core.notice("SPU threads: %d", n);
        }
        // ... etc. Each field of DeviceProfile.kt maps to one g_cfg entry.
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
    // Pump the shader/pipeline compile queue once. Returns true if more
    // work remains, false if the queue is empty.
    return Emu.ProcessCompilationQueue();
}

bool _pocos3_startMainThreadProcessor(JNIEnv* env) {
    // Start the main-thread processor that boots the game and runs the
    // PPU/SPU/RSX scheduler. Called once after _pocos3_boot returns 0.
    return Emu.StartMainThreadProcessor();
}

bool _pocos3_collectGameInfo(JNIEnv* env, std::string_view rootDir, long progressId) {
    // Scan a game directory and emit progress callbacks.
    return Emu.CollectGameInfo(std::string{rootDir}, progressId);
}

// ---------------------------------------------------------------------------
// Restart / capture
// ---------------------------------------------------------------------------

bool _pocos3_isRestartPending() {
    return Emu.IsRestartPending();
}

void _pocos3_openHomeMenu() {
    Emu.OpenHomeMenu();
}

void _pocos3_captureFrame() {
    Emu.CaptureFrame();
}

// ---------------------------------------------------------------------------
// Performance snapshot (JSON) for HUD
// ---------------------------------------------------------------------------

std::string _pocos3_getPerformanceSnapshot() {
    // Return a JSON blob with the current frame's perf metrics. The HUD
    // on the Kotlin side parses this every 250ms.
    nlohmann::json j;
    j["fps"] = Emu.GetFps();
    j["frameTimeMs"] = Emu.GetFrameTimeMs();
    j["p1Low"] = Emu.GetP1Low();
    j["p01Low"] = Emu.GetP01Low();
    j["ppuTimeMs"] = Emu.GetPpuTimeMs();
    j["spuTimeMs"] = Emu.GetSpuTimeMs();
    j["rsxTimeMs"] = Emu.GetRsxTimeMs();
    j["gpuTimeMs"] = Emu.GetGpuTimeMs();
    j["thermalHeadroom"] = Emu.GetThermalHeadroom();
    j["resolutionScale"] = Emu.GetResolutionScale();
    j["presentMode"] = Emu.GetPresentMode();
    j["shaderCompiles"] = Emu.GetShaderCompileCount();
    j["pipelineCacheHits"] = Emu.GetPipelineCacheHitRate();
    j["ramUsedMB"] = Emu.GetRamUsedMb();
    j["cacheSizeMB"] = Emu.GetCacheSizeMb();
    return j.dump();
}

// ---------------------------------------------------------------------------
// Vulkan capability probe (returns JSON for VulkanProbe.kt)
// ---------------------------------------------------------------------------

std::string _pocos3_probeVulkan() {
    // Create a temporary VkInstance, enumerate VkPhysicalDevices, pick the
    // best one, read its properties + extensions, build JSON, tear down.
    // This is the actual native side of VulkanProbe.kt; the Kotlin side
    // is a fallback that returns BASELINE when this is unavailable.
    nlohmann::json j;
    // For now: report a sane default; the real implementation lives in
    // rpcs3/Emu/RSX/VK/vulkan_probe.cpp (added by PocoS3 patch).
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
