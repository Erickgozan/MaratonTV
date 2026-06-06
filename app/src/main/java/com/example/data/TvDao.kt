package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TvDao {
    // --- Profiles ---
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<Profile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile): Long

    @Delete
    suspend fun deleteProfile(profile: Profile)

    // --- Playlists ---
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Int)

    // --- Channels ---
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<DbChannel>>

    @Query("SELECT * FROM channels WHERE groupTitle = :group")
    fun getChannelsByGroup(group: String): Flow<List<DbChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<DbChannel>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Int)

    @Query("DELETE FROM channels")
    suspend fun clearAllChannels()

    // --- Favorites ---
    @Query("SELECT * FROM favorites WHERE profileId = :profileId")
    fun getFavoritesForProfile(profileId: Int): Flow<List<Favorite>>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesDirect(): List<Favorite>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: Favorite)

    @Delete
    suspend fun removeFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun deleteFavoriteByKeys(profileId: Int, streamUrl: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    @Query("DELETE FROM profiles")
    suspend fun clearAllProfiles()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    // --- EPG / Programs ---
    @Query("SELECT * FROM epg_programs WHERE startTime >= :fromTime AND startTime <= :toTime")
    fun getProgramsInTimeRange(fromTime: Long, toTime: Long): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE channelName = :channelName ORDER BY startTime ASC")
    fun getProgramsForChannel(channelName: String): Flow<List<EpgProgram>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllPrograms()

    // --- Playback Progress ---
    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun getProgress(profileId: Int, streamUrl: String): PlaybackProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgress)

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND streamUrl = :streamUrl")
    suspend fun deleteProgress(profileId: Int, streamUrl: String)

    // --- Media Metadata Cache ---
    @Query("SELECT * FROM media_metadata_cache WHERE cleanName = :cleanName")
    suspend fun getCachedMetadata(cleanName: String): DbCachedMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMetadata(metadata: DbCachedMetadata)

    @Query("DELETE FROM media_metadata_cache WHERE cleanName = :cleanName")
    suspend fun deleteCachedMetadata(cleanName: String)

    @Query("DELETE FROM media_metadata_cache")
    suspend fun clearAllCachedMetadata()
}
