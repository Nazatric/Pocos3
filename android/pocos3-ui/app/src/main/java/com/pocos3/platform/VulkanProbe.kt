package com.pocos3.platform

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import com.pocos3.runtime.PocoS3Core

// =============================================================================
// Vulkan probe.
//
// Two paths:
//   1. Native probe (preferred): calls PocoS3Core.nativeProbeVulkan()
//      which delegates to _pocos3_probeVulkan in libpocos3-core.so.
//      That function creates a VkInstance, enumerates VkPhysicalDevices,
//      reads properties + extensions, returns JSON.
//   2. Fallback: VulkanProbeFallback.guess(context) returns BASELINE
//      without touching Vulkan. Used before the core is initialised.
// =============================================================================

object VulkanProbe {

    fun probe(context: Context): VulkanCapabilityReport {
        val json = runCatching { PocoS3Core.nativeProbeVulkan() }.getOrNull()
        return if (json != null && json.isNotBlank() && json != "{}") {
            parse(json)
        } else {
            // Fallback when the native probe is unavailable: returns
            // BASELINE. The real probe runs once the core is loaded.
            VulkanCapabilityReport(
                tier = VulkanCapabilityTier.BASELINE,
                vendorId = 0, deviceId = 0,
                deviceName = android.os.Build.HOST ?: "unknown",
                driverName = "unknown", driverInfo = "unknown",
                apiVersion = 0, apiVersionString = "0.0.0",
                extensions = emptyList(), knownBugs = emptyList(),
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

    private fun parse(json: String): VulkanCapabilityReport {
        val o = Json.parseToJsonElement(json) as JsonObject
        val tierStr = (o["tier"] as? JsonPrimitive)?.content ?: "BASELINE"
        val tier = runCatching { VulkanCapabilityTier.valueOf(tierStr) }
            .getOrDefault(VulkanCapabilityTier.BASELINE)
        val exts = (o["extensions"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        val bugs = (o["knownBugs"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
        return VulkanCapabilityReport(
            tier = tier,
            vendorId = (o["vendorId"] as? JsonPrimitive)?.intOrNull ?: 0,
            deviceId = (o["deviceId"] as? JsonPrimitive)?.intOrNull ?: 0,
            deviceName = (o["deviceName"] as? JsonPrimitive)?.content ?: "unknown",
            driverName = (o["driverName"] as? JsonPrimitive)?.content ?: "unknown",
            driverInfo = (o["driverInfo"] as? JsonPrimitive)?.content ?: "unknown",
            apiVersion = (o["apiVersion"] as? JsonPrimitive)?.intOrNull ?: 0,
            apiVersionString = (o["apiVersionString"] as? JsonPrimitive)?.content ?: "0.0.0",
            extensions = exts,
            knownBugs = bugs,
            maxBoundDescriptorSets = (o["maxBoundDescriptorSets"] as? JsonPrimitive)?.intOrNull ?: 4,
            maxUpdateAfterBindDescriptors = (o["maxUpdateAfterBindDescriptors"] as? JsonPrimitive)?.intOrNull ?: 0,
            timelineSemaphores = (o["timelineSemaphores"] as? JsonPrimitive)?.booleanOrNull ?: false,
            dynamicRendering = (o["dynamicRendering"] as? JsonPrimitive)?.booleanOrNull ?: false,
            synchronization2 = (o["synchronization2"] as? JsonPrimitive)?.booleanOrNull ?: false,
            descriptorIndexing = (o["descriptorIndexing"] as? JsonPrimitive)?.booleanOrNull ?: false,
            pipelineLibrary = (o["pipelineLibrary"] as? JsonPrimitive)?.booleanOrNull ?: false,
            memoryBudget = (o["memoryBudget"] as? JsonPrimitive)?.booleanOrNull ?: false,
            presentTiming = (o["presentTiming"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }
}
