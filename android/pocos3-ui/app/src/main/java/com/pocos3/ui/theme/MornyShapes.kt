package com.pocos3.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =============================================================================
// Mornye geometry tokens. See docs/MORNYE_DESIGN.md.
// =============================================================================

object MornyShapes {
    val radiusCard = 28.dp
    val radiusButton = 24.dp
    val radiusChip = 999.dp
    val radiusField = 16.dp

    val spacingXxs = 4.dp
    val spacingXs = 8.dp
    val spacingS = 12.dp
    val spacingM = 16.dp
    val spacingL = 24.dp
    val spacingXl = 32.dp
    val spacingXxl = 48.dp

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(spacingS),
        small = RoundedCornerShape(radiusField),
        medium = RoundedCornerShape(radiusButton),
        large = RoundedCornerShape(radiusCard),
        extraLarge = RoundedCornerShape(radiusCard),
    )
}
