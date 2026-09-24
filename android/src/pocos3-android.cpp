// =============================================================================
// PocoS3 Android JNI glue.
//
// This file is the *only* C++ the Gradle externalNativeBuild ever compiles.
// It is deliberately tiny: it owns:
//
//   1. dlopen() of libpocos3-core.so (the upstream RPCS3 core, built
//      separately by android/configure.sh).
//   2. Resolution of the PocoS3Api function-pointer table via dlsym().
//   3. JNI entry points exposed to Kotlin for surface, input, storage,
//      and lifecycle.
//
// It owns NOTHING that should live in the core: no PPU, no SPU, no RSX,
// no Vulkan calls. Everything hot lives in the core .so; the glue is a
// thin marshalling layer.
//
// Licensed under GPL-2.0-only, same as upstream RPCS3.
//
// Inspired by ARMSX3's `android/src/rpcsx-android.cpp`. The PocoS3Api table
// shape mirrors ARMSX3's RPCSXApi because that shape itself is the right
// abstraction; the implementation here is PocoS3's own.
// =============================================================================

#include "pocos3-android.h"

#include <android/api-level.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <optional>
#include <sys/system_properties.h>
#include <unistd.h>
#include <filesystem>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <vector>

// =====================================================================
// Logging
// =====================================================================

namespace {

constexpr const char* kLogTag = "PocoS3";

void pocos3_log(int prio, std::string_view msg) noexcept {
    __android_log_print(prio, kLogTag, "%.*s",
                        static_cast<int>(msg.size()), msg.data());
}

#define POCOS3_LOG_INFO(msg)  pocos3_log(ANDROID_LOG_INFO,  msg)
#define POCOS3_LOG_WARN(msg)  pocos3_log(ANDROID_LOG_WARN,  msg)
#define POCOS3_LOG_ERROR(msg) pocos3_log(ANDROID_LOG_ERROR, msg)
#define POCOS3_LOG_DEBUG(msg) pocos3_log(ANDROID_LOG_DEBUG, msg)

// =====================================================================
// Core handle: dlopen + dlsym resolution of PocoS3Api.
// =====================================================================

struct CoreHandle {
    void* dl_handle = nullptr;
    PocoS3Api api{};
    bool initialised = false;

