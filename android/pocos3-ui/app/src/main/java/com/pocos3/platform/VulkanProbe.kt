package com.pocos3.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.json.JSONObject

// =============================================================================
// Vulkan capability probe.
//
// Creates a VkInstance via JNI, enumerates VkPhysicalDevices, picks the
// best one, reads the device + driver properties + extension list, and
// sorts the device into a capability tier (BASELINE / OPTIMIZED / ADVANCED
// / MALI_OPTIMIZED).
//
// The actual vk* function calls happen in native code; this Kotlin side
// orchestrates the call and parses the JSON the native side returns.
// The native probe entry point is exported by libpocos3-glue.so:
//
//   public static native String nativeProbeVulkan()
//
// The native impl creates a VkInstance, picks a physical device, builds
// the JSON, and tears down the instance.
// =============================================================================

@Serializable
enum class VulkanCapabilityTier {
    BASELINE, OPTIMIZED, ADVANCED, MALI_OPTIMIZED
}

@Serializable
data class VulkanCapabilityReport(
    val tier: VulkanCapabilityTier,
    val vendorId: Int,
    val deviceId: Int,
    val deviceName: String,
    val driverName: String,
    val driverInfo: String,
    val apiVersion: Int,
    val apiVersionString: String,
    val extensions: List<String>,
    val knownBugs: List<String>,
    val maxBoundDescriptorSets: Int,
    val maxUpdateAfterBindDescriptors: Int,
    val timelineSemaphores: Boolean,
    val dynamicRendering: Boolean,
    val synchronization2: Boolean,
    val descriptorIndexing: Boolean,
    val pipelineLibrary: Boolean,
    val memoryBudget: Boolean,
    val presentTiming: Boolean,
) {
    val isMali: Boolean get() = vendorId == 0x13B5
    val isAdreno: Boolean get() = vendorId == 0x5143
    val isMaliG720Family: Boolean
        get() = isMali && driverName.startsWith("Mali-G", ignoreCase = true) &&
                (driverName.contains("G720") || driverName.contains("Immortalis"))
}

// =============================================================================
// Inline capability detector that runs purely on Java/Kotlin — no native
// call required. Used as a *fallback* when the native probe isn't
// available (e.g. early in onboarding, before dlopen).
// =============================================================================

object VulkanProbeFallback {

    /**
     * Returns the best-guess tier using only ActivityManager PackageManager
     * info. Less accurate than native probe; use as a hint only.
     */
    fun guess(context: Context): VulkanCapabilityReport {
        // Without a VkInstance we can only guess; default to BASELINE.
        return VulkanCapabilityReport(
            tier = VulkanCapabilityTier.BASELINE,
            vendorId = 0,
            deviceId = 0,
            deviceName = Build.HOST ?: "unknown",
            driverName = "unknown",
            driverInfo = "unknown",
            apiVersion = 0,
            apiVersionString = "0.0.0",
            extensions = emptyList(),
            knownBugs = emptyList(),
            maxBoundDescriptorSets = 4,
            maxUpdateAfterBindDescriptors = 0,
            timelineSemaphores = false,
            dynamicRendering = false,
            synchronization2 = false,
            descriptorIndexing = false,
            pipelineLibrary = false,
            memoryBudget = false,
            presentTiming = false,
        )
    }
}

// =============================================================================
// Display probe.
// =============================================================================

@Serializable
data class DisplayReport(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val supportedRefreshRates: List<Float>,
)

object DisplayProbe {
    fun probe(context: Context): DisplayReport {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Use getMaximumWindowMetrics on Android 11+; fall back to current metrics.
        val metrics = if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            android.graphics.Rect().also { /* placeholder */ }
        }
        val display = wm.defaultDisplay
        val refreshRates = if (Build.VERSION.SDK_INT >= 31) {
            display.supportedRefreshRates.toList()
        } else {
            listOf(display.refreshRate)
        }
        val dm = context.resources.displayMetrics
        return DisplayReport(
            widthPx = if (Build.VERSION.SDK_INT >= 30) metrics.width() else dm.widthPixels,
            heightPx = if (Build.VERSION.SDK_INT >= 30) metrics.height() else dm.heightPixels,
            densityDpi = dm.densityDpi,
            refreshRateHz = display.refreshRate,
            supportedRefreshRates = refreshRates,
        )
    }
}
