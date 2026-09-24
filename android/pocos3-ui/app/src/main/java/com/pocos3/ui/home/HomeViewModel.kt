package com.pocos3.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocos3.data.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// =============================================================================
// Home screen ViewModel.
//
// Wires GameRepository.games to the HomeScreen's state. Also exposes the
// actions the screen can trigger (refresh, pick directory, boot game).
// =============================================================================

class HomeViewModel(
    private val repo: GameRepository,
) : ViewModel() {

    val games: StateFlow<List<GameModel>> =
        repo.games.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val gameDirUri: StateFlow<android.net.Uri?> = repo.gameDirUri

    fun setGameDirectory(uri: android.net.Uri) {
        viewModelScope.launch {
            repo.setGameDirectory(uri)
        }
    }

    companion object {
        fun factory(context: android.content.Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(GameRepository.get(context)) as T
            }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launch(block: suspend () -> Unit) {
    kotlinx.coroutines.launch { block() }
}
