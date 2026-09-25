package com.pocos3.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocos3.EmulationActivity
import com.pocos3.ui.theme.MornyColors
import com.pocos3.ui.theme.MornyShapes

// =============================================================================
// Home screen - the game library.
//
// Wires the HomeViewModel (which holds GameRepository) to the UI.
// The user picks a game directory via the SAF picker; the repository
// scans it and emits games via the games StateFlow.
// =============================================================================

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSystem: () -> Unit,
    onPlayGame: (String) -> Unit,
) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(context))
    val games by vm.games.collectAsState()
    val gameDirUri by vm.gameDirUri.collectAsState()

    // SAF picker for the game directory.
    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission so we can read the directory across launches.
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            vm.setGameDirectory(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            gameDirSet = gameDirUri != null,
            onPickDir = { pickDir.launch(null) },
            onOpenSettings = onOpenSettings,
            onOpenSystem = onOpenSystem,
        )
        if (games.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f), onPick = { pickDir.launch(null) })
        } else {
            GameList(
                modifier = Modifier.weight(1f),
                games = games,
                onPlay = { path -> onPlayGame(path) },
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    gameDirSet: Boolean,
    onPickDir: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(MornyShapes.spacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "PocoS3",
            style = MaterialTheme.typography.displayMedium,
            color = MornyColors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPickDir,
                shape = RoundedCornerShape(MornyShapes.radiusButton),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (gameDirSet) MornyColors.accentTealDim
                                     else MornyColors.accentAmber,
                    contentColor = MornyColors.textPrimary,
                ),
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.width(MornyShapes.spacingXs))
                Text(if (gameDirSet) "Rescan" else "Pick Folder",
                     fontWeight = FontWeight.SemiBold)
            }
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
private fun EmptyState(modifier: Modifier, onPick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(MornyShapes.spacingXxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No games found.",
            style = MaterialTheme.typography.bodyLarge,
            color = MornyColors.textSecondary,
        )
        Spacer(Modifier.height(MornyShapes.spacingS))
        Text(
            "Pick a directory containing your PS3 games.",
            style = MaterialTheme.typography.bodyMedium,
            color = MornyColors.textTertiary,
        )
        Spacer(Modifier.height(MornyShapes.spacingL))
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(MornyShapes.radiusButton),
            colors = ButtonDefaults.buttonColors(
                containerColor = MornyColors.accentAmber,
                contentColor = MornyColors.bgDeep,
            ),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(MornyShapes.spacingXs))
            Text("Pick Game Directory", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GameList(modifier: Modifier, games: List<GameModel>, onPlay: (String) -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = MornyShapes.spacingL),
        verticalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
    ) {
        items(games, key = { it.path }) { game ->
            GameCard(game) { onPlay(game.path) }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(MornyShapes.spacingM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MornyColors.textPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MornyShapes.spacingS),
                ) {
                    Text(
                        game.titleId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MornyColors.textTertiary,
                    )
                    game.appVersion?.let {
                        Text(
                            "v$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MornyColors.textTertiary,
                        )
                    }
                    game.category?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MornyColors.textTertiary,
                        )
                    }
                }
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MornyColors.accentAmber,
            )
        }
    }
}
