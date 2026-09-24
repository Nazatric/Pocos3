package com.pocos3

import android.app.Application
import android.os.PowerManager
import androidx.lifecycle.ProcessLifecycleOwner
import com.pocos3.platform.DeviceProfileRegistry
import com.pocos3.platform.ThermalPolicy
import com.pocos3.runtime.PocoS3Core

// =============================================================================
// PocoS3 application entry point.
//
// Performs the *first-boot* setup: detects the device profile, initialises
// the JNI glue (which in turn dlopen()s libpocos3-core.so), and feeds the
// detected profile to the core via setProfile(json).
// =============================================================================

class PocoS3Application : Application() {
    override fun onCreate() {
        super.onCreate()

        // Probe device + pick a profile.
        val profile = DeviceProfileRegistry.detect(this)
        com.pocos3.BuildConfig
        val profileJson = DeviceProfileRegistry.toJson(profile)

        // Initialise the core. dlopen + resolve PocoS3Api table.
        if (!PocoS3Core.initialise(this)) {
            // The core .so is missing or incompatible. We don't crash the
            // app; the UI will show a setup error screen.
            android.util.Log.e("PocoS3", "PocoS3Core.initialise() failed; " +
                    "core library missing or patches not applied. " +
                    "Did you run ./android/configure.sh?")
            return
        }

        // Hand the profile JSON to the core.
        PocoS3Core.nativeSetProfile(profileJson)
        PocoS3Core.nativeSetSocInfo(profile.profileName)

        // Set up the thermal policy. It samples PowerManager every 30s
        // during emulation and pushes the result to the core.
        ThermalPolicy.startMonitoring(this)
    }
}
