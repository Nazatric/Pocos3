package com.pocos3.platform

import android.content.Context
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi

// =============================================================================
// Frame pacer.
//
// Picks the right Vulkan present mode for the device's detected refresh
// rate + the user's selected frame rate limit. Wraps
// ANativeWindow_setFrameRate (Android 11+) for the surface frame-rate
// hint, and tells the core which present mode to use.
//
// The goal is stable frame pacing, not inflated FPS:
//   * 60 Hz display + 60 FPS game → FIFO (VSync)
//   * 120 Hz display + 60 FPS game → FIFO_RELAXED (so we don't render
//     at 120 and waste Mali bandwidth)
//   * 120 Hz display + 120 FPS game → FIFO (true 120, only when user
//     explicitly opts in via settings)
// =============================================================================

enum class PresentMode(val vkValue: Int) {
    FIFO_KHR(2),                  // vsync; default
    FIFO_RELAXED_KHR(3),          // adaptive vsync; tear if late
    MAILBOX_KHR(1),               // mailbox; triple-buffered
    IMMEDIATE_KHR(0),             // no vsync; tearing
}

enum class FramePaceStrategy {
    VSYNC,                  // hard cap at panel refresh
    ADAPTIVE_VSYNC,         // VSync + tear on miss
    UNCAPPED,               // render as fast as possible
}

object FramePacer {

    @RequiresApi(Build.VERSION_CODES.R)
    fun setSurfaceFrameRate(surface: Surface, fps: Float) {
        // ANativeWindow_setFrameRate via the Surface wrapper.
        // Android 11+ exposes this as Surface.setFrameRate.
        runCatching {
            surface.setFrameRate(
                fps,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
            )
        }
    }

    fun pickStrategy(
        panelRefreshHz: Float,
        targetFps: Int,
        capabilities: VulkanCapabilityReport,
    ): Pair<PresentMode, FramePaceStrategy> {
        // If the panel can do 120 and the user wants 120, we do FIFO at 120.
        if (targetFps >= 120 && panelRefreshHz >= 119.0f) {
            return PresentMode.FIFO_KHR to FramePaceStrategy.VSYNC
        }

        // If the panel refresh > target, we want FIFO_RELAXED so the
        // GPU can sleep between frames instead of spinning at 120Hz.
        if (panelRefreshHz > targetFps + 1.0f) {
            // FIFO_RELAXED_KHR requires it be advertised by the driver.
            // All Mali drivers support it.
            return PresentMode.FIFO_RELAXED_KHR to FramePaceStrategy.ADAPTIVE_VSYNC
        }

        // If panel refresh == target, hard FIFO.
        return PresentMode.FIFO_KHR to FramePaceStrategy.VSYNC
    }

    fun pickMaxRenderRate(panelRefreshHz: Float, targetFps: Int): Float {
        // We never render above the user's target FPS - PS3 timing is
        // frame-counted, and rendering above the cap breaks game logic.
        // We also never render above the panel's refresh - that's wasted
        // Mali bandwidth.
        return minOf(targetFps.toFloat(), panelRefreshHz)
    }
}
