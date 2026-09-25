package com.pocos3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// =============================================================================
// Mornye theme entry point.
//
// This intentionally does NOT use Material 3's default color roles
// (primary / onPrimary / primaryContainer / ...). Mornye defines its own
// role names that map cleanly to the MornyColors palette. Material's
// colorScheme is populated only so Material 3 components inherit the right
// accent; the Mornye components below pull from MornyColors directly.
// =============================================================================

private val MornyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 53.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.6.sp,
    ),
)

private val DarkColorScheme = darkColorScheme(
    primary = MornyColors.accentTeal,
    onPrimary = MornyColors.textPrimary,
    primaryContainer = MornyColors.accentTealDim,
    onPrimaryContainer = MornyColors.textPrimary,
    secondary = MornyColors.accentAmber,
    onSecondary = MornyColors.bgDeep,
    secondaryContainer = MornyColors.accentAmberDim,
    onSecondaryContainer = MornyColors.textPrimary,
    tertiary = MornyColors.accentAmber,
    background = MornyColors.bgDeep,
    onBackground = MornyColors.textPrimary,
    surface = MornyColors.bgSurface1,
    onSurface = MornyColors.textPrimary,
    surfaceVariant = MornyColors.bgSurface0,
    onSurfaceVariant = MornyColors.textSecondary,
    surfaceTint = MornyColors.accentTeal,
    outline = MornyColors.borderSubtle,
    outlineVariant = MornyColors.borderActive,
    error = MornyColors.signalDanger,
    onError = MornyColors.textPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = MornyLightColors.accentTeal,
    onPrimary = MornyLightColors.textPrimary,
    background = MornyLightColors.bgDeep,
    onBackground = MornyLightColors.textPrimary,
    surface = MornyLightColors.bgSurface1,
    onSurface = MornyLightColors.textPrimary,
    secondary = MornyLightColors.accentAmber,
    onSecondary = MornyLightColors.bgDeep,
    outline = MornyLightColors.borderSubtle,
    error = MornyLightColors.signalDanger,
)

@Composable
fun MornyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = MornyTypography,
        shapes = MornyShapes.shapes,
        content = content,
    )
}
