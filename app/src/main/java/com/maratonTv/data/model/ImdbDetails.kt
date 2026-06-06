package com.maratonTv.data.model

data class ImdbDetails(
    val synopsis: String,
    val rating: String,
    val year: String,
    val director: String,
    val actors: String,
    val trailerUrl: String,
    val sourceUsed: String, // "YOUTUBE" or "IMDb" or "TMDB"
    val posterUrl: String = "",
    val backdropUrl: String = ""
)
