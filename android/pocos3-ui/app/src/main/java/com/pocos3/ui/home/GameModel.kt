package com.pocos3.ui.home

// =============================================================================
// Game model with optional PARAM.SFO metadata.
// =============================================================================

data class GameModel(
    val path: String,
    val title: String,
    val titleId: String,
    val appVersion: String? = null,
    val category: String? = null,
    val lastPlayed: Long? = null,
) {
    val isDisc: Boolean get() = category == "DG" || category == "GD"
    val isHddGame: Boolean get() = category == "HG" || category == "HD"
    val isPsp: Boolean get() = category == "PE" || category == "PP"
    val isPs2: Boolean get() = category == "2G" || category == "2P"
}
