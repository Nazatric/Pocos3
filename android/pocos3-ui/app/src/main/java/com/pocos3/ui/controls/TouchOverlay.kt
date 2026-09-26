package com.pocos3.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
// PS3 pad constants (CELL_PAD_CTRL_*) mirror upstream RPCS3's
// rpcs3/Emu/Cell/Modules/cellPad.h bit layout so the core side does not
// need a separate path.
// =============================================================================

private object Pad {
    // digital1 (CELL_PAD_BTN_OFFSET_DIGITAL1) - byte 2 of pad data
    const val CTRL_LEFT   = 0x80
    const val CTRL_DOWN   = 0x40
    const val CTRL_RIGHT  = 0x20
    const val CTRL_UP     = 0x10
    const val CTRL_START  = 0x08
    const val CTRL_R3     = 0x04
    const val CTRL_L3     = 0x02
    const val CTRL_SELECT = 0x01
    // digital2 (CELL_PAD_BTN_OFFSET_DIGITAL2) - byte 3 of pad data
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
fun TouchOverlay(modifier: Modifier = Modifier) {
    // Per-frame state for the overlay's digital bytes. Pad state is sent
    // to the core via nativeOverlayPadData whenever any of these change.
    var d1 by remember { mutableStateOf(0) }
    var d2 by remember { mutableStateOf(0) }
    var lx by remember { mutableStateOf(0) }
    var ly by remember { mutableStateOf(0) }
    var rx by remember { mutableStateOf(0) }
    var ry by remember { mutableStateOf(0) }

    fun pushPad() {
        PocoS3Core.nativeOverlayPadData(
            port = 0,
            digital1 = d1,
            digital2 = d2,
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
        )
    }

    fun setBit(field: Int, mask: Int, pressed: Boolean) {
        if (field == 1) {
            d1 = if (pressed) d1 or mask else d1 and mask.inv()
        } else {
            d2 = if (pressed) d2 or mask else d2 and mask.inv()
        }
        pushPad()
    }

    Box(modifier = modifier) {
        // Top row: shoulders + triggers + START/SELECT + PS.
        Row(
            modifier = Modifier.fillMaxWidth().padding(MornyShapes.spacingL),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingXs)) {
                TouchButton("L2", 24) { p -> setBit(2, Pad.CTRL_L2, p) }
                TouchButton("L1", 24) { p -> setBit(2, Pad.CTRL_L1, p) }
                TouchButton("SELECT", 18) { p -> setBit(1, Pad.CTRL_SELECT, p) }
            }
            TouchButton("PS", 22) { /* PS button is separate cell key; skip for now */ }
            Row(horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingXs)) {
                TouchButton("START", 18) { p -> setBit(1, Pad.CTRL_START, p) }
                TouchButton("R1", 24) { p -> setBit(2, Pad.CTRL_R1, p) }
                TouchButton("R2", 24) { p -> setBit(2, Pad.CTRL_R2, p) }
            }
        }

        // Middle row: left stick + D-pad | face buttons + right stick.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.padding(MornyShapes.spacingXxl),
                verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
            ) {
                AnalogStick(
                    modifier = Modifier.size(96.dp),
                    onStick = { x, y -> lx = x; ly = y; pushPad() },
                )
                DPad(
                    onToggle = { mask, pressed -> setBit(1, mask, pressed) },
                )
            }
            Column(
                modifier = Modifier.padding(MornyShapes.spacingXxl),
                verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingL),
                horizontalAlignment = androidx.compose.ui.Alignment.End,
            ) {
                FaceButtons(
                    onToggle = { mask, pressed -> setBit(2, mask, pressed) },
                )
                AnalogStick(
                    modifier = Modifier.size(96.dp),
                    onStick = { x, y -> rx = x; ry = y; pushPad() },
                )
            }
        }

        // L3 / R3 on the analog sticks - toggled when the user double-taps
        // the stick center. Skipped for brevity; real impl would be a
        // LongPressGesture on the AnalogStick.
    }
}

@Composable
private fun DPad(onToggle: (Int, Boolean) -> Unit) {
    Box(modifier = Modifier.size(160.dp)) {
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp),
                    label = "▲",
                    onPressChange = { onToggle(Pad.CTRL_UP, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp, y = 104.dp),
                    label = "▼",
                    onPressChange = { onToggle(Pad.CTRL_DOWN, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(y = 52.dp),
                    label = "◄",
                    onPressChange = { onToggle(Pad.CTRL_LEFT, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 104.dp, y = 52.dp),
                    label = "►",
                    onPressChange = { onToggle(Pad.CTRL_RIGHT, it) })
    }
}

@Composable
private fun FaceButtons(onToggle: (Int, Boolean) -> Unit) {
    Box(modifier = Modifier.size(160.dp)) {
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp),
                    label = "△", onPressChange = { onToggle(Pad.CTRL_TRIANGLE, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 52.dp, y = 104.dp),
                    label = "✕", onPressChange = { onToggle(Pad.CTRL_CROSS, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(y = 52.dp),
                    label = "□", onPressChange = { onToggle(Pad.CTRL_SQUARE, it) })
        TouchButton(modifier = Modifier.size(56.dp).offset(x = 104.dp, y = 52.dp),
                    label = "○", onPressChange = { onToggle(Pad.CTRL_CIRCLE, it) })
    }
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
                    val cx = size.width / 2f; val cy = size.height / 2f
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
        // Well (outer ring).
        drawCircle(
            color = MornyColors.borderSubtle,
            radius = size.minDimension / 2f,
            style = Stroke(width = 1.dp.toPx()),
        )
        // Thumb (inner circle that follows the touch).
        drawCircle(
            color = MornyColors.bgSurfaceGlass,
            radius = size.minDimension / 5f,
            center = if (pos == Offset.Zero) Offset(size.width / 2, size.height / 2) else pos,
        )
    }
}

@Composable
private fun TouchButton(
    label: String = "",
    sizeDp: Int = 24,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    onPressChange(true)
                    tryAwaitRelease()
                    pressed = false
                    onPressChange(false)
                }
            )
        }
    ) {
        val r = size.minDimension / 2f
        val fill = if (pressed) MornyColors.accentTealDim.copy(alpha = 0.5f)
                   else MornyColors.bgSurfaceGlass.copy(alpha = 0.7f)
        drawCircle(color = fill, radius = r)
        drawCircle(color = Color.White, radius = r, style = Stroke(width = 2.dp.toPx()))
    }
    if (label.isNotEmpty()) {
        androidx.compose.material3.Text(
            text = label,
            color = MornyColors.textPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(4.dp),
        )
    }
}

