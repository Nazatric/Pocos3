package com.pocos3.platform

import android.content.Context
import com.pocos3.runtime.PocoS3Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

// =============================================================================
// Live settings manager.
//
// Subscribes to PocoS3Settings (DataStore-backed user prefs) and pushes the
// merged profile to the core via _pocos3_setProfile on every change. The
// merged profile = detected device profile (e.g. POCO_X7_PRO) overlaid with
// the user's runtime overrides (resolution, fps, touch overlay, preset).
//
// Uses an application-scoped CoroutineScope so jobs are cancelled cleanly on
// process death (instead of GlobalScope which leaks across Activity
// recreation).
// =============================================================================

class LiveSettingsManager private constructor(
    private val context: Context,
    private val settings: com.pocos3.ui.settings.PocoS3Settings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _liveProfile = MutableStateFlow<DeviceProfile?>(null)
    val liveProfile: StateFlow<DeviceProfile?> = _liveProfile.asStateFlow()

    init {
        scope.launch {
            kotlinx.coroutines.coroutineScope {
                launch { settings.resolutionScale.collect { pushLive() } }
                launch { settings.fpsLimit.collect { pushLive() } }
                launch { settings.vsyncMode.collect { pushLive() } }
                launch { settings.touchOpacity.collect { pushLive() } }
                launch { settings.touchScale.collect { pushLive() } }
                launch { settings.performancePreset.collect { pushLive() } }
                launch { settings.hudVisible.collect { pushHud() } }
            }
        }
    }

    private suspend fun pushLive() {
        val base = DeviceProfileRegistry.detect(context)
        val snap = settings.snapshot()
        // Merge: user overrides win, fall back to detected profile defaults.
        val merged = base.copy(
            resolutionScale = snap.resolutionScale,
            maxFrameRate = snap.maxFrameRate,
            touchOverlayOpacity = snap.touchOverlayOpacity,
            touchOverlayScale = snap.touchOverlayScale,
            performancePreset = snap.performancePreset,
        )
        _liveProfile.value = merged

        val json = JSONObject().apply {
            put("profileName", merged.profileName)
            put("renderer", merged.renderer)
            put("vulkanCapabilityTier", merged.vulkanCapabilityTier)
            put("resolutionScale", merged.resolutionScale)
            put("framePaceMode", merged.framePaceMode)
            put("maxFrameRate", merged.maxFrameRate)
            put("ppuDecoder", merged.ppuDecoder)
            put("spuDecoder", merged.spuDecoder)
            put("numSPUThreads", merged.numSPUThreads)
            put("touchOverlayOpacity", merged.touchOverlayOpacity)
            put("touchOverlayScale", merged.touchOverlayScale)
            put("thermalPolicy", merged.thermalPolicy)
            put("performancePreset", merged.performancePreset)
        }.toString()
        runCatching { PocoS3Core.nativeSetProfile(json) }
    }

    private fun pushHud() {
        // HUD visibility is purely Kotlin-side; no core call needed.
    }

    companion object {
        @Volatile private var instance: LiveSettingsManager? = null
        fun get(context: Context): LiveSettingsManager =
            instance ?: synchronized(this) {
                instance ?: LiveSettingsManager(
                    context.applicationContext,
                    com.pocos3.ui.settings.PocoS3Settings.get(context.applicationContext)
                ).also { instance = it }
            }
    }
}
