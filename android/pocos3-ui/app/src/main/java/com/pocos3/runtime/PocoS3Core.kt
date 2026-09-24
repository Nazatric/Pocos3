package com.pocos3.runtime

// =============================================================================
// Kotlin-side bindings to libpocos3-glue.so.
//
// Every method declared here has a matching `extern "C"` JNI entry in
// android/src/pocos3-android.cpp. The Kotlin side is intentionally thin:
// it marshalls types and exposes them via a single object so the UI can
// call into the emulator from anywhere.
// =============================================================================

import android.content.Context
import android.view.Surface
import androidx.annotation.Keep

@Keep
object PocoS3Core {
    init {
        // Load the JNI glue first; the core .so is dlopen()'d inside it.
        System.loadLibrary("pocos3-glue")
    }

    external fun nativeInitialise(cacheDir: String, dataDir: String): Boolean
    external fun nativeShutdown()
    external fun nativeBoot(path: String): Int
    external fun nativeGetState(): Int
    external fun nativePause()
    external fun nativeResume()
    external fun nativeKill()
    external fun nativeSurfaceEvent(surface: Surface, event: Int): Boolean
    external fun nativeSurfaceSizeChanged(width: Int, height: Int)
    external fun nativeOverlayPadData(
        port: Int, digital1: Int, digital2: Int,
        leftStickX: Int, leftStickY: Int,
        rightStickX: Int, rightStickY: Int,
    )
    external fun nativeSetProfile(json: String)
    external fun nativeSetCapabilities(json: String)
    external fun nativeSetSocInfo(socInfo: String)
    external fun nativeGetPerformanceSnapshot(): String
    external fun nativeSetThermals(cpu: Float, gpu: Float, battery: Float, show: Boolean)
    external fun nativeProcessCompilationQueue(): Boolean
    external fun nativeStartMainThreadProcessor(): Boolean
    external fun nativeInstallFirmware(fd: Int, progressId: Long): Boolean
    external fun nativeIsInstallableFile(fd: Int): Boolean

    // ----- Convenience wrappers -----

    fun initialise(context: Context): Boolean {
        return nativeInitialise(
            cacheDir = context.cacheDir.absolutePath,
            dataDir = context.filesDir.absolutePath,
        )
    }

    enum class EmulatorState(val nativeValue: Int) {
        STOPPED(0), RUNNING(1), PAUSED(2);

        companion object {
            fun fromNative(v: Int) = entries.firstOrNull { it.nativeValue == v } ?: STOPPED
        }
    }

    fun getState(): EmulatorState = EmulatorState.fromNative(nativeGetState())
}
