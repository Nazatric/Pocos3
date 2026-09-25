package com.pocos3.runtime

import android.content.Context
import android.view.Surface
import androidx.annotation.Keep

// =============================================================================
// Kotlin-side bindings to libpocos3-glue.so.
//
// Every method declared here has a matching `extern "C"` JNI entry in
// android/pocos3-ui/app/src/main/cpp/native-lib.cpp.
// =============================================================================

@Keep
object PocoS3Core {
    init {
        System.loadLibrary("pocos3-glue")
    }

    // Lifecycle
    external fun nativeInitialise(cacheDir: String, dataDir: String): Boolean
    external fun nativeShutdown()
    external fun nativeBoot(path: String): Int
    external fun nativeGetState(): Int
    external fun nativePause()
    external fun nativeResume()
    external fun nativeKill()

    // Surface
    external fun nativeSurfaceEvent(surface: Surface, event: Int): Boolean
    external fun nativeSurfaceSizeChanged(width: Int, height: Int)

    // Pad input
    external fun nativeOverlayPadData(
        port: Int, digital1: Int, digital2: Int,
        leftStickX: Int, leftStickY: Int,
        rightStickX: Int, rightStickY: Int,
    )
    external fun nativeOverlayPadPressure(port: Int, values: IntArray, count: Int): Boolean

    // Keyboard + sensor
    external fun nativeKeyboardKey(
        androidKeyCode: Int, unicode: Int, pressed: Boolean, repeat: Boolean
    ): Boolean
    external fun nativeSetPadSensor(port: Int, x: Int, y: Int, z: Int, g: Int)
    external fun nativeGetPadRumble(port: Int): Int
    external fun nativeSetRenderPosition(portraitTop: Boolean, topInset: Int)

    // Capability / profile
    external fun nativeSetProfile(json: String)
    external fun nativeSetCapabilities(json: String)
    external fun nativeSetSocInfo(socInfo: String)
    external fun nativeProbeVulkan(): String

    // Performance / thermal
    external fun nativeGetPerformanceSnapshot(): String
    external fun nativeSetThermals(cpu: Float, gpu: Float, battery: Float, show: Boolean)

    // Compilation queue
    external fun nativeProcessCompilationQueue(): Boolean
    external fun nativeStartMainThreadProcessor(): Boolean

    // Storage / firmware / install
    external fun nativeInstallFirmware(fd: Int, progressId: Long): Boolean
    external fun nativeIsInstallableFile(fd: Int): Boolean

    // Save state + screenshot
    external fun nativeSaveState(slot: Int): Boolean
    external fun nativeLoadState(slot: Int): Boolean
    external fun nativeCaptureFrame()

    // Getters from the running emulator
    external fun nativeGetTitleId(): String
    external fun nativeGetFramePeriodNs(): Long
    external fun nativeGetFrameWorkNs(): Long
    external fun nativeGetRsxThreadTid(): Int
    external fun nativeIsRestartPending(): Boolean

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
