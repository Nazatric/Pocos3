package com.pocos3

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pocos3.runtime.PocoS3Core
import com.pocos3.ui.controls.TouchOverlay
import com.pocos3.ui.hud.PerformanceHud
import com.pocos3.ui.theme.MornyTheme

// =============================================================================
// Emulation activity.
//
// Hosts the Vulkan surface, touch overlay, and performance HUD. Lifecycle
// is sensorLandscape + fullscreen; the user enters this activity by
// tapping a game in the library.
// =============================================================================

class EmulationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MornyTheme {
                EmulationSurface()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PocoS3Core.nativeResume()
    }

    override fun onPause() {
        PocoS3Core.nativePause()
        super.onPause()
    }
}

@Composable
private fun EmulationSurface() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Vulkan surface. The SurfaceView's holder callbacks are forwarded
        // to the JNI glue, which in turn forwards them to the core so the
        // core can recreate its VkSurfaceKHR.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                object : SurfaceView(ctx) {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        super.surfaceCreated(holder)
                        PocoS3Core.nativeSurfaceEvent(holder.surface, 0)
                    }
                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, w: Int, h: Int
                    ) {
                        super.surfaceChanged(holder, format, w, h)
                        PocoS3Core.nativeSurfaceEvent(holder.surface, 1)
                        PocoS3Core.nativeSurfaceSizeChanged(w, h)
                    }
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        PocoS3Core.nativeSurfaceEvent(holder.surface, 2)
                        super.surfaceDestroyed(holder)
                    }
                }.apply {
                    // Let the core drive the frame rate.
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        holder.setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                    }
                }
            },
        )

        // Touch overlay on top of the surface.
        TouchOverlay(modifier = Modifier.fillMaxSize())

        // Performance HUD (developer toggle; off by default).
        PerformanceHud(modifier = Modifier.fillMaxSize())
    }
}
