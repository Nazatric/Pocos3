package com.pocos3.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================================
// Build-time-independent device information bundle. Aggregates the
// probes in this package into a single string for the SystemInfo screen
// and crash reports.
// =============================================================================

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val board: String,
    val hardware: String,
    val androidRelease: String,
    val androidSdk: Int,
    val supportedAbis: List<String>,
    val socHint: String?,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowRam: Boolean,
    val bigCores: Int,
    val littleCores: Int,
    val armFeatures: List<String>,
    val displayRefreshRateHz: Float,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val displayDensityDpi: Int,
) {
    override fun toString(): String = buildString {
        appendLine("manufacturer=$manufacturer")
        appendLine("model=$model")
        appendLine("board=$board")
        appendLine("hardware=$hardware")
        appendLine("android=$androidRelease (sdk $androidSdk)")
        appendLine("abis=${supportedAbis.joinToString(",")}")
        appendLine("soc=$socHint")
        appendLine("ram=${totalRamBytes / 1024 / 1024} MB total, ${availableRamBytes / 1024 / 1024} MB available, lowRam=$isLowRam")
        appendLine("cpu=$bigCores big + $littleCores LITTLE")
        appendLine("armFeatures=${armFeatures.joinToString(",")}")
        appendLine("display=${displayWidthPx}x${displayHeightPx}@${displayRefreshRateHz}Hz ${displayDensityDpi}dpi")
    }
}

object DeviceInfoAggregator {
    suspend fun probe(context: Context): DeviceInfo = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cpu = CpuTopology.probe()
        val display = DisplayProbe.probe(context)
        DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            androidRelease = Build.VERSION.RELEASE ?: "?",
            androidSdk = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            socHint = SocIdentifier.identify(),
            totalRamBytes = mem.totalMem,
            availableRamBytes = mem.availMem,
            isLowRam = am.isLowRamDevice,
            bigCores = cpu.bigCores,
            littleCores = cpu.littleCores,
            armFeatures = cpu.features,
            displayRefreshRateHz = display.refreshRateHz,
            displayWidthPx = display.widthPx,
            displayHeightPx = display.heightPx,
            displayDensityDpi = display.densityDpi,
        )
    }
}
