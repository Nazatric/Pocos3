// =============================================================================
// PocoS3 JNI glue (the small Gradle-built shim).
//
// Built by Gradle's externalNativeBuild (see app/build.gradle.kts). This
// is the *small* glue: it dlopen()s libpocos3-core.so (built separately by
// android/configure.sh) and resolves the PocoS3Api function-pointer table
// via dlsym(). JNI methods on com.pocos3.runtime.PocoS3Core land here.
//
// Modeled on ARMSX3's android/armsx3-ui/app/src/main/cpp/native-lib.cpp.
// The PocoS3Api struct shape mirrors ARMSX3's RPCSXApi because that shape
// is the right abstraction; the implementation is PocoS3's own.
// =============================================================================

#include <algorithm>
#include <android/api-level.h>
#include <android/dlext.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <elf.h>
#include <jni.h>
#include <optional>
#include <string>
#include <string_view>
#include <sys/resource.h>
#include <unistd.h>
#include <utility>
#include <vector>

#if defined(__aarch64__)
// Adrenotools is optional - only built into the glue if POCOS3_WITH_ADRENOTOOLS=ON
// at CMake time. On Mali devices it is unused; the include is guarded.
#ifdef POCOS3_WITH_ADRENOTOOLS
#include <adrenotools/driver.h>
#include <adrenotools/priv.h>
#endif
#endif

#define POCOS3_LOG(prio, msg) __android_log_print(prio, "PocoS3", "%s", (msg).c_str())
#define POCOS3_LOGF(prio, fmt, ...) __android_log_print(prio, "PocoS3", fmt, ##__VA_ARGS__)

// =============================================================================
// PocoS3Api - the function-pointer table the JNI glue resolves via dlsym().
// =============================================================================
// Each field corresponds to an extern "C" symbol named `_pocos3_<fnname>`
// in libpocos3-core.so. Required fields are populated via dlsym at
// nativeInitialise; missing required symbols cause dlopen to fail.

struct PocoS3Api {
    // Lifecycle
    bool (*initialize)(std::string_view dataDir, std::string_view cacheDir);
    void (*shutdown)();

    // Boot / state
    int  (*boot)(std::string_view path);
    int  (*getState)();
    void (*kill)();
    void (*pause)();
    void (*resume)();

    // Surface (Vulkan VkSurfaceKHR lives in the core, not here)
    bool (*surfaceEvent)(JNIEnv* env, jobject surface, jint event);
    void (*surfaceSizeChanged)(int width, int height);

    // Input
    bool (*overlayPadData)(int port, int digital1, int digital2,
                           int leftStickX, int leftStickY,
                           int rightStickX, int rightStickY);
    bool (*overlayPadPressure)(int port, const int* values, int count);
    bool (*keyboardKey)(int androidKeyCode, int unicode, bool pressed, bool repeat);
    void (*setPadSensor)(int port, int x, int y, int z, int g);
    int  (*getPadRumble)(int port);
    void (*setPadDeviceClasses)(const int* classes, int count);
    bool (*usbDeviceEvent)(int fd, int vendorId, int productId, int event);

    // Storage / firmware / install
    bool (*installFw)(JNIEnv* env, int fd, long progressId);
    bool (*isInstallableFile)(jint fd);
    jstring (*getDirInstallPath)(JNIEnv* env, jint fd);
    jstring (*probePkgInfo)(JNIEnv* env, jint fd);
    bool (*install)(JNIEnv* env, int fd, long progressId);

    // Performance / HUD
    unsigned long long (*getFramePeriodNs)();
    unsigned long long (*getFrameWorkNs)();
    int  (*getRsxThreadTid)();
    std::string (*getTitleId)();
    std::string (*getCurrentTrophyName)();
    void (*setThermals)(float cpu, float gpu, float battery, int show);
    void (*setRenderPosition)(bool portraitTop, int topInset);

    // Capability / profile
    void (*setCapabilities)(std::string_view json);
    void (*setProfile)(std::string_view json);
    void (*setSocInfo)(std::string_view socInfo);

