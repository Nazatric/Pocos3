package com.pocos3.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pocos3.ui.home.GameModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =============================================================================
// Game scanner.
//
// Walks a SAF-picked tree URI and identifies PS3 bootable titles. PS3
// games on disc are a folder named after the title ID (BCUS98111 etc.)
// containing PS3_GAME/USRDIR/EBOOT.BIN. PSN games live in a similar
// structure under NPEB01234 / NPUB01234.
//
// The scanner does NOT parse PARAM.SFO here - it just finds candidate
// directories. The actual PARAM.SFO read happens in the core when the
// game is booted.
// =============================================================================

class GameScanner {
    suspend fun scan(context: Context, rootUri: Uri): List<GameModel> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val games = mutableListOf<GameModel>()
        // PS3 title IDs match a small set of patterns.
        val titleIdRegex = Regex("^(BCES|BCUS|BLES|BLUS|NPUB|NPEB|NPEJ|NPJB|BLES|BLJM)[0-9A-F]{5}$")
        for (child in root.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (!titleIdRegex.matches(name)) continue
            // Verify it has a PS3_GAME subdirectory (disc games) or an
            // EBOOT.BIN directly (PSN games).
            val hasGame = child.findFile("PS3_GAME")?.isDirectory == true ||
                          child.findFile("EBOOT.BIN")?.isFile == true
            if (!hasGame) continue
            games += GameModel(
                path = child.uri.toString(),
                title = name,  // Will be replaced by PARAM.SFO read at boot.
                titleId = name,
            )
        }
        games
    }
}
