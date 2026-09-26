package com.pocos3

import android.content.Intent
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.pocos3.runtime.EmulatorService
import com.pocos3.runtime.PocoS3Core
import com.pocos3.ui.controls.TouchOverlay
import com.pocos3.ui.hud.PerformanceHud
import com.pocos3.ui.settings.PocoS3Settings
import com.pocos3.ui.theme.MornyTheme
import kotlinx.coroutines.launch

// =============================================================================
// Emulation activity.
//
// Hosts the Vulkan surface + touch overlay + performance HUD. Receives a
// game path via Intent extras (EXTRA_GAME_PATH) and boots the core.
//
// sensorLandscape + fullscreen + immersive; the user enters this activity
// by tapping a game in the library.
// =============================================================================

class EmulationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GAME_PATH = "pocos3.intent.extra.GAME_PATH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gamePath = intent.getStringExtra(EXTRA_GAME_PATH)
        if (gamePath == null) {
            // Defensive: someone launched us without a path.
            finish()
            return
        }

        // Start the foreground service that keeps the emulator alive.
        EmulatorService.start(this)

        // Boot the game on a background coroutine so the Activity's main
        // thread stays free for surface rendering.
        lifecycleScope.launch {
            val result = PocoS3Core.nativeBoot(gamePath)
            if (result != 0) {
                // Boot failed; show a toast + finish.
                android.widget.Toast.makeText(
                    this@EmulationActivity,
                    "Boot failed. See PocoS3 log.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }

        setContent {
            MornyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EmulationContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PocoS3Core.getState() == PocoS3Core.EmulatorState.PAUSED) {
            PocoS3Core.nativeResume()
        }
    }

    override fun onPause() {
        PocoS3Core.nativePause()
        super.onPause()
    }

    override fun onDestroy() {
        // Kill the emulator only if the Activity is finishing (not just
        // rotating). Otherwise keep the core alive across rotations.
        if (isFinishing) {
            PocoS3Core.nativeKill()
            EmulatorService.stop(this)
        }
        super.onDestroy()
    }

    @Composable
    private fun EmulationContent() {
        val settings = remember { PocoS3Settings.get(this) }
        val hudVisible by settings.hudVisible.collectAsState(initial = false)
        Box(modifier = Modifier.fillMaxSize()) {
            // Vulkan surface host.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                PocoS3Core.nativeSurfaceEvent(holder.surface, 0)
                            }
                            override fun surfaceChanged(
                                holder: SurfaceHolder, format: Int, w: Int, h: Int
                            ) {
                                PocoS3Core.nativeSurfaceEvent(holder.surface, 1)
                                PocoS3Core.nativeSurfaceSizeChanged(w, h)
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                PocoS3Core.nativeSurfaceEvent(holder.surface, 2)
                            }
                        })
                        // Frame-rate hint: PS3 native frame rate (capped at 60).
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            holder.surface.setFrameRate(
                                60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                            )
                        }
                    }
                },
            )

            // Touch overlay on top.
            TouchOverlay(modifier = Modifier.fillMaxSize())

            // Performance HUD (developer toggle).
            if (hudVisible) {
                PerformanceHud(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

