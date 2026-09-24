package com.pocos3.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocos3.runtime.PocoS3Core
import com.pocos3.ui.theme.MornyColors
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

// =============================================================================
// Performance HUD overlay.
//
// Polls PocoS3Core.nativeGetPerformanceSnapshot() every 250ms and renders
// the metrics as a tabular-mono readout in the top-left of the emulation
// surface. The HUD is OFF by default and is enabled in Settings → Developer.
// =============================================================================

@Composable
fun PerformanceHud(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }   // TODO: wire to settings
    if (!visible) return

    var snapshot by remember { mutableStateOf<HudSnapshot?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val json = runCatching { PocoS3Core.nativeGetPerformanceSnapshot() }.getOrNull() ?: "{}"
            snapshot = parseSnapshot(json)
            delay(250)
        }
    })

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
        // Glass background
        drawRect(color = MornyColors.bgSurfaceGlass.copy(alpha = 0.85f))
        drawRect(color = MornyColors.borderSubtle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
    }
    Column(modifier = modifier.padding(MornyShapes.spacingS)) {
        snapshot?.let {
            Text("${it.fps}", color = MornyColors.accentAmber, fontFamily = FontFamily.Monospace)
            Text("Frame: ${it.frameTimeMs}ms", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("1% low: ${it.p1Low}", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("0.1% low: ${it.p01Low}", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("PPU: ${it.ppuTimeMs}ms", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("SPU: ${it.spuTimeMs}ms", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("RSX: ${it.rsxTimeMs}ms", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("GPU: ${it.gpuTimeMs}ms", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("Thermal: ${it.thermalHeadroom}", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("Resolution: ${it.resolutionScale}x", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
            Text("Present: ${it.presentMode}", color = MornyColors.textSecondary, fontFamily = FontFamily.Monospace)
        } ?: Text("waiting for snapshot...", color = MornyColors.textTertiary)
    }
}

data class HudSnapshot(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val p1Low: Float = 0f,
    val p01Low: Float = 0f,
    val ppuTimeMs: Float = 0f,
    val spuTimeMs: Float = 0f,
    val rsxTimeMs: Float = 0f,
    val gpuTimeMs: Float = 0f,
    val thermalHeadroom: Float = 0f,
    val resolutionScale: Float = 1f,
    val presentMode: String = "FIFO_KHR",
)

private fun parseSnapshot(json: String): HudSnapshot? {
    return runCatching {
        val o = Json.parseToJsonElement(json) as JsonObject
        HudSnapshot(
            fps = o["fps"]?.floatOrNull ?: 0f,
            frameTimeMs = o["frameTimeMs"]?.floatOrNull ?: 0f,
            p1Low = o["p1Low"]?.floatOrNull ?: 0f,
            p01Low = o["p01Low"]?.floatOrNull ?: 0f,
            ppuTimeMs = o["ppuTimeMs"]?.floatOrNull ?: 0f,
            spuTimeMs = o["spuTimeMs"]?.floatOrNull ?: 0f,
            rsxTimeMs = o["rsxTimeMs"]?.floatOrNull ?: 0f,
            gpuTimeMs = o["gpuTimeMs"]?.floatOrNull ?: 0f,
            thermalHeadroom = o["thermalHeadroom"]?.floatOrNull ?: 0f,
            resolutionScale = o["resolutionScale"]?.floatOrNull ?: 1f,
            presentMode = (o["presentMode"]?.toString() ?: "FIFO_KHR").trim('"'),
        )
    }.getOrNull()
}
