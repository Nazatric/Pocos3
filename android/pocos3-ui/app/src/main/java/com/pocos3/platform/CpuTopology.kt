package com.pocos3.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// =============================================================================
// CPU topology probe.
//
// Parses /sys/devices/system/cpu/cpu*/topology/ at runtime to learn the
// big.LITTLE layout. This is the only source of truth for the CPU side of
// the device profile; we do NOT match against Build.MODEL or "Dimensity"
// strings.
//
// If /sys is not readable (some vendor kernels lock it down), we fall back
// to getauxval-based feature detection only.
// =============================================================================

data class CpuCore(
    val id: Int,
    val isBig: Boolean,
    val maxFreqKHz: Long,
    val packageId: Int,
)

@Serializable
data class CpuTopologyReport(
    val cores: List<CpuCoreSerializable>,
    val bigCores: Int,
    val littleCores: Int,
    val features: List<String>,
    val socHint: String?,
) {
    @Serializable
    data class CpuCoreSerializable(
        val id: Int,
        val isBig: Boolean,
        val maxFreqKHz: Long,
        val packageId: Int,
    )
}

object CpuTopology {
    fun probe(): CpuTopologyReport {
        val cores = readCpuCores()
        val bigCores = cores.count { it.isBig }
        val littleCores = cores.count { !it.isBig }
        val features = readArmFeatures()
        val socHint = readSocHint()
        return CpuTopologyReport(
            cores = cores.map {
                CpuTopologyReport.CpuCoreSerializable(
                    it.id, it.isBig, it.maxFreqKHz, it.packageId
                )
            },
            bigCores = bigCores,
            littleCores = littleCores,
            features = features,
            socHint = socHint,
        )
    }

