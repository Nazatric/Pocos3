package com.pocos3.platform

enum class VulkanCapabilityTier {
    BASELINE, OPTIMIZED, ADVANCED, MALI_OPTIMIZED
}

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
)
