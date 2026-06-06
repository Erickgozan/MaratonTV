package com.maratonTv.data.model

data class ChannelSource(
    val playlistName: String,
    val streamUrl: String
)

data class UiChannel(
    val name: String,
    val groupTitle: String,
    val logoUrl: String,
    val primaryStreamUrl: String,
    val sources: List<ChannelSource>,
    val currentProgram: String? = null,
    val currentProgramDescription: String? = null,
    val startEndText: String? = null,
    val programProgress: Float = 0f, // 0.0 to 1.0
    val isFavorite: Boolean = false,
    val rating: String = "8.2",
    val year: String = "2026",
    val director: String = "Blooders Creator",
    val actors: String = "Zazie Beetz, Patricia Arquette, Tom Clancy",
    val synopsis: String = "Un contenido de entretenimiento fluido optimizado para BloodersTV sin retrasos con transmisión en alta fidelidad y controles de mandos dedicados.",
    val originalGroup: String = "TV",
    val isEmbedText: Boolean = false,
    val adBlockerEnabled: Boolean = false
)
