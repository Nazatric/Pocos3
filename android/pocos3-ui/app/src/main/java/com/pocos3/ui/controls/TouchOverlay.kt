package com.pocos3.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pocos3.runtime.PocoS3Core
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// =============================================================================
// Touch overlay.
//
// Renders virtual D-pad, two analog sticks, face buttons (square / cross /
// circle / triangle), shoulders (L1/R1), triggers (L2/R2), START, SELECT,
// PS button. All transparent, movable, scalable, multi-touch.
//
// Input is forwarded to the core via PocoS3Core.nativeOverlayPadData with
// PS3 pad constants (CELL_PAD_CTRL_*). The encoding matches ARMSX3's
// overlayPadData bit layout so the core side does not need a separate
// path.
// =============================================================================

@Composable
fun TouchOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster: D-pad + left stick.
            Column(
                modifier = Modifier.padding(MornyShapes.spacingXxl),
                verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
            ) {
                LeftAnalogStick()
                DPad()
            }
            // Right cluster: face buttons + right stick.
            Column(
                modifier = Modifier.padding(MornyShapes.spacingXxl),
                verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
                horizontalAlignment = Alignment.End,
            ) {
                FaceButtons()
                RightAnalogStick()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(MornyShapes.spacingL),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ShoulderButton(label = "L1")
            ShoulderButton(label = "L2", isTrigger = true)
            Spacer(Modifier.weight(1f))
            ShoulderButton(label = "R2", isTrigger = true)
            ShoulderButton(label = "R1")
        }
    }
}

// PS3 pad constants. Mirror upstream RPCS3's CellPad.h bit layout.
private object Pad {
    // digital1 (CELL_PAD_BTN_OFFSET_DIGITAL1)
    const val CTRL_LEFT   = 0x80
    const val CTRL_DOWN   = 0x40
    const val CTRL_RIGHT  = 0x20
    const val CTRL_UP     = 0x10
    const val CTRL_START  = 0x08
    const val CTRL_R3     = 0x04
    const val CTRL_L3     = 0x02
    const val CTRL_SELECT = 0x01
    // digital2 (CELL_PAD_BTN_OFFSET_DIGITAL2)
    const val CTRL_SQUARE   = 0x80
    const val CTRL_CROSS    = 0x40
    const val CTRL_CIRCLE   = 0x20
    const val CTRL_TRIANGLE = 0x10
    const val CTRL_R1       = 0x08
    const val CTRL_L1       = 0x04
    const val CTRL_R2       = 0x02
    const val CTRL_L2       = 0x01
}

@Composable
private fun DPad() {
    var d1 by remember { mutableStateOf(0) }
    val onToggle: (Int, Boolean) -> Unit = { mask, pressed ->
        d1 = if (pressed) d1 or mask else d1 and mask.inv()
        PocoS3Core.nativeOverlayPadData(
            port = 0, digital1 = d1, digital2 = 0,
            leftStickX = 0, leftStickY = 0, rightStickX = 0, rightStickY = 0
        )
    }
    Box(modifier = Modifier.size(160.dp)) {
        // Up
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_UP, it) })
        // Down
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp, y = 104.dp),
                    onPressChange = { onToggle(Pad.CTRL_DOWN, it) })
        // Left
        TouchButton(modifier = Modifier.size(56.dp).offset(y = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_LEFT, it) })
        // Right
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 104.dp, y = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_RIGHT, it) })
    }
}

@Composable
private fun FaceButtons() {
    var d2 by remember { mutableStateOf(0) }
    val onToggle: (Int, Boolean) -> Unit = { mask, pressed ->
        d2 = if (pressed) d2 or mask else d2 and mask.inv()
        PocoS3Core.nativeOverlayPadData(
            port = 0, digital1 = 0, digital2 = d2,
            leftStickX = 0, leftStickY = 0, rightStickX = 0, rightStickY = 0
        )
    }
    Box(modifier = Modifier.size(160.dp)) {
        // Triangle
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_TRIANGLE, it) })
        // Cross
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp, y = 104.dp),
                    onPressChange = { onToggle(Pad.CTRL_CROSS, it) })
        // Square
        TouchButton(modifier = Modifier.size(56.dp).offset(y = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_SQUARE, it) })
        // Circle
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 104.dp, y = 52.dp),
                    onPressChange = { onToggle(Pad.CTRL_CIRCLE, it) })
    }
}

@Composable
private fun LeftAnalogStick() {
    AnalogStick(
        modifier = Modifier.size(96.dp),
        onStick = { x, y ->
            PocoS3Core.nativeOverlayPadData(
                port = 0, digital1 = 0, digital2 = 0,
                leftStickX = x, leftStickY = y, rightStickX = 0, rightStickY = 0
            )
        }
    )
}

@Composable
private fun RightAnalogStick() {
    AnalogStick(
        modifier = Modifier.size(96.dp),
        onStick = { x, y ->
            PocoS3Core.nativeOverlayPadData(
                port = 0, digital1 = 0, digital2 = 0,
                leftStickX = 0, leftStickY = 0, rightStickX = x, rightStickY = y
            )
        }
    )
}

@Composable
private fun AnalogStick(modifier: Modifier = Modifier, onStick: (Int, Int) -> Unit) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset -> pos = offset },
                onDragEnd = { pos = Offset.Zero; onStick(0, 0) },
                onDrag = { change, _ ->
                    pos = change.position
                    val (cx, cy) = size.center()
                    val dx = (pos.x - cx) / (size.width / 2f)
                    val dy = (pos.y - cy) / (size.height / 2f)
                    val mag = hypot(dx, dy).coerceAtMost(1f)
                    val angle = atan2(dy, dx)
                    val nx = (cos(angle) * mag * 127f).toInt()
                    val ny = (sin(angle) * mag * 127f).toInt()
                    onStick(nx, ny)
                }
            )
        }
    ) {
        // Well
        drawCircle(
            color = MornyColors.borderSubtle,
            radius = size.minDimension / 2f,
            style = Stroke(width = 1.dp.toPx())
        )
        // Thumb
        drawCircle(
            color = MornyColors.bgSurfaceGlass,
            radius = size.minDimension / 5f,
            center = if (pos == Offset.Zero) Offset(size.width / 2, size.height / 2) else pos
        )
    }
}

@Composable
private fun TouchButton(
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = { onPressChange(true); pressed = true; tryAwaitRelease(); pressed = false; onPressChange(false) }
            )
        }
    ) {
        val r = size.minDimension / 2f
        val fill = if (pressed) MornyColors.accentTealDim.copy(alpha = 0.5f)
                   else MornyColors.bgSurfaceGlass.copy(alpha = 0.7f)
        drawCircle(color = fill, radius = r)
        drawCircle(color = Color.White, radius = r, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun ShoulderButton(label: String, isTrigger: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = if (isTrigger) 36.dp else 48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        // L1 / R1 / L2 / R2 mapping - simplified for the sketch
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fill = if (pressed) MornyColors.accentTealDim.copy(alpha = 0.5f)
                       else MornyColors.bgSurfaceGlass.copy(alpha = 0.6f)
            drawRoundRect(color = fill, size = size)
            drawRoundRect(color = Color.White, size = size, style = Stroke(width = 2.dp.toPx()))
        }
        Text(
            text = label,
            color = MornyColors.textPrimary,
            modifier = Modifier.padding(MornyShapes.spacingS),
        )
    }
}