    private fun readCpuCores(): List<CpuCore> {
        val sysCpu = File("/sys/devices/system/cpu")
        if (!sysCpu.isDirectory) return emptyList()

        return sysCpu.listFiles { f -> f.isDirectory && f.name.matches(Regex("cpu\\d+")) }
            ?.mapNotNull { cpuDir ->
                val id = cpuDir.name.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
                val maxFreq = File(cpuDir, "cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull() ?: 0L
                val pkgId = File(cpuDir, "topology/physical_package_id").readText().trim().toIntOrNull() ?: 0
                // Heuristic: A "big" core peaks above ~2.4 GHz (2_400_000 KHz). This
                // threshold is the modern mobile-SoC big/LITTLE dividing line.
                val isBig = maxFreqKHz >= 2_400_000
                CpuCore(id, isBig, maxFreqKHz, pkgId)
            }
            ?.sortedBy { it.id }
            ?: emptyList()
    }

    private fun readArmFeatures(): List<String> {
        // getauxval(AT_HWCAP) and AT_HWCAP2 give us a bitset of supported ARM
        // features. We translate the relevant bits to readable names.
        val features = mutableListOf<String>()
        // AT_HWCAP = 16, AT_HWCAP2 = 26 on ARM64.
        val hwcap = readAuxv(16)
        val hwcap2 = readAuxv(26)

        // HWCAP bits (arch/arm64/include/uapi/asm/hwcap.h)
        if ((hwcap and (1L shl 1)) != 0L) features += "FP"
        if ((hwcap and (1L shl 2)) != 0L) features += "ASIMD"     // NEON v1
        if ((hwcap and (1L shl 3)) != 0L) features += "EVTSTRM"
        if ((hwcap and (1L shl 4)) != 0L) features += "AES"
        if ((hwcap and (1L shl 5)) != 0L) features += "PMULL"
        if ((hwcap and (1L shl 6)) != 0L) features += "SHA1"
        if ((hwcap and (1L shl 7)) != 0L) features += "SHA2"
        if ((hwcap and (1L shl 8)) != 0L) features += "CRC32"
        if ((hwcap and (1L shl 9)) != 0L) features += "ATOMICS"
        if ((hwcap and (1L shl 10)) != 0L) features += "FPHP"     // FP16
        if ((hwcap and (1L shl 11)) != 0L) features += "ASIMDHP" // FP16 SIMD
        if ((hwcap and (1L shl 12)) != 0L) features += "CPUID"
        if ((hwcap and (1L shl 13)) != 0L) features += "RAS"
        if ((hwcap and (1L shl 14)) != 0L) features += "GIC_BASE"
        if ((hwcap and (1L shl 15)) != 0L) features += "JSCVT"
        if ((hwcap and (1L shl 16)) != 0L) features += "FCMA"
        if ((hwcap and (1L shl 17)) != 0L) features += "LRCPC"
        if ((hwcap and (1L shl 18)) != 0L) features += "DCPOP"
        if ((hwcap and (1L shl 19)) != 0L) features += "SHA3"
        if ((hwcap and (1L shl 20)) != 0L) features += "SM3"
        if ((hwcap and (1L shl 21)) != 0L) features += "SM4"
        if ((hwcap and (1L shl 22)) != 0L) features += "ASIMDDP" // dot-prod
        if ((hwcap and (1L shl 23)) != 0L) features += "SHA512"
        if ((hwcap and (1L shl 24)) != 0L) features += "SVE"
        if ((hwcap and (1L shl 25)) != 0L) features += "FRINT"
        if ((hwcap and (1L shl 26)) != 0L) features += "SB"
        if ((hwcap and (1L shl 27)) != 0L) features += "SSBS"
        if ((hwcap and (1L shl 28)) != 0L) features += "MTE"
        if ((hwcap and (1L shl 29)) != 0L) features += "CSV2"

        // HWCAP2 bits
        if ((hwcap2 and (1L shl 0)) != 0L) features += "DCPODP"
        if ((hwcap2 and (1L shl 1)) != 0L) features += "SVE2"
        if ((hwcap2 and (1L shl 2)) != 0L) features += "SVEAES"
        if ((hwcap2 and (1L shl 3)) != 0L) features += "SVEPMULL"
        if ((hwcap2 and (1L shl 4)) != 0L) features += "SVEBITPERM"
        if ((hwcap2 and (1L shl 5)) != 0L) features += "SVESHA3"
        if ((hwcap2 and (1L shl 6)) != 0L) features += "SVESM4"
        if ((hwcap2 and (1L shl 7)) != 0L) features += "FLAGM2"
        if ((hwcap2 and (1L shl 8)) != 0L) features += "FRTS"
        if ((hwcap2 and (1L shl 9)) != 0L) features += "SVEBF16"
        if ((hwcap2 and (1L shl 10)) != 0L) features += "I8MM"
        if ((hwcap2 and (1L shl 11)) != 0L) features += "BF16"
        if ((hwcap2 and (1L shl 12)) != 0L) features += "DGH"
        if ((hwcap2 and (1L shl 13)) != 0L) features += "RNG"
        if ((hwcap2 and (1L shl 14)) != 0L) features += "BTI"
        if ((hwcap2 and (1L shl 15)) != 0L) features += "MTE3"

        return features
    }

    private fun readAuxv(atType: Int): Long {
        // The JVM does not expose getauxval() directly; we parse /proc/self/auxv.
        // Each auxv entry is a pair of unsigned longs (type, value).
        val auxv = File("/proc/self/auxv").inputStream().use { stream ->
            val buf = ByteArray(16)
            val list = mutableListOf<Pair<Long, Long>>()
            while (stream.read(buf) == 16) {
                val type = bytesToLong(buf, 0)
                val value = bytesToLong(buf, 8)
                if (type == 0L) break  // AT_NULL
                list += type to value
            }
            list
        }
        return auxv.firstOrNull { it.first == atType.toLong() }?.second ?: 0L
    }

    private fun bytesToLong(buf: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[off + i].toLong() and 0xFF) shl (i * 8))
        return v
    }

    private fun readSocHint(): String? {
        // /proc/cpuinfo's "Hardware" line is the closest the kernel gives us
        // to a SoC family name. Empty on some kernels.
        val cpuinfo = File("/proc/cpuinfo").readText()
        val match = Regex("Hardware\\s*:\\s*(\\S.+)").find(cpuinfo)
        return match?.groupValues?.get(1)?.trim()
    }
}

// =============================================================================
// RAM probe.
// =============================================================================

@Serializable
data class MemoryReport(val totalBytes: Long, val availableBytes: Long, val isLowRam: Boolean)

object MemoryProbe {
    fun probe(context: Context): MemoryReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryReport(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            isLowRam = am.isLowRamDevice,
        )
    }
}
