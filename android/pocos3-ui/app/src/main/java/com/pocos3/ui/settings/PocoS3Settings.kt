package com.pocos3.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocos3.platform.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// =============================================================================
// Settings repository backed by Jetpack DataStore.
//
// Persists user-settable settings across launches. The device profile
// detected at first boot is the *default*; user overrides on top of that
// live here. Per-game overrides live in a separate file.
// =============================================================================

private val Context.pocoDataStore: DataStore<Preferences> by preferencesDataStore("pocos3_settings")

class PocoS3Settings(private val context: Context) {

    // Keys. Each setting is a separate key so reading / writing one does
    // not deserialise the entire settings blob.
    private val KEY_RESOLUTION_SCALE = floatPreferencesKey("resolution_scale")
    private val KEY_FPS_LIMIT = intPreferencesKey("fps_limit")
    private val KEY_VSYNC_MODE = stringPreferencesKey("vsync_mode")
    private val KEY_TOUCH_OPACITY = floatPreferencesKey("touch_opacity")
    private val KEY_TOUCH_SCALE = floatPreferencesKey("touch_scale")
    private val KEY_PERFORMANCE_PRESET = stringPreferencesKey("performance_preset")
    private val KEY_HUD_VISIBLE = booleanPreferencesKey("hud_visible")
    private val KEY_FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    private val KEY_GAME_DIR_URI = stringPreferencesKey("game_dir_uri")
    private val KEY_FIRMWARE_INSTALLED = booleanPreferencesKey("firmware_installed")

    val resolutionScale: Flow<Float> =
        context.pocoDataStore.data.map { it[KEY_RESOLUTION_SCALE] ?: 1.0f }

    val fpsLimit: Flow<Int> =
        context.pocoDataStore.data.map { it[KEY_FPS_LIMIT] ?: 60 }

    val vsyncMode: Flow<String> =
        context.pocoDataStore.data.map { it[KEY_VSYNC_MODE] ?: "ADAPTIVE_VSYNC" }

    val touchOpacity: Flow<Float> =
        context.pocoDataStore.data.map { it[KEY_TOUCH_OPACITY] ?: 0.55f }

    val touchScale: Flow<Float> =
        context.pocoDataStore.data.map { it[KEY_TOUCH_SCALE] ?: 1.0f }

    val performancePreset: Flow<String> =
        context.pocoDataStore.data.map { it[KEY_PERFORMANCE_PRESET] ?: "BALANCED" }

    val hudVisible: Flow<Boolean> =
        context.pocoDataStore.data.map { it[KEY_HUD_VISIBLE] ?: false }

    val firstRunDone: Flow<Boolean> =
        context.pocoDataStore.data.map { it[KEY_FIRST_RUN_DONE] ?: false }

    val gameDirUri: Flow<String?> =
        context.pocoDataStore.data.map { it[KEY_GAME_DIR_URI] }

    val firmwareInstalled: Flow<Boolean> =
        context.pocoDataStore.data.map { it[KEY_FIRMWARE_INSTALLED] ?: false }

    suspend fun setResolutionScale(v: Float) {
        context.pocoDataStore.edit { it[KEY_RESOLUTION_SCALE] = v }
    }
    suspend fun setFpsLimit(v: Int) {
        context.pocoDataStore.edit { it[KEY_FPS_LIMIT] = v }
    }
    suspend fun setVsyncMode(v: String) {
        context.pocoDataStore.edit { it[KEY_VSYNC_MODE] = v }
    }
    suspend fun setTouchOpacity(v: Float) {
        context.pocoDataStore.edit { it[KEY_TOUCH_OPACITY] = v }
    }
    suspend fun setTouchScale(v: Float) {
        context.pocoDataStore.edit { it[KEY_TOUCH_SCALE] = v }
    }
    suspend fun setPerformancePreset(v: String) {
        context.pocoDataStore.edit { it[KEY_PERFORMANCE_PRESET] = v }
    }
    suspend fun setHudVisible(v: Boolean) {
        context.pocoDataStore.edit { it[KEY_HUD_VISIBLE] = v }
    }
    suspend fun markFirstRunDone() {
        context.pocoDataStore.edit { it[KEY_FIRST_RUN_DONE] = true }
    }
    suspend fun setGameDirUri(uri: String?) {
        context.pocoDataStore.edit {
            if (uri == null) it.remove(KEY_GAME_DIR_URI)
            else it[KEY_GAME_DIR_URI] = uri
        }
    }
    suspend fun setFirmwareInstalled(v: Boolean) {
        context.pocoDataStore.edit { it[KEY_FIRMWARE_INSTALLED] = v }
    }

    suspend fun snapshot(): DeviceProfile {
        // Read current values; default to a conservative profile.
        val data = context.pocoDataStore.data.first()
        return DeviceProfile(
            profileName = "USER_OVERRIDE",
            resolutionScale = data[KEY_RESOLUTION_SCALE] ?: 1.0f,
            maxFrameRate = data[KEY_FPS_LIMIT] ?: 60,
            touchOverlayOpacity = data[KEY_TOUCH_OPACITY] ?: 0.55f,
            touchOverlayScale = data[KEY_TOUCH_SCALE] ?: 1.0f,
            performancePreset = data[KEY_PERFORMANCE_PRESET] ?: "BALANCED",
        )
    }

    companion object {
        @Volatile private var instance: PocoS3Settings? = null
        fun get(context: Context): PocoS3Settings =
            instance ?: synchronized(this) {
                instance ?: PocoS3Settings(context.applicationContext).also { instance = it }
            }
    }
}
