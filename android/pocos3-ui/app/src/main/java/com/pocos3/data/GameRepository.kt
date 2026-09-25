package com.pocos3.data

import android.content.Context
import android.net.Uri
import com.pocos3.storage.GameScanner
import com.pocos3.ui.home.GameModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================================
// Game repository.
//
// Holds the list of games the user has scanned from their SAF-picked game
// directory. Persists the directory URI to SharedPreferences so the app
// remembers it across launches.
//
// The actual scanning + PARAM.SFO parsing is delegated to GameScanner.
// =============================================================================

class GameRepository private constructor(private val context: Context) {

    // Application-scoped CoroutineScope avoids GlobalScope leaks across
    // Activity recreation.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _games = MutableStateFlow<List<GameModel>>(emptyList())
    val games: StateFlow<List<GameModel>> = _games.asStateFlow()

    private val _gameDirUri = MutableStateFlow<Uri?>(null)
    val gameDirUri: StateFlow<Uri?> = _gameDirUri.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scanner = GameScanner()

    init {
        prefs.getString(KEY_GAME_DIR_URI, null)?.let { uriString ->
            _gameDirUri.value = Uri.parse(uriString)
            val uri = _gameDirUri.value
            if (uri != null) {
                scope.launch { scan(uri) }
            }
        }
    }

    suspend fun setGameDirectory(uri: Uri) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_GAME_DIR_URI, uri.toString()).apply()
        _gameDirUri.value = uri
        scan(uri)
    }

    suspend fun scan(uri: Uri) = withContext(Dispatchers.IO) {
        _games.value = scanner.scan(context, uri)
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
