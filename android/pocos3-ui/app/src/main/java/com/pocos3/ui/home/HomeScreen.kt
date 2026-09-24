package com.pocos3.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes

// =============================================================================
// Home screen - main game library + tabs for Settings / System.
//
// This is a layout sketch with real Compose + Mornye tokens. The data
// (game list) is fed by a ViewModel that talks to GameRepository.
// =============================================================================

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSystem: () -> Unit,
    onPlayGame: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            onOpenSettings = onOpenSettings,
            onOpenSystem = onOpenSystem,
        )
        GameList(
            modifier = Modifier.weight(1f),
            onPlayGame = onPlayGame,
        )
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit, onOpenSystem: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MornyShapes.spacingL),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "PocoS3",
            style = MaterialTheme.typography.displayMedium,
            color = MornyColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingS)) {
            TextButton(onClick = onOpenSettings) {
                Text("Settings", color = MornyColors.accentTeal)
            }
            TextButton(onClick = onOpenSystem) {
                Text("System", color = MornyColors.accentTeal)
            }
        }
    }
}

@Composable
private fun GameList(modifier: Modifier, onPlayGame: (String) -> Unit) {
    val games = emptyList<GameModel>()  // TODO: ViewModel
    if (games.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(MornyShapes.spacingXxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No games found.", style = MaterialTheme.typography.bodyLarge,
                 color = MornyColors.textSecondary)
            Spacer(Modifier.height(MornyShapes.spacingS))
            Text("Pick a directory to scan.",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MornyColors.textTertiary)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = MornyShapes.spacingL),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
    ) {
        items(games) { game ->
            GameCard(game) { onPlayGame(game.path) }
        }
    }
}

@Composable
private fun GameCard(game: GameModel, onPlay: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MornyShapes.radiusCard),
        colors = CardDefaults.cardColors(containerColor = MornyColors.bgSurface1),
        border = androidx.compose.foundation.BorderStroke(1.dp, MornyColors.borderSubtle),
        onClick = onPlay,
    ) {
        Column(modifier = Modifier.padding(MornyShapes.spacingM)) {
            Text(game.title,
                 style = MaterialTheme.typography.titleLarge,
                 color = MornyColors.textPrimary)
            Text(game.titleId,
                 style = MaterialTheme.typography.bodySmall,
                 color = MornyColors.textTertiary)
        }
    }
}

data class GameModel(
    val path: String,
    val title: String,
    val titleId: String,
    val lastPlayed: Long? = null,
)
