package com.pocos3.platform

import android.os.Build
import java.io.File

// =============================================================================
// SoC identifier.
//
// Reads /proc/cpuinfo's "Hardware" line (where exposed) and the
// /sys/devices/soc0/* family name (Qualcomm-specific but informative on
// MediaTek too) to derive a best-effort SoC family string.
//
// This is for *informational* use only (HUD display). It is NOT used to
// select a device profile; the profile is a function of detected
// capabilities, not a name match.
// =============================================================================

object SocIdentifier {
    fun identify(): String {
        val cpuinfo = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        val hardware = Regex("Hardware\\s*:\\s*(.+)").find(cpuinfo)?.groupValues?.get(1)?.trim()
        if (!hardware.isNullOrEmpty()) return hardware

        val soc0 = File("/sys/devices/soc0/soc_id")
        if (soc0.exists()) {
            val socId = soc0.readText().trim()
            val machineName = File("/sys/devices/soc0/machine").runCatching { readText().trim() }.getOrNull()
            return machineName ?: "soc:$socId"
        }
        return Build.HARDWARE
    }
}
