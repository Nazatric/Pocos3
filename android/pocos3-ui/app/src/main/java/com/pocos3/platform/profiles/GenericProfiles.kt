package com.pocos3.platform.profiles

import com.pocos3.platform.CpuTopologyReport
import com.pocos3.platform.DeviceProfile
import com.pocos3.platform.MemoryReport

// =============================================================================
// Generic fallback profiles.
//
// When the device does not match the POCO X7 Pro signature, PocoS3 picks
// one of these based on the detected GPU family + capability tier.
// =============================================================================

object GenericMaliProfile {
    fun profileFor(cpu: CpuTopologyReport, mem: MemoryReport) = DeviceProfile(
        profileName = "GENERIC_MALI",
        vulkanCapabilityTier = "ADVANCED",
        // Same defaults as PocoX7ProProfile, just more conservative on
        // SPU thread count and resolution scale.
        numSPUThreads = (cpu.bigCores - 1).coerceAtLeast(2),
        resolutionScale = 1.0f,
        maxFrameRate = 60,
    )
}

object GenericAdrenoProfile {
    fun profileFor(cpu: CpuTopologyReport, mem: MemoryReport) = DeviceProfile(
        profileName = "GENERIC_ADRENO",
        vulkanCapabilityTier = "OPTIMIZED",
        numSPUThreads = (cpu.bigCores - 1).coerceAtLeast(2),
        resolutionScale = 1.0f,
        maxFrameRate = 60,
    )
}

object GenericFallbackProfile {
    fun profileFor(cpu: CpuTopologyReport, mem: MemoryReport, display: com.pocos3.platform.DisplayReport) = DeviceProfile(
        profileName = "GENERIC_FALLBACK",
        vulkanCapabilityTier = "BASELINE",
        // Conservative everywhere.
        numSPUThreads = 2,
        ppuDecoder = "INTERPRETER",  // safe default
        spuDecoder = "INTERPRETER",  // safe default
        resolutionScale = 1.0f,
        maxFrameRate = 30,
    )
}
