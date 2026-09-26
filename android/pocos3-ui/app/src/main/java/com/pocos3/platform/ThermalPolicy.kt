package com.pocos3.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.pocos3.runtime.PocoS3Core
import java.util.concurrent.Executors

// =============================================================================
// Thermal policy.
//
// Samples PowerManager.getThermalStatusHeadroom() every 30 seconds during
// emulation and reduces background work when the device is hot. This
// intentionally runs *ahead* of the system's thermal throttle so the
// Mali driver does not start returning VK_ERROR_DEVICE_LOST.
// =============================================================================

object ThermalPolicy {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun startMonitoring(context: Context) {
        if (running) return
        running = true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Note: getThermalStatusHeadroom requires API 30+; on older devices
        // we degrade to just observing the thermal status enum.
        executor.scheduleAtFixedRate({
            val headroom = if (android.os.Build.VERSION.SDK_INT >= 30) {
                runCatching { pm.getThermalHeadroom(30) }.getOrDefault(1f)
            } else {
                // No headroom API; use status code as a coarse hint.
                val s = pm.currentThermalStatus
                when (s) {
                    PowerManager.THERMAL_STATUS_NONE -> 1f
                    PowerManager.THERMAL_STATUS_LIGHT -> 0.7f
                    PowerManager.THERMAL_STATUS_MODERATE -> 0.5f
                    PowerManager.THERMAL_STATUS_SEVERE -> 0.3f
                    PowerManager.THERMAL_STATUS_CRITICAL -> 0.1f
                    else -> 0.05f
                }
            }
            PocoS3Core.nativeSetThermals(
                cpu = headroom, gpu = headroom, battery = headroom, show = headroom < 0.3f
            )
        }, 0, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun stopMonitoring() {
        running = false
        executor.shutdownNow()
    }
}
