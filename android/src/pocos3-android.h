// =============================================================================
// PocoS3 Android JNI glue - public header.
//
// Declares:
//   - The PocoS3Api function-pointer table that libpocos3-core.so exposes
//     via dlsym() (extern "C" symbols named pocos3_<fnname>).
//   - The surface event constants the Kotlin side uses.
//
// This header is the *contract* between the JNI glue and the core. It is
// intentionally a single header with no dependencies on RPCS3 internals:
// a new core can be built against this header alone, without dragging the
// emulator's C++ types into the glue's ABI.
// =============================================================================

#pragma once

#include <jni.h>
#include <cstdint>
#include <functional>
#include <string>
#include <string_view>

// Surface events the Activity forwards to nativeSurfaceEvent.
enum PocoS3SurfaceEvent {
    POCOS3_SURFACE_CREATED = 0,
    POCOS3_SURFACE_CHANGED = 1,
    POCOS3_SURFACE_DESTROYED = 2,
    POCOS3_SURFACE_REDRAW_NEEDED = 3,
};

// The function-pointer table. The Kotlin side never touches this directly;
// it goes through the JNI methods declared on com.pocos3.runtime.PocoS3Core.
//
// Each function below corresponds to an extern "C" symbol in
// libpocos3-core.so named pocos3_<fnname>. If a symbol is missing,
// the glue treats it as an optional entry and silently skips it.
struct PocoS3Api {
    // Lifecycle ----------------------------------------------------------
    bool (*initialize)(std::string_view dataDir, std::string_view cacheDir);
    void (*shutdown)();

    // Boot / state -------------------------------------------------------
    int  (*boot)(std::string_view path);     // returns 0 on success
    int  (*getState)();                       // 0=stopped 1=running 2=paused
    void (*kill)();
    void (*pause)();
    void (*resume)();

    // Surface (Vulkan VkSurfaceKHR lives in the core, not the glue) ------
    bool (*surfaceEvent)(JNIEnv* env, jobject surface, jint event);
    void (*surfaceSizeChanged)(int width, int height);

    // Input --------------------------------------------------------------
    bool (*overlayPadData)(int port, int digital1, int digital2,
                           int leftStickX, int leftStickY,
                           int rightStickX, int rightStickY);
    bool (*overlayPadPressure)(int port, const int* values, int count);
    bool (*keyboardKey)(int androidKeyCode, int unicode, bool pressed, bool repeat);
    void (*setPadSensor)(int port, int x, int y, int z, int g);
    int  (*getPadRumble)(int port);
    void (*setPadDeviceClasses)(const int* classes, int count);
    bool (*usbDeviceEvent)(int fd, int vendorId, int productId, int event);

    // Storage / firmware / install --------------------------------------
    bool (*installFw)(JNIEnv* env, int fd, long progressId);
    bool (*isInstallableFile)(jint fd);
    jstring (*getDirInstallPath)(JNIEnv* env, jint fd);
    jstring (*probePkgInfo)(JNIEnv* env, jint fd);
    bool (*install)(JNIEnv* env, int fd, long progressId);

    // Performance / HUD -------------------------------------------------
    unsigned long long (*getFramePeriodNs)();
    unsigned long long (*getFrameWorkNs)();
    int  (*getRsxThreadTid)();
    std::string (*getTitleId)();
    std::string (*getCurrentTrophyName)();
    void (*setThermals)(float cpu, float gpu, float battery, int show);
    void (*setRenderPosition)(bool portraitTop, int topInset);

    // Capability / profile ----------------------------------------------
    void (*setCapabilities)(std::string_view json);
    void (*setProfile)(std::string_view json);
    void (*setSocInfo)(std::string_view socInfo);

    // Compilation queue --------------------------------------------------
    bool (*processCompilationQueue)(JNIEnv* env);
    bool (*startMainThreadProcessor)(JNIEnv* env);
    bool (*collectGameInfo)(JNIEnv* env, std::string_view rootDir, long progressId);

    // Restart / capture --------------------------------------------------
    bool (*isRestartPending)();
    void (*openHomeMenu)();
    void (*captureFrame)();

    // Performance snapshot (JSON) for the HUD ----------------------------
    std::string (*getPerformanceSnapshot)();
};
