package com.pocos3.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pocos3.ui.home.GameModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

// =============================================================================
// Game repository.
//
// Holds the list of games the user has scanned from their SAF-picked game
// directory. Persists the list + the last-picked directory URI to
// SharedPreferences so the app remembers them across launches.
//
// This is the data source for HomeViewModel and the GameLibraryScreen.
// =============================================================================

class GameRepository private constructor(private val context: Context) {

    private val _games = MutableStateFlow<List<GameModel>>(emptyList())
    val games: StateFlow<List<GameModel>> = _games.asStateFlow()

    private val _gameDirUri = MutableStateFlow<Uri?>(null)
    val gameDirUri: StateFlow<Uri?> = _gameDirUri.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Restore the last game directory URI on startup.
        prefs.getString(KEY_GAME_DIR_URI, null)?.let { uriString ->
            _gameDirUri.value = Uri.parse(uriString)
            // If we have a saved URI, immediately re-scan it.
            val uri = _gameDirUri.value
            if (uri != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    scan(uri)
                }
            }
        }
    }

    suspend fun setGameDirectory(uri: Uri) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_GAME_DIR_URI, uri.toString()).apply()
        _gameDirUri.value = uri
        scan(uri)
    }

    suspend fun scan(uri: Uri) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null) {
            _games.value = emptyList()
            return@withContext
        }
        // PS3 title ID patterns:
        val titleIdRegex = Regex(
            "^(BCES|BCUS|BLES|BLUS|BLJM|BCJM|BCJS|BLJS|BCKS|BCJK|BLJK|" +
            "NPUB|NPEB|NPEJ|NPJB|NPJC|NPHB|NPHJ|NPHD)[0-9A-F]{5}$"
        )
        val games = mutableListOf<GameModel>()
        for (child in root.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (!titleIdRegex.matches(name)) continue
            val hasGame = child.findFile("PS3_GAME")?.isDirectory == true ||
                          child.findFile("EBOOT.BIN")?.isFile == true
            if (!hasGame) continue
            games += GameModel(
                path = child.uri.toString(),
                title = name,  // Will be replaced by PARAM.SFO read at boot.
                titleId = name,
            )
        }
        _games.value = games.sortedBy { it.title }
    }

    companion object {
        private const val PREFS_NAME = "pocos3"
        private const val KEY_GAME_DIR_URI = "game_dir_uri"

        @Volatile private var instance: GameRepository? = null
        fun get(context: Context): GameRepository =
            instance ?: synchronized(this) {
                instance ?: GameRepository(context.applicationContext).also { instance = it }
            }
    }
}
