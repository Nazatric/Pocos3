package com.pocos3.data

import android.content.Context
import com.pocos3.platform.DeviceProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// =============================================================================
// Per-game settings.
//
// Stores user overrides for a specific game (by title ID). When the user
// boots a game, the emulator loads the global DeviceProfile, then overlays
// any per-game overrides on top. Games without overrides inherit the
// global defaults.
//
// Stored in a single JSON file per game at:
//   <data>/games/<titleId>/overrides.json
// =============================================================================

@Serializable
data class GameOverrides(
    val titleId: String,
    val resolutionScale: Float? = null,
    val maxFrameRate: Int? = null,
    val performancePreset: String? = null,
    val ppuDecoder: String? = null,
    val spuDecoder: String? = null,
    val numSPUThreads: Int? = null,
    val shaderCacheMode: String? = null,
    val customConfig: Map<String, String> = emptyMap(),
)

class GameSettingsRepository(private val context: Context) {

    fun loadOverrides(titleId: String): GameOverrides? {
        val file = java.io.File(context.filesDir, "games/$titleId/overrides.json")
        if (!file.exists()) return null
        return runCatching {
            Json.decodeFromString(GameOverrides.serializer(), file.readText())
        }.getOrNull()
    }

    fun saveOverrides(overrides: GameOverrides) {
        val dir = java.io.File(context.filesDir, "games/${overrides.titleId}")
        dir.mkdirs()
        val file = java.io.File(dir, "overrides.json")
        file.writeText(Json.encodeToString(GameOverrides.serializer(), overrides))
    }

    fun applyOverrides(base: DeviceProfile, overrides: GameOverrides?): DeviceProfile {
        if (overrides == null) return base
        return base.copy(
            resolutionScale = overrides.resolutionScale ?: base.resolutionScale,
            maxFrameRate = overrides.maxFrameRate ?: base.maxFrameRate,
            performancePreset = overrides.performancePreset ?: base.performancePreset,
            ppuDecoder = overrides.ppuDecoder ?: base.ppuDecoder,
            spuDecoder = overrides.spuDecoder ?: base.spuDecoder,
            numSPUThreads = overrides.numSPUThreads ?: base.numSPUThreads,
            shaderCacheMode = overrides.shaderCacheMode ?: base.shaderCacheMode,
            profileName = "${base.profileName}+${overrides.titleId}",
        )
    }

    companion object {
        @Volatile private var instance: GameSettingsRepository? = null
        fun get(context: Context): GameSettingsRepository =
            instance ?: synchronized(this) {
                instance ?: GameSettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
