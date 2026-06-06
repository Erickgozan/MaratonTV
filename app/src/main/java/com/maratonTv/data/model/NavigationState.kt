package com.maratonTv.data.model

data class NavigationState(
    val screen: String,
    val selectedChannel: UiChannel?,
    val playingChannel: UiChannel?,
    val activeStreamUrl: String,
    val selectedSeason: Int,
    val selectedEpisodeNum: Int,
    val seriesEpisodes: List<UiChannel>,
    val isPlayerMaximized: Boolean
)
