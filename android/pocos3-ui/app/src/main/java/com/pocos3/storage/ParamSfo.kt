package com.pocos3.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pocos3.ui.home.GameModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

// =============================================================================
// PS3 PARAM.SFO parser.
//
// Reads a PS3 PARAM.SFO file (the format used for game metadata) and
// extracts the title (TITLE), title ID (TITLE_ID), and category (CATEGORY).
//
// Format reference: https://www.psdevwiki.com/ps3/PARAM.SFO
//
// Header structure:
//   0x00  magic       4 bytes  "\0PSF"
//   0x04  version     4 bytes  0x00000101
//   0x08  key_table   4 bytes  offset to key table
//   0x0C  data_table   4 bytes  offset to data table
//   0x10  entries     4 bytes  count of index entries
//
// Each index entry is 16 bytes:
//   0x00  key_offset    2 bytes  offset into key table
//   0x02  param_fmt      2 bytes  0x0204 = string, 0x0404 = int32
//   0x04  param_len      4 bytes  length of value (or 4 for ints)
//   0x08  param_max      4 bytes  max length
//   0x0C  data_offset    4 bytes  offset into data table
// =============================================================================

data class ParamSfo(
    val title: String?,
    val titleId: String?,
    val category: String?,
    val appVersion: String?,
    val attributes: Map<String, String>,
) {
    companion object {
        fun parse(data: ByteArray): ParamSfo? {
            if (data.size < 0x14) return null
            if (data[0] != 0.toByte() || data[1] != 'P'.code.toByte() ||
                data[2] != 'S'.code.toByte() || data[3] != 'F'.code.toByte()) {
                return null  // not a PSF file
            }
            val keyTableOffset = readU32(data, 0x08)
            val dataTableOffset = readU32(data, 0x0C)
            val entryCount = readU32(data, 0x10)

            val attrs = mutableMapOf<String, String>()
            for (i in 0 until entryCount.toInt().coerceAtMost(256)) {
                val entryOff = 0x14 + (i * 16)
                if (entryOff + 16 > data.size) break
                val keyOff = readU16(data, entryOff)
                val dataOff = readU32(data, entryOff + 0x0C)
                val paramLen = readU32(data, entryOff + 0x04).toInt()

                // Read key (null-terminated string at key_table + keyOff).
                val key = readCString(data, (keyTableOffset + keyOff).toInt())
                // Read value (string at data_table + dataOff).
                val valueStart = (dataTableOffset + dataOff).toInt()
                if (valueStart >= 0 && valueStart + paramLen <= data.size) {
                    val value = readCString(data, valueStart)
                    attrs[key] = value
                }
            }

            return ParamSfo(
                title = attrs["TITLE"],
                titleId = attrs["TITLE_ID"],
                category = attrs["CATEGORY"],
                appVersion = attrs["APP_VER"],
                attributes = attrs,
            )
        }

        private fun readU32(b: ByteArray, off: Int): Long {
            if (off + 4 > b.size) return 0
            return ((b[off].toLong() and 0xFF)) or
                   ((b[off + 1].toLong() and 0xFF) shl 8) or
                   ((b[off + 2].toLong() and 0xFF) shl 16) or
                   ((b[off + 3].toLong() and 0xFF) shl 24)
        }

        private fun readU16(b: ByteArray, off: Int): Int {
            if (off + 2 > b.size) return 0
            return ((b[off].toInt() and 0xFF)) or
                   ((b[off + 1].toInt() and 0xFF) shl 8)
        }

        private fun readCString(b: ByteArray, off: Int): String {
            if (off < 0 || off >= b.size) return ""
            val sb = StringBuilder()
            var i = off
            while (i < b.size && b[i] != 0.toByte()) {
                sb.append(b[i].toInt().toChar())
                i++
            }
            return sb.toString().trim()
        }
    }
}

// =============================================================================
// Game scanner: walks a SAF tree and identifies PS3 bootable titles.
//
// For each candidate directory (named after a title ID), reads
// PS3_GAME/PARAM.SFO (disc) or PARAM.SFO (PSN) and extracts the real
// title + app version. Falls back to the directory name if SFO is missing.
// =============================================================================

class GameScanner {
    suspend fun scan(context: Context, rootUri: Uri): List<GameModel> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val games = mutableListOf<GameModel>()
        val titleIdRegex = Regex(
            "^(BCES|BCUS|BLES|BLUS|BLJM|BCJM|BCJS|BLJS|BCKS|BCJK|BLJK|" +
            "NPUB|NPEB|NPEJ|NPJB|NPJC|NPHB|NPHJ|NPHD)[0-9A-F]{5}$"
        )
        for (child in root.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (!titleIdRegex.matches(name)) continue

            // Disc games have PS3_GAME/PARAM.SFO; PSN games have it at the root.
            val sfoFile = child.findFile("PS3_GAME")?.findFile("PARAM.SFO")
                ?: child.findFile("PARAM.SFO")
            val sfo = sfoFile?.let { sf ->
                runCatching {
                    context.contentResolver.openInputStream(sf.uri)?.use { input ->
                        input.readBytes()
                    }?.let { ParamSfo.parse(it) }
                }.getOrNull()
            }

            games += GameModel(
                path = child.uri.toString(),
                title = sfo?.title ?: name,
                titleId = sfo?.titleId ?: name,
                appVersion = sfo?.appVersion,
                category = sfo?.category,
            )
        }
        games.sortedBy { it.title.lowercase() }
    }
}