    // Compilation queue
    bool (*processCompilationQueue)(JNIEnv* env);
    bool (*startMainThreadProcessor)(JNIEnv* env);
    bool (*collectGameInfo)(JNIEnv* env, std::string_view rootDir, long progressId);

    // Restart / capture
    bool (*isRestartPending)();
    void (*openHomeMenu)();
    void (*captureFrame)();

    // Performance snapshot JSON for HUD
    std::string (*getPerformanceSnapshot)();

    // Vulkan capability probe - returns JSON
    std::string (*probeVulkan)();
};

// =============================================================================
// Globals
// =============================================================================

namespace {
void* g_core_handle = nullptr;
PocoS3Api g_api{};
bool g_initialised = false;
std::string g_cache_dir;
std::string g_data_dir;
}  // namespace

// =============================================================================
// dlopen + dlsym resolution
// =============================================================================

namespace {

bool resolve_api(void* handle, PocoS3Api& api) {
    // dlsym lookups. Each lookup: the symbol name is _pocos3_<fnname>.
    // Required = failure if missing. Optional = warning if missing.
    struct ResolveResult { bool ok; void* ptr; };
    auto resolve = [handle](const char* name, bool required) -> ResolveResult {
        void* p = dlsym(handle, name);
        if (!p && required) {
            POCOS3_LOGF(ANDROID_LOG_ERROR,
                        "dlsym: required symbol %s not found in libpocos3-core.so",
                        name);
        } else if (!p) {
            POCOS3_LOGF(ANDROID_LOG_WARN,
                        "dlsym: optional symbol %s not found (older core .so?)",
                        name);
        }
        return {p != nullptr || !required, p};
    };

    // Required: lifecycle, boot, state, surface, pad
    auto r = resolve("_pocos3_initialize", true);
    if (!r.ok) return false; else api.initialize = (bool(*)(std::string_view, std::string_view))r.ptr;
    r = resolve("_pocos3_shutdown", true);
    if (!r.ok) return false; else api.shutdown = (void(*)())r.ptr;
    r = resolve("_pocos3_boot", true);
    if (!r.ok) return false; else api.boot = (int(*)(std::string_view))r.ptr;
    r = resolve("_pocos3_getState", true);
    if (!r.ok) return false; else api.getState = (int(*)())r.ptr;
    r = resolve("_pocos3_kill", true);
    if (!r.ok) return false; else api.kill = (void(*)())r.ptr;
    r = resolve("_pocos3_pause", true);
    if (!r.ok) return false; else api.pause = (void(*)())r.ptr;
    r = resolve("_pocos3_resume", true);
    if (!r.ok) return false; else api.resume = (void(*)())r.ptr;
    r = resolve("_pocos3_surfaceEvent", true);
    if (!r.ok) return false; else api.surfaceEvent = (bool(*)(JNIEnv*, jobject, jint))r.ptr;
    r = resolve("_pocos3_overlayPadData", true);
    if (!r.ok) return false; else api.overlayPadData = (bool(*)(int, int, int, int, int, int, int))r.ptr;

    // Optional
    r = resolve("_pocos3_surfaceSizeChanged", false); api.surfaceSizeChanged = (void(*)(int,int))r.ptr;
    r = resolve("_pocos3_overlayPadPressure", false); api.overlayPadPressure = (bool(*)(int, const int*, int))r.ptr;
    r = resolve("_pocos3_keyboardKey", false); api.keyboardKey = (bool(*)(int, int, bool, bool))r.ptr;
    r = resolve("_pocos3_setPadSensor", false); api.setPadSensor = (void(*)(int, int, int, int, int))r.ptr;
    r = resolve("_pocos3_getPadRumble", false); api.getPadRumble = (int(*)(int))r.ptr;
    r = resolve("_pocos3_setPadDeviceClasses", false); api.setPadDeviceClasses = (void(*)(const int*, int))r.ptr;
    r = resolve("_pocos3_usbDeviceEvent", false); api.usbDeviceEvent = (bool(*)(int, int, int, int))r.ptr;
    r = resolve("_pocos3_installFw", false); api.installFw = (bool(*)(JNIEnv*, int, long))r.ptr;
    r = resolve("_pocos3_isInstallableFile", false); api.isInstallableFile = (bool(*)(jint))r.ptr;
    r = resolve("_pocos3_install", false); api.install = (bool(*)(JNIEnv*, int, long))r.ptr;
    r = resolve("_pocos3_setThermals", false); api.setThermals = (void(*)(float, float, float, int))r.ptr;
    r = resolve("_pocos3_setRenderPosition", false); api.setRenderPosition = (void(*)(bool, int))r.ptr;
    r = resolve("_pocos3_setCapabilities", false); api.setCapabilities = (void(*)(std::string_view))r.ptr;
    r = resolve("_pocos3_setProfile", false); api.setProfile = (void(*)(std::string_view))r.ptr;
    r = resolve("_pocos3_setSocInfo", false); api.setSocInfo = (void(*)(std::string_view))r.ptr;
    r = resolve("_pocos3_processCompilationQueue", false); api.processCompilationQueue = (bool(*)(JNIEnv*))r.ptr;
    r = resolve("_pocos3_startMainThreadProcessor", false); api.startMainThreadProcessor = (bool(*)(JNIEnv*))r.ptr;
    r = resolve("_pocos3_collectGameInfo", false); api.collectGameInfo = (bool(*)(JNIEnv*, std::string_view, long))r.ptr;
    r = resolve("_pocos3_isRestartPending", false); api.isRestartPending = (bool(*)())r.ptr;
    r = resolve("_pocos3_openHomeMenu", false); api.openHomeMenu = (void(*)())r.ptr;
    r = resolve("_pocos3_captureFrame", false); api.captureFrame = (void(*)())r.ptr;
    r = resolve("_pocos3_getPerformanceSnapshot", false); api.getPerformanceSnapshot = (std::string(*)())r.ptr;
    r = resolve("_pocos3_probeVulkan", false); api.probeVulkan = (std::string(*)())r.ptr;

    return true;
}

std::string jstr(JNIEnv* env, jstring j) {
    if (!j) return {};
    const char* c = env->GetStringUTFChars(j, nullptr);
    if (!c) return {};
    std::string s(c);
    env->ReleaseStringUTFChars(j, c);
    return s;
}

}  // namespace

