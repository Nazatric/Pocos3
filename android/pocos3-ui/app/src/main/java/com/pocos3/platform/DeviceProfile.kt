package com.pocos3.platform

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

// =============================================================================
// Device profile.
//
// A DeviceProfile is the set of defaults PocoS3 applies when this device
// is detected. It is a *function of detected features*, not a name match.
// The profile is JSON-serialized and handed to the core via
// PocoS3Api.setProfile(json) at startup; the C++ side deserialises it into
// the upstream RPCS3 cfg tree.
// =============================================================================

@Serializable
data class DeviceProfile(
    val profileName: String,
    val renderer: String = "VULKAN",
    val vulkanCapabilityTier: String = "OPTIMIZED",
    val resolutionScale: Float = 1.0f,
    val framePaceMode: String = "ADAPTIVE_VSYNC",
    val maxFrameRate: Int = 60,
    val shaderCacheMode: String = "PERSISTENT",
    val ppuDecoder: String = "LLVM_RECOMPILER",
    val spuDecoder: String = "LLVM_RECOMPILER",
    val spuBlockMaxSize: String = "MEDIUM",
    val numPPUThreads: Int = 2,
    val numSPUThreads: Int = 6,
    val cpuAffinity: String = "BIG_CORES_ONLY",
    val threadAffinityMode: String = "HINT",
    val audioBackend: String = "OBOE_AAUDIO",
    val audioSampleRate: Int = 48000,
    val audioBufferSize: Int = 256,
    val touchOverlayOpacity: Float = 0.55f,
    val touchOverlayScale: Float = 1.0f,
    val thermalPolicy: String = "SUSTAINED",
    val performancePreset: String = "BALANCED",
)

object DeviceProfileRegistry {
    fun detect(context: Context): DeviceProfile {
        val cpu = CpuTopology.probe()
        val mem = MemoryProbe.probe(context)
        val display = DisplayProbe.probe(context)

        // The POCO X7 Pro signature: 4 big + 4 LITTLE, ~12 GB RAM, 120 Hz.
        val isPocoX7ProSignature =
            cpu.bigCores == 4 &&
            cpu.littleCores == 4 &&
            mem.totalBytes in 11_000_000_000..13_000_000_000 &&
            display.refreshRateHz >= 119.0f &&
            cpu.socHint?.contains("mt6899", ignoreCase = true) == true

        return when {
            isPocoX7ProSignature -> PocoX7ProProfile.profile
            else -> GenericFallbackProfile.profileFor(cpu, mem, display)
        }
    }

    fun toJson(profile: DeviceProfile): String = Json.encodeToString(profile)
}
