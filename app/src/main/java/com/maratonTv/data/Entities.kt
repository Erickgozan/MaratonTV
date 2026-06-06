package com.maratonTv.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatarUrl: String,
    val pinCode: String? = null, // Profile password or PIN
    val customM3uUrl: String? = null,
    val customEpgUrl: String? = null
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val classification: String = "GENERAL",
    val playbackMode: String = "AUTOMATIC"
)

@Entity(tableName = "channels")
data class DbChannel(
    @PrimaryKey val streamUrl: String,
    val name: String,
    val groupTitle: String,
    val logoUrl: String,
    val playlistId: Int,
    val originalGroup: String = "",
    val isEmbedText: Boolean = false,
    val adBlockerEnabled: Boolean = false
)

@Entity(tableName = "favorites", primaryKeys = ["profileId", "streamUrl"])
data class Favorite(
    val profileId: Int,
    val streamUrl: String
)

@Entity(tableName = "epg_programs")
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelName: String,
    val title: String,
    val description: String,
    val startTime: Long, // Epoch millis
    val endTime: Long   // Epoch millis
)

@Entity(tableName = "playback_progress", primaryKeys = ["profileId", "streamUrl"])
data class PlaybackProgress(
    val profileId: Int,
    val streamUrl: String,
    val position: Long,
    val duration: Long,
    val lastAccessed: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_metadata_cache")
data class DbCachedMetadata(
    @PrimaryKey val cleanName: String,
    val synopsis: String,
    val rating: String,
    val year: String,
    val director: String,
    val actors: String,
    val trailerUrl: String,
    val sourceUsed: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