// =============================================================================
// JNI entry points (called from Kotlin via com.pocos3.runtime.PocoS3Core)
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeInitialise(
        JNIEnv* env, jclass, jstring jCacheDir, jstring jDataDir) {
    if (g_initialised) return JNI_TRUE;

    g_cache_dir = jstr(env, jCacheDir);
    g_data_dir = jstr(env, jDataDir);
    POCOS3_LOGF(ANDROID_LOG_INFO, "PocoS3 glue init; cache=%s data=%s",
                g_cache_dir.c_str(), g_data_dir.c_str());

    // dlopen the core .so. The android_dlopen_ext variant would give us
    // namespace isolation; plain dlopen works because both .so files live
    // in the same linker namespace by virtue of being packaged together.
    g_core_handle = dlopen("libpocos3-core.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_core_handle) {
        const char* err = dlerror();
        POCOS3_LOGF(ANDROID_LOG_ERROR,
                    "dlopen(libpocos3-core.so) failed: %s",
                    err ? err : "(no error)");
        POCOS3_LOGF(ANDROID_LOG_ERROR,
                    "Did you run ./android/configure.sh? See BUILDING.md.");
        return JNI_FALSE;
    }

    if (!resolve_api(g_core_handle, g_api)) {
        POCOS3_LOG(ANDROID_LOG_ERROR,
                   "PocoS3Api table did not resolve - core .so is incompatible");
        return JNI_FALSE;
    }

    if (g_api.initialize) {
        if (!g_api.initialize(g_data_dir, g_cache_dir)) {
            POCOS3_LOG(ANDROID_LOG_ERROR, "_pocos3_initialize returned false");
            return JNI_FALSE;
        }
    }

    g_initialised = true;
    POCOS3_LOG(ANDROID_LOG_INFO, "PocoS3 core loaded and initialised");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeShutdown(JNIEnv*, jclass) {
    if (!g_initialised) return;
    if (g_api.shutdown) g_api.shutdown();
    if (g_core_handle) {
        dlclose(g_core_handle);
        g_core_handle = nullptr;
    }
    g_initialised = false;
    POCOS3_LOG(ANDROID_LOG_INFO, "PocoS3 core shut down");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeBoot(JNIEnv* env, jclass, jstring jPath) {
    if (!g_api.boot) return -1;
    return g_api.boot(jstr(env, jPath));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeGetState(JNIEnv*, jclass) {
    if (!g_api.getState) return 0;
    return g_api.getState();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativePause(JNIEnv*, jclass) {
    if (g_api.pause) g_api.pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeResume(JNIEnv*, jclass) {
    if (g_api.resume) g_api.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeKill(JNIEnv*, jclass) {
    if (g_api.kill) g_api.kill();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSurfaceEvent(
        JNIEnv* env, jclass, jobject surface, jint event) {
    if (!g_api.surfaceEvent) return JNI_FALSE;
    return g_api.surfaceEvent(env, surface, event) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSurfaceSizeChanged(
        JNIEnv*, jclass, jint width, jint height) {
    if (g_api.surfaceSizeChanged) g_api.surfaceSizeChanged(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeOverlayPadData(
        JNIEnv*, jclass,
        jint port, jint digital1, jint digital2,
        jint leftStickX, jint leftStickY,
        jint rightStickX, jint rightStickY) {
    if (g_api.overlayPadData) {
        g_api.overlayPadData(port, digital1, digital2,
                             leftStickX, leftStickY,
                             rightStickX, rightStickY);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetProfile(
        JNIEnv* env, jclass, jstring jJson) {
    if (g_api.setProfile) g_api.setProfile(jstr(env, jJson));
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetCapabilities(
        JNIEnv* env, jclass, jstring jJson) {
    if (g_api.setCapabilities) g_api.setCapabilities(jstr(env, jJson));
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetSocInfo(
        JNIEnv* env, jclass, jstring jSocInfo) {
    if (g_api.setSocInfo) g_api.setSocInfo(jstr(env, jSocInfo));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeGetPerformanceSnapshot(JNIEnv* env, jclass) {
    if (!g_api.getPerformanceSnapshot) {
        return env->NewStringUTF("{}");
    }
    return env->NewStringUTF(g_api.getPerformanceSnapshot().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeProbeVulkan(JNIEnv* env, jclass) {
    if (!g_api.probeVulkan) {
        return env->NewStringUTF("{}");
    }
    return env->NewStringUTF(g_api.probeVulkan().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetThermals(
        JNIEnv*, jclass,
        jfloat cpu, jfloat gpu, jfloat battery, jboolean show) {
    if (g_api.setThermals) g_api.setThermals(cpu, gpu, battery, show ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeProcessCompilationQueue(JNIEnv* env, jclass) {
    if (!g_api.processCompilationQueue) return JNI_FALSE;
    return g_api.processCompilationQueue(env) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeStartMainThreadProcessor(JNIEnv* env, jclass) {
    if (!g_api.startMainThreadProcessor) return JNI_FALSE;
    return g_api.startMainThreadProcessor(env) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeInstallFirmware(
        JNIEnv* env, jclass, jint fd, jlong progressId) {
    if (!g_api.installFw) return JNI_FALSE;
    return g_api.installFw(env, fd, static_cast<long>(progressId)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeIsInstallableFile(
        JNIEnv*, jclass, jint fd) {
    if (!g_api.isInstallableFile) return JNI_FALSE;
    return g_api.isInstallableFile(fd) ? JNI_TRUE : JNI_FALSE;
}

// =============================================================================
// Library init: System.loadLibrary("pocos3-glue") triggers this.
// =============================================================================

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    POCOS3_LOG(ANDROID_LOG_INFO, "PocoS3 JNI glue loaded");
    return JNI_VERSION_1_6;
}
