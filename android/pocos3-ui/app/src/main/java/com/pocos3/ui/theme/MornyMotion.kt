package com.pocos3.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

// =============================================================================
// Mornye motion tokens. See docs/MORNYE_DESIGN.md.
//
// The Mornye motion curve is `cubic-bezier(0.2, 0.6, 0.05, 1)` - fast attack,
// very long settle. Material's `FastOutSlowInElastic` is too soft; this is
// the family the entire app uses for non-list animations.
// =============================================================================

object MornyMotion {
    val MornyEasing: Easing = CubicBezierEasing(0.2f, 0.6f, 0.05f, 1.0f)

    const val DurTap = 90
    const val DurFadeInShort = 120
    const val DurFadeInDefault = 220
    const val DurFadeInSlow = 380
    const val DurSlideUpDefault = 280
    const val DurSheetExpand = 320
    const val DurGlassShimmer = 1200

    fun <T> fadeIn(durationMillis: Int = DurFadeInDefault) =
        tween<T>(durationMillis = durationMillis, easing = MornyEasing)

    fun <T> slideUp(durationMillis: Int = DurSlideUpDefault) =
        tween<T>(durationMillis = durationMillis, easing = MornyEasing)
}