    // True if dlopen succeeded. The table may still be partially populated
    // if the core .so is an older build that doesn't expose every entry.
    bool api_resolved = false;
};

CoreHandle g_core;
std::mutex g_core_mutex;

// Path the core caches its persistent state under. Set by Kotlin at init.
std::string g_cache_dir;
std::string g_data_dir;

// =====================================================================
// dlopen helper. Searches the same directory the glue .so lives in.
// =====================================================================

std::string locate_core_so() {
    // 1) Try the same load library directory as ourselves (jniLibs/arm64-v8a).
    // Android places both .so files in /data/app/<pkg>/lib/arm64/.
    // We can't easily get our own dlpath; use a fixed name and let
    // android_dlopen_ext handle it.
    return "libpocos3-core.so";
}

bool resolve_core_symbols(CoreHandle& h) {
    if (!h.dl_handle) {
        return false;
    }

    // Helper macro: looks up a symbol and writes into h.api.<name>.
    // Treats "symbol not found" as a hard error on required entries.
#define POCOS3_RESOLVE_REQUIRED(name)                                       \
    do {                                                                     \
        h.api.name = reinterpret_cast<decltype(h.api.name)>(                 \
            dlsym(h.dl_handle, "pocos3_" #name));                            \
        if (!h.api.name) {                                                   \
            POCOS3_LOG_ERROR("dlsym: required symbol pocos3_" #name          \
                             " not found in libpocos3-core.so");             \
            return false;                                                    \
        }                                                                    \
    } while (0)

#define POCOS3_RESOLVE_OPTIONAL(name)                                       \
    do {                                                                     \
        h.api.name = reinterpret_cast<decltype(h.api.name)>(                 \
            dlsym(h.dl_handle, "pocos3_" #name));                            \
        if (!h.api.name) {                                                   \
            POCOS3_LOG_WARN("dlsym: optional symbol pocos3_" #name           \
                            " not found (older core .so)");                  \
        }                                                                    \
    } while (0)

    // ---- Lifecycle ----------------------------------------------------
    POCOS3_RESOLVE_REQUIRED(initialize);
    POCOS3_RESOLVE_REQUIRED(shutdown);
    POCOS3_RESOLVE_REQUIRED(boot);
    POCOS3_RESOLVE_REQUIRED(kill);
    POCOS3_RESOLVE_REQUIRED(pause);
    POCOS3_RESOLVE_REQUIRED(resume);
    POCOS3_RESOLVE_REQUIRED(getState);

    // ---- Surface / Vulkan ---------------------------------------------
    POCOS3_RESOLVE_REQUIRED(surfaceEvent);
    POCOS3_RESOLVE_OPTIONAL(surfaceSizeChanged);

    // ---- Input ---------------------------------------------------------
    POCOS3_RESOLVE_REQUIRED(overlayPadData);
    POCOS3_RESOLVE_OPTIONAL(overlayPadPressure);
    POCOS3_RESOLVE_OPTIONAL(keyboardKey);
    POCOS3_RESOLVE_OPTIONAL(setPadSensor);
    POCOS3_RESOLVE_OPTIONAL(getPadRumble);
    POCOS3_RESOLVE_OPTIONAL(setPadDeviceClasses);
    POCOS3_RESOLVE_OPTIONAL(usbDeviceEvent);

    // ---- Storage / firmware / install ---------------------------------
    POCOS3_RESOLVE_OPTIONAL(installFw);
    POCOS3_RESOLVE_OPTIONAL(isInstallableFile);
    POCOS3_RESOLVE_OPTIONAL(getDirInstallPath);
    POCOS3_RESOLVE_OPTIONAL(probePkgInfo);
    POCOS3_RESOLVE_OPTIONAL(install);

    // ---- Performance / HUD --------------------------------------------
    POCOS3_RESOLVE_OPTIONAL(getFramePeriodNs);
    POCOS3_RESOLVE_OPTIONAL(getFrameWorkNs);
    POCOS3_RESOLVE_OPTIONAL(getRsxThreadTid);
    POCOS3_RESOLVE_OPTIONAL(getTitleId);
    POCOS3_RESOLVE_OPTIONAL(getCurrentTrophyName);
    POCOS3_RESOLVE_OPTIONAL(setThermals);
    POCOS3_RESOLVE_OPTIONAL(setRenderPosition);

    // ---- Capability / profile -----------------------------------------
    POCOS3_RESOLVE_OPTIONAL(setCapabilities);
    POCOS3_RESOLVE_OPTIONAL(setProfile);
    POCOS3_RESOLVE_OPTIONAL(setSocInfo);

    // ---- Compilation queue --------------------------------------------
    POCOS3_RESOLVE_OPTIONAL(processCompilationQueue);
    POCOS3_RESOLVE_OPTIONAL(startMainThreadProcessor);
    POCOS3_RESOLVE_OPTIONAL(collectGameInfo);

    // ---- Restart / capture -------------------------------------------
    POCOS3_RESOLVE_OPTIONAL(isRestartPending);
    POCOS3_RESOLVE_OPTIONAL(openHomeMenu);
    POCOS3_RESOLVE_OPTIONAL(captureFrame);

    // ---- Performance snapshot for HUD ---------------------------------
    POCOS3_RESOLVE_OPTIONAL(getPerformanceSnapshot);

#undef POCOS3_RESOLVE_REQUIRED
#undef POCOS3_RESOLVE_OPTIONAL

    h.api_resolved = true;
    return true;
}

// =====================================================================
// JNI helpers
// =====================================================================

std::string jstr_to_std(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

jstring std_to_jstr(JNIEnv* env, std::string_view sv) {
    return env->NewStringUTF(std::string{sv}.c_str());
}

}  // namespace

// =============================================================================
// JNI entry points. Called from Kotlin/Java.
// =============================================================================

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeInitialise(
        JNIEnv* env, jclass, jstring jCacheDir, jstring jDataDir) {
    std::lock_guard lock(g_core_mutex);

    if (g_core.initialised) {
        POCOS3_LOG_WARN("nativeInitialise: already initialised; ignoring");
        return JNI_TRUE;
    }

    g_cache_dir = jstr_to_std(env, jCacheDir);
    g_data_dir  = jstr_to_std(env, jDataDir);

    POCOS3_LOG_INFO("PocoS3 glue initialising; cache=" + g_cache_dir +
                    " data=" + g_data_dir);

    // dlopen the core .so. android_dlopen_ext would be more correct for
    // namespace isolation, but plain dlopen works because both .so files
    // live in the same linker namespace by virtue of being in jniLibs.
    g_core.dl_handle = dlopen(locate_core_so().c_str(),
                              RTLD_NOW | RTLD_LOCAL);
    if (!g_core.dl_handle) {
        const char* err = dlerror();
        POCOS3_LOG_ERROR(std::string{"dlopen(libpocos3-core.so) failed: "} +
                         (err ? err : "(no error)"));
        return JNI_FALSE;
    }

    if (!resolve_core_symbols(g_core)) {
        POCOS3_LOG_ERROR("PocoS3Api table did not resolve; "
                         "core .so is incompatible with this glue");
        // Keep dl_handle open so subsequent nativeInitialise retries succeed
        // without re-dlopen; the caller is expected to kill the process.
        return JNI_FALSE;
    }

    // Tell the core about its data + cache dirs.
    if (g_core.api.initialize) {
        if (!g_core.api.initialize(g_data_dir, g_cache_dir)) {
            POCOS3_LOG_ERROR("pocos3_initialize returned false");
            return JNI_FALSE;
        }
    }

    g_core.initialised = true;
    POCOS3_LOG_INFO("PocoS3 core loaded and initialised");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeShutdown(JNIEnv*, jclass) {
    std::lock_guard lock(g_core_mutex);
    if (!g_core.initialised) return;

    if (g_core.api.shutdown) {
        g_core.api.shutdown();
    }
    if (g_core.dl_handle) {
        dlclose(g_core.dl_handle);
        g_core.dl_handle = nullptr;
    }
    g_core.initialised = false;
    g_core.api_resolved = false;
    POCOS3_LOG_INFO("PocoS3 core shut down");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeBoot(
        JNIEnv* env, jclass, jstring jPath) {
    std::lock_guard lock(g_core_mutex);
    if (!g_core.api.boot) return -1;
    return g_core.api.boot(jstr_to_std(env, jPath));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeGetState(JNIEnv*, jclass) {
    if (!g_core.api.getState) return 0;
    return g_core.api.getState();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativePause(JNIEnv*, jclass) {
    if (g_core.api.pause) g_core.api.pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeResume(JNIEnv*, jclass) {
    if (g_core.api.resume) g_core.api.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeKill(JNIEnv*, jclass) {
    if (g_core.api.kill) g_core.api.kill();
}

// Surface lifecycle. The Activity owns the SurfaceHolder; we forward
// surface events into the core so it can recreate its VkSurface.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSurfaceEvent(
        JNIEnv* env, jclass, jobject surface, jint event) {
    if (!g_core.api.surfaceEvent) return JNI_FALSE;
    return g_core.api.surfaceEvent(env, surface, event) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSurfaceSizeChanged(
        JNIEnv*, jclass, jint width, jint height) {
    if (g_core.api.surfaceSizeChanged) {
        g_core.api.surfaceSizeChanged(width, height);
    }
}

// Pad input from touch overlay / controllers. See PocoS3Api.overlayPadData.
extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeOverlayPadData(
        JNIEnv*, jclass,
        jint port, jint digital1, jint digital2,
        jint leftStickX, jint leftStickY,
        jint rightStickX, jint rightStickY) {
    if (g_core.api.overlayPadData) {
        g_core.api.overlayPadData(port, digital1, digital2,
                                  leftStickX, leftStickY,
                                  rightStickX, rightStickY);
    }
}

// Capability / profile JSON.
extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetProfile(
        JNIEnv* env, jclass, jstring jJson) {
    if (g_core.api.setProfile) {
        g_core.api.setProfile(jstr_to_std(env, jJson));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetCapabilities(
        JNIEnv* env, jclass, jstring jJson) {
    if (g_core.api.setCapabilities) {
        g_core.api.setCapabilities(jstr_to_std(env, jJson));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetSocInfo(
        JNIEnv* env, jclass, jstring jSocInfo) {
    if (g_core.api.setSocInfo) {
        g_core.api.setSocInfo(jstr_to_std(env, jSocInfo));
    }
}

// Performance snapshot for HUD.
extern "C" JNIEXPORT jstring JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeGetPerformanceSnapshot(
        JNIEnv* env, jclass) {
    if (!g_core.api.getPerformanceSnapshot) {
        return std_to_jstr(env, "{}");
    }
    return std_to_jstr(env, g_core.api.getPerformanceSnapshot());
}

// Thermal feedback from Android PowerManager.
extern "C" JNIEXPORT void JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeSetThermals(
        JNIEnv*, jclass,
        jfloat cpu, jfloat gpu, jfloat battery, jboolean show) {
    if (g_core.api.setThermals) {
        g_core.api.setThermals(cpu, gpu, battery, show ? 1 : 0);
    }
}

// Compilation queue (for the on-boot shader pre-compile UI).
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeProcessCompilationQueue(JNIEnv* env, jclass) {
    if (!g_core.api.processCompilationQueue) return JNI_FALSE;
    return g_core.api.processCompilationQueue(env) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeStartMainThreadProcessor(JNIEnv* env, jclass) {
    if (!g_core.api.startMainThreadProcessor) return JNI_FALSE;
    return g_core.api.startMainThreadProcessor(env) ? JNI_TRUE : JNI_FALSE;
}

// Firmware install. Called from a Kotlin coroutine that has the file
// descriptor for a PS3UPDAT.PUP located via SAF.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeInstallFirmware(
        JNIEnv* env, jclass, jint fd, jlong progressId) {
    if (!g_core.api.installFw) return JNI_FALSE;
    return g_core.api.installFw(env, fd, static_cast<long>(progressId)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeIsInstallableFile(
        JNIEnv* env, jclass, jint fd) {
    if (!g_core.api.isInstallableFile) return JNI_FALSE;
    return g_core.api.isInstallableFile(fd) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocos3_runtime_PocoS3Core_nativeGetPerformanceString(JNIEnv* env, jclass) {
    return std_to_jstr(env, "PocoS3 0.1.0");
}

// Library init: called by System.loadLibrary("pocos3-glue") side-effect.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    POCOS3_LOG_INFO("PocoS3 JNI glue loaded");
    return JNI_VERSION_1_6;
}
