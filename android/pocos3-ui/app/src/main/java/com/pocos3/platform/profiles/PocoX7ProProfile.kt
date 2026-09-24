package com.pocos3.platform.profiles

import com.pocos3.platform.DeviceProfile

// =============================================================================
// POCO X7 Pro profile.
//
// Default profile for the primary target device: POCO X7 Pro with
// Dimensity 8400-Ultra, Mali-G720, 12 GB LPDDR5X, 120 Hz display.
//
// See docs/POCO_X7_PRO_PROFILE.md for the rationale behind every default.
// =============================================================================

object PocoX7ProProfile {
    val profile = DeviceProfile(
        profileName = "POCO_X7_PRO_DIMENSITY_8400_ULTRA_12GB",
        renderer = "VULKAN",
        vulkanCapabilityTier = "MALI_OPTIMIZED",
        resolutionScale = 1.0f,           // PS3 native 720p; upscaling hurts Mali bandwidth
        framePaceMode = "ADAPTIVE_VSYNC",
        maxFrameRate = 60,                // PS3 timing cap; 120 is opt-in
        shaderCacheMode = "PERSISTENT",
        ppuDecoder = "LLVM_RECOMPILER",
        spuDecoder = "LLVM_RECOMPILER",
        spuBlockMaxSize = "MEDIUM",
        numPPUThreads = 2,
        numSPUThreads = 6,                // 4 big cores + 2 overflow
        cpuAffinity = "BIG_CORES_ONLY",
        threadAffinityMode = "HINT",     // hard pins break thermal recovery
        audioBackend = "OBOE_AAUDIO",
        audioSampleRate = 48000,
        audioBufferSize = 256,
        touchOverlayOpacity = 0.55f,
        touchOverlayScale = 1.0f,
        thermalPolicy = "SUSTAINED",
        performancePreset = "BALANCED",
    )
}
