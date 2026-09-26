package com.pocos3.platform

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

data class DisplayReport(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
)

object DisplayProbe {
    fun probe(context: Context): DisplayReport {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return DisplayReport(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRateHz = metrics.refreshRate,
        )
    }
}
