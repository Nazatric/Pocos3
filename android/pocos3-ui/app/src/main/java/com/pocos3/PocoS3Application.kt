package com.pocos3

import android.app.Application
import android.util.Log
import com.pocos3.platform.DeviceProfileRegistry
import com.pocos3.platform.SocIdentifier
import com.pocos3.platform.ThermalPolicy
import com.pocos3.platform.VulkanProbe
import com.pocos3.runtime.PocoS3Core
import org.json.JSONObject

// =============================================================================
// PocoS3 application entry point.
//
// Performs the first-boot setup: detects the device profile, initialises
// the JNI glue (which in turn dlopen()s libpocos3-core.so), feeds the
// detected profile + Vulkan capabilities to the core via setProfile(json)
// and setCapabilities(json).
// =============================================================================

class PocoS3Application : Application() {
    override fun onCreate() {
        super.onCreate()

        val profile = DeviceProfileRegistry.detect(this)
        val profileJson = DeviceProfileRegistry.toJson(profile)

        // Initialise the core. dlopen + resolve PocoS3Api table.
        if (!PocoS3Core.initialise(this)) {
            Log.e("PocoS3", "PocoS3Core.initialise() failed; " +
                    "core library missing or incompatible. " +
                    "Did you run ./android/configure.sh?")
            return
        }

        // Hand the profile JSON to the core.
        PocoS3Core.nativeSetProfile(profileJson)

        // Probe Vulkan capabilities and forward to the core.
        val report = VulkanProbe.probe(this)
        val capsJson = JSONObject().apply {
            put("tier", report.tier.name)
            put("vendorId", report.vendorId)
            put("deviceId", report.deviceId)
            put("deviceName", report.deviceName)
            put("driverName", report.driverName)
            put("driverInfo", report.driverInfo)
            put("apiVersion", report.apiVersion)
            put("apiVersionString", report.apiVersionString)
            put("extensions", report.extensions)
            put("knownBugs", report.knownBugs)
            put("maxBoundDescriptorSets", report.maxBoundDescriptorSets)
            put("maxUpdateAfterBindDescriptors", report.maxUpdateAfterBindDescriptors)
            put("timelineSemaphores", report.timelineSemaphores)
            put("dynamicRendering", report.dynamicRendering)
            put("synchronization2", report.synchronization2)
            put("descriptorIndexing", report.descriptorIndexing)
            put("pipelineLibrary", report.pipelineLibrary)
            put("memoryBudget", report.memoryBudget)
            put("presentTiming", report.presentTiming)
        }.toString()
        PocoS3Core.nativeSetCapabilities(capsJson)

        // Tell the core about the SoC family (informational).
        val socInfo = SocIdentifier.identify()
        PocoS3Core.nativeSetSocInfo(socInfo)

        // Start the thermal policy monitor.
        ThermalPolicy.startMonitoring(this)

        Log.i("PocoS3", "PocoS3 ready; profile=${profile.profileName}, " +
              "tier=${report.tier}, soc=$socInfo")
    }
}
