package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

data class SeriesEpisodeInfo(
    val seriesName: String,
    val season: Int,
    val episode: Int
)

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

private val seriesRegex = "(?i)(S[0-9]+|T[0-9]+|E[0-9]+|TEMPORADA\\s*[0-9]+|CAPITULO\\s*[0-9]+|[0-9]+x[0-9]+|CAP\\.?\\s*[0-9]+|EP\\.?\\s*[0-9]+)".toRegex()
private val sxeRegex = "(?i)(?:S|T|TEM|TEMPORADA)\\s*([0-9]+)\\s*(?:-|\\*|E|EP|EPISODIO|C|CAP|CAPITULO)?\\s*(?:E|EP|EPISODIO|C|CAP|CAPITULO)?\\s*([0-9]+)".toRegex()
private val xRegex = "([0-9]+)x([0-9]+)".toRegex()
private val capEpRegex = "(?i)(?:CAP|EP|CAPITULO|EPISODIO)\\.?\\s*([0-9]+)".toRegex()
private val seasonOnlyRegex = "(?i)(?:TEMPORADA|TEM|SEASON)\\s*([0-9]+)".toRegex()
private val tempSeasonRegex = "(?i)(?:TEM|TEMP|SEASON)\\s*([0-9]+)".toRegex()
private val numMatchRegex = "([0-9]+)$".toRegex()
private val backTemRegex = "(?i)\\*back-tem".toRegex()
private val temRegex = "(?i)\\*tem".toRegex()
private val endCharsRegex = "[:\\-*\\s]+$".toRegex()

private val parseSeriesEpisodeCache = android.util.LruCache<String, SeriesEpisodeInfo>(2000)
private val NOT_A_SERIES = SeriesEpisodeInfo("", -1, -1)

fun parseSeriesEpisode(channel: UiChannel): SeriesEpisodeInfo? {
    val cacheKey = channel.name + "||" + channel.groupTitle
    val cached = parseSeriesEpisodeCache.get(cacheKey)
    if (cached != null) {
        return if (cached === NOT_A_SERIES) null else cached
    }
    
    val res = parseSeriesEpisodeUncached(channel)
    parseSeriesEpisodeCache.put(cacheKey, res ?: NOT_A_SERIES)
    return res
}

fun parseSeriesEpisodeUncached(channel: UiChannel): SeriesEpisodeInfo? {
    val name = channel.name.trim()
    val group = channel.groupTitle.uppercase()
    
    if (channel.originalGroup == "BLOODERSCRAP_EP") {
        val numMatch = numMatchRegex.find(name) ?: capEpRegex.find(name)
        val epNum = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val seriesNameValue = if (channel.director.isNotEmpty() && channel.director != "Blooderscrap" && channel.director != "Blooders Creator" && channel.director != "BloodersTv") {
            channel.director
        } else {
            "BloodersTv"
        }
        return SeriesEpisodeInfo(seriesNameValue, 1, epNum)
    }
    
    // Determine if it is a Series: if group is SERIES or name has series patterns
    val isSeriesCategory = group.contains("SERIE")
    val hasSeriesMarker = seriesRegex.containsMatchIn(name)
    
    if (!isSeriesCategory && !hasSeriesMarker) return null
    
    var season = 1
    var episode = 1
    var seriesName = name
    
    val mSxe = sxeRegex.find(name)
    val mX = xRegex.find(name)
    val mCapEp = capEpRegex.find(name)
    val mSeasonOnly = seasonOnlyRegex.find(name)
    
    if (mSxe != null) {
        season = mSxe.groupValues[1].toIntOrNull() ?: 1
        episode = mSxe.groupValues[2].toIntOrNull() ?: 1
        seriesName = name.substring(0, mSxe.range.first).trim()
    } else if (mX != null) {
        season = mX.groupValues[1].toIntOrNull() ?: 1
        episode = mX.groupValues[2].toIntOrNull() ?: 1
        seriesName = name.substring(0, mX.range.first).trim()
    } else if (mCapEp != null) {
        val seasonMatch = tempSeasonRegex.find(name)
        if (seasonMatch != null) {
            season = seasonMatch.groupValues[1].toIntOrNull() ?: 1
        }
        episode = mCapEp.groupValues[1].toIntOrNull() ?: 1
        seriesName = name.substring(0, mCapEp.range.first).trim()
    } else if (mSeasonOnly != null) {
        season = mSeasonOnly.groupValues[1].toIntOrNull() ?: 1
        val numMatch = numMatchRegex.find(name)
        if (numMatch != null) {
            episode = numMatch.groupValues[1].toIntOrNull() ?: 1
        }
        seriesName = name.substring(0, mSeasonOnly.range.first).trim()
    } else {
        val numMatch = numMatchRegex.find(name)
        if (numMatch != null) {
            episode = numMatch.groupValues[1].toIntOrNull() ?: 1
            seriesName = name.substring(0, numMatch.range.first).trim()
        } else {
            // No numerical markers found, but this is categorized under SERIES.
            // Treat as Season 1, Episode 1 of itself.
            season = 1
            episode = 1
            seriesName = name
        }
    }
    
    seriesName = seriesName
        .replace(backTemRegex, "")
        .replace(temRegex, "")
        .replace(endCharsRegex, "")
        .trim()
        
    if (seriesName.isEmpty()) {
        seriesName = name
    }
    
    return SeriesEpisodeInfo(seriesName, season, episode)
}

class TvViewModel(
    application: Application,
    private val repository: TvRepository
) : AndroidViewModel(application) {

    private val _navigationHistory = java.util.Stack<NavigationState>()

    fun pushToNavigationHistory() {
        val state = captureNavigationState()
        if (_navigationHistory.isNotEmpty()) {
            val top = _navigationHistory.peek()
            if (top.screen == state.screen &&
                top.selectedChannel?.primaryStreamUrl == state.selectedChannel?.primaryStreamUrl &&
                top.playingChannel?.primaryStreamUrl == state.playingChannel?.primaryStreamUrl &&
                top.isPlayerMaximized == state.isPlayerMaximized
            ) {
                return
            }
        }
        _navigationHistory.push(state)
    }

    private fun captureNavigationState(): NavigationState {
        return NavigationState(
            screen = _currentScreen.value,
            selectedChannel = _selectedChannel.value,
            playingChannel = _playingChannel.value,
            activeStreamUrl = _activeStreamUrl.value,
            selectedSeason = _selectedSeason.value,
            selectedEpisodeNum = _selectedEpisodeNum.value,
            seriesEpisodes = _seriesEpisodes.value,
            isPlayerMaximized = _isPlayerMaximized.value
        )
    }

    fun goBack(): Boolean {
        if (_navigationHistory.isNotEmpty()) {
            val prevState = _navigationHistory.pop()
            
            // If we are exiting PLAYER, call stopPlayback() if target doesn't have playback
            if (_currentScreen.value == "PLAYER" && prevState.screen != "PLAYER" && prevState.activeStreamUrl.isEmpty()) {
                stopPlayback()
            }

            _currentScreen.value = prevState.screen
            _selectedChannel.value = prevState.selectedChannel
            _playingChannel.value = prevState.playingChannel
            _activeStreamUrl.value = prevState.activeStreamUrl
            _selectedSeason.value = prevState.selectedSeason
            _selectedEpisodeNum.value = prevState.selectedEpisodeNum
            _seriesEpisodes.value = prevState.seriesEpisodes
            _isPlayerMaximized.value = prevState.isPlayerMaximized
            return true
        }
        return false
    }

    private var pendingTvChannelUrlToRestore: String? = null

    // Profiles List
    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Profile State
    private val _currentProfile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _currentProfile.asStateFlow()

    // Playlists List
    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Navigation Category (Default: TV)
    private val _activeCategory = MutableStateFlow("TV")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    // Global Search Query (can be typed or filled via voice search)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Preferences and external VLC player option
    private val prefs = application.getSharedPreferences("blooders_tv_prefs", android.content.Context.MODE_PRIVATE)

    // Active Channel List for current active profile
    val allChannels: StateFlow<List<UiChannel>> = _currentProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                repository.getUiChannels(profile.id).map { dbList ->
                    dbList + StaticTvChannels.channels
                }
            } else {
                flowOf(StaticTvChannels.channels)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Watch history tracking list block
    private val _watchHistory = MutableStateFlow<List<UiChannel>>(emptyList())
    val watchHistory: StateFlow<List<UiChannel>> = _watchHistory.asStateFlow()

    // --- Chrome Extension Companion Scraper ("Blooderscrap") Server States ---
    private val _extensionStatus = MutableStateFlow("Escuchando (Puerto 9999)")
    val extensionStatus: StateFlow<String> = _extensionStatus.asStateFlow()

    private val _scrapedItems = MutableStateFlow<List<UiChannel>>(emptyList())
    val scrapedItems: StateFlow<List<UiChannel>> = _scrapedItems.asStateFlow()

    private val _scrapedTitle = MutableStateFlow("BloodersTv Autónomo")
    val scrapedTitle: StateFlow<String> = _scrapedTitle.asStateFlow()

    private val _scrapedType = MutableStateFlow("")
    val scrapedType: StateFlow<String> = _scrapedType.asStateFlow()

    private val _lastHeartbeat = MutableStateFlow<Long>(0L)
    val lastHeartbeat: StateFlow<Long> = _lastHeartbeat.asStateFlow()

    // --- Autonomous Scrapper Integration ---
    private val _blooderscrapIntegrado = MutableStateFlow(prefs.getBoolean("blooderscrap_integrado", true))
    val blooderscrapIntegrado: StateFlow<Boolean> = _blooderscrapIntegrado.asStateFlow()

    private val _scrapedCatalogItems = MutableStateFlow<List<UiChannel>>(emptyList())
    val scrapedCatalogItems: StateFlow<List<UiChannel>> = _scrapedCatalogItems.asStateFlow()

    // --- Chrome Extensions variables ---
    private val _chromeExtensions = MutableStateFlow<List<ChromeExtension>>(emptyList())
    val chromeExtensions: StateFlow<List<ChromeExtension>> = _chromeExtensions.asStateFlow()

    // Filtered Channels for the UI based on Active Category AND Search Query
    val filteredChannels: StateFlow<List<UiChannel>> = combine(
        allChannels,
        _activeCategory,
        _searchQuery,
        _watchHistory,
        _scrapedItems,
        _scrapedCatalogItems
    ) { flowArray ->
        val channels = flowArray[0] as List<UiChannel>
        val category = flowArray[1] as String
        val query = flowArray[2] as String
        val historyChannels = flowArray[3] as List<UiChannel>
        val extensionItems = flowArray[4] as List<UiChannel>
        val localScrapedCatalog = flowArray[5] as List<UiChannel>

        val filtered = if (query.isNotEmpty()) {
            val dbMatches = channels.filter { 
                it.name.contains(query, ignoreCase = true) ||
                it.groupTitle.contains(query, ignoreCase = true) ||
                (it.currentProgram ?: "").contains(query, ignoreCase = true)
            }
            val extMatches = extensionItems.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.groupTitle.contains(query, ignoreCase = true)
            }
            dbMatches + extMatches + localScrapedCatalog
        } else if (category == "EXTENSION") {
            extensionItems + localScrapedCatalog
        } else if (category == "HISTORIAL") {
            historyChannels
        } else if (category == "FAVORITOS") {
            channels.filter { it.isFavorite }
        } else if (category == "SERIES") {
            val localSeries = channels.filter { it.groupTitle == "SERIES" }
            localSeries + localScrapedCatalog
        } else if (category == "PELICULA") {
            val localMovies = channels.filter { it.groupTitle == "PELICULA" }
            localMovies + localScrapedCatalog
        } else if (category == "KIDS") {
            val localKids = channels.filter { it.groupTitle == "KIDS" }
            localKids + localScrapedCatalog
        } else if (category == "ANIME") {
            val localAnime = channels.filter { it.groupTitle == "ANIME" }
            localAnime + localScrapedCatalog
        } else {
            channels.filter { it.groupTitle == category }
        }

        if (query.isNotEmpty() || category.equals("SERIES", ignoreCase = true) || category.equals("EXTENSION", ignoreCase = true)) {
            // High-performance series & movie classification partition.
            // Bypasses heavy regex and grouping for standard live channels and movies, making lists load instantly!
            val seriesChannels = ArrayList<UiChannel>()
            val nonSeriesChannels = ArrayList<UiChannel>()
            
            for (i in 0 until filtered.size) {
                val ch = filtered[i]
                val isSeriesCategory = ch.groupTitle == "SERIES" || ch.groupTitle == "EXTENSION"
                val isSeries = isSeriesCategory || parseSeriesEpisode(ch) != null
                if (isSeries) {
                    seriesChannels.add(ch)
                } else {
                    nonSeriesChannels.add(ch)
                }
            }

            val groupedSeries = if (seriesChannels.isNotEmpty()) {
                val groupedSeriesMap = java.util.LinkedHashMap<String, ArrayList<UiChannel>>()
                for (i in 0 until seriesChannels.size) {
                    val ch = seriesChannels[i]
                    val epInfo = parseSeriesEpisode(ch)
                    val seriesName = epInfo?.seriesName ?: ch.name
                    var list = groupedSeriesMap[seriesName]
                    if (list == null) {
                        list = ArrayList()
                        groupedSeriesMap[seriesName] = list
                    }
                    list.add(ch)
                }
                
                val resultList = ArrayList<UiChannel>(groupedSeriesMap.size)
                for ((seriesName, eps) in groupedSeriesMap) {
                    var bestRep = eps[0]
                    var bestScore = let {
                        val info = parseSeriesEpisode(bestRep)
                        if (info != null) (info.season * 1000 + info.episode) else 0
                    }
                    for (j in 1 until eps.size) {
                        val ch = eps[j]
                        val info = parseSeriesEpisode(ch)
                        val score = if (info != null) (info.season * 1000 + info.episode) else 0
                        if (score < bestScore) {
                            bestRep = ch
                            bestScore = score
                        }
                    }
                    resultList.add(bestRep.copy(name = seriesName))
                }
                resultList
            } else {
                emptyList()
            }

            groupedSeries + nonSeriesChannels
        } else {
            filtered
        }
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected state for Movie/Show Details page
    private val _selectedChannel = MutableStateFlow<UiChannel?>(null)
    val selectedChannel: StateFlow<UiChannel?> = _selectedChannel.asStateFlow()

    // Dynamic IMDb Details state
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

    private val _tmdbApiKey = MutableStateFlow(
        prefs.getString("tmdb_api_key", "ba8a6b2302e1b1062b1bdfac4f7396a8") ?: "ba8a6b2302e1b1062b1bdfac4f7396a8"
    )
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    fun setTmdbApiKey(key: String) {
        _tmdbApiKey.value = key
        prefs.edit().putString("tmdb_api_key", key).apply()
    }

    private val _imdbDetailsState = MutableStateFlow<ImdbDetails?>(null)
    val imdbDetailsState: StateFlow<ImdbDetails?> = _imdbDetailsState.asStateFlow()

    private val _isLoadingImdb = MutableStateFlow(false)
    val isLoadingImdb: StateFlow<Boolean> = _isLoadingImdb.asStateFlow()

    private val okHttpClient = repository.okHttpClient
    private val imdbCache = android.util.LruCache<String, ImdbDetails>(500)

    private fun htmlDecode(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\u0022", "\"")
            .replace("\\u0027", "'")
    }

    private fun cleanMovieNameForSearch(name: String): String {
        var title = name.trim()
        // Remove leading numbers or prefix channel numbers
        title = title.replace("^\\d+\\s+".toRegex(), "")
        title = title.replace("^🔞\\s+".toRegex(), "")
        // Remove resolutions, formats and tags
        val tags = listOf(
            "1080P", "720P", "2160P", "4K", "HD", "FHD", "UHD", "BLURAY", "BDRIP", "DVDRIP", "HDRIP", "CAMRIP",
            "H264", "H265", "HEVC", "X264", "X265", "LATINO", "CASTELLANO", "SUB", "MULTILANG", "DUAL", "SPA", "ENG"
        )
        for (tag in tags) {
            title = title.replace("(?i)\\b$tag\\b".toRegex(), "")
        }
        // Remove season markers
        title = title.replace("(?i)\\b(?:S[0-9]+|T[0-9]+|E[0-9]+|TEMPORADA\\s*[0-9]+|CAPITULO\\s*[0-9]+|[0-9]+x[0-9]+|CAP\\.?\\s*[0-9]+|EP\\.?\\s*[0-9]+).*$".toRegex(), "")
        // Remove years in parenthesis or bracket
        title = title.replace("\\([12][0-9]{3}\\)".toRegex(), "")
        title = title.replace("\\[[12][0-9]{3}\\]".toRegex(), "")
        title = title.replace("\\b[12][0-9]{3}\\b".toRegex(), "")
        // Remove trailing dashes, spaces, etc.
        title = title.replace("[:\\-*\\s]+$".toRegex(), "").trim()
        return title
    }

    // Series episodes list and selection states
    private val _seriesEpisodes = MutableStateFlow<List<UiChannel>>(emptyList())
    val seriesEpisodes: StateFlow<List<UiChannel>> = _seriesEpisodes.asStateFlow()

    private val _selectedSeason = MutableStateFlow<Int>(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _selectedEpisodeNum = MutableStateFlow<Int>(1)
    val selectedEpisodeNum: StateFlow<Int> = _selectedEpisodeNum.asStateFlow()

    // Currently playing channel stream
    private val _playingChannel = MutableStateFlow<UiChannel?>(null)
    val playingChannel: StateFlow<UiChannel?> = _playingChannel.asStateFlow()

    // The exactly active stream URL chosen (supports source switching)
    private val _activeStreamUrl = MutableStateFlow("")
    val activeStreamUrl: StateFlow<String> = _activeStreamUrl.asStateFlow()

    // Cloud Sync Configuration State
    private val _cloudSyncEmail = MutableStateFlow(prefs.getString("sync_email", "") ?: "")
    val cloudSyncEmail: StateFlow<String> = _cloudSyncEmail.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow("Sincronizado") // "Sincronizado", "Pendiente", "Sincronizando..."
    val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

    private val _useVlcPlayer = MutableStateFlow(prefs.getBoolean("use_vlc_player", false))
    val useVlcPlayer: StateFlow<Boolean> = _useVlcPlayer.asStateFlow()

    private val _libVlcUtilityInstalled = MutableStateFlow(prefs.getBoolean("libvlc_utility_installed", false))
    val libVlcUtilityInstalled: StateFlow<Boolean> = _libVlcUtilityInstalled.asStateFlow()

    private val _codecsPackInstalled = MutableStateFlow(prefs.getBoolean("codecs_pack_installed", false))
    val codecsPackInstalled: StateFlow<Boolean> = _codecsPackInstalled.asStateFlow()

    private val _playbackMessage = MutableStateFlow<String?>(null)
    val playbackMessage: StateFlow<String?> = _playbackMessage.asStateFlow()

    private val _isVlcAudioActive = MutableStateFlow(false)
    val isVlcAudioActive: StateFlow<Boolean> = _isVlcAudioActive.asStateFlow()

    // --- New States for configured video format, player, subtitles, and trailer ---
    private val _videoFormat = MutableStateFlow(prefs.getString("video_format", "forzar 16:9") ?: "forzar 16:9")
    val videoFormat: StateFlow<String> = _videoFormat.asStateFlow()

    private val _videoPlayerEngine = MutableStateFlow(prefs.getString("video_player_engine", "Reproductor 2") ?: "Reproductor 2")
    val videoPlayerEngine: StateFlow<String> = _videoPlayerEngine.asStateFlow()

    private val _videoLanguage = MutableStateFlow(prefs.getString("video_language", "Español Latino") ?: "Español Latino")
    val videoLanguage: StateFlow<String> = _videoLanguage.asStateFlow()

    private val _videoSubtitles = MutableStateFlow(prefs.getString("video_subtitles", "Desactivados") ?: "Desactivados")
    val videoSubtitles: StateFlow<String> = _videoSubtitles.asStateFlow()

    private val _audioFixIgnoreErrors = MutableStateFlow(prefs.getBoolean("audio_fix_ignore_errors", false))
    val audioFixIgnoreErrors: StateFlow<Boolean> = _audioFixIgnoreErrors.asStateFlow()

    private val _audioFixChannels = MutableStateFlow(prefs.getString("audio_fix_channels", "Stereo") ?: "Stereo")
    val audioFixChannels: StateFlow<String> = _audioFixChannels.asStateFlow()

    private val _disableTrailers = MutableStateFlow(prefs.getBoolean("disable_trailers", false))
    val disableTrailers: StateFlow<Boolean> = _disableTrailers.asStateFlow()

    private val _activeTrailerUrl = MutableStateFlow<String?>(null)
    val activeTrailerUrl: StateFlow<String?> = _activeTrailerUrl.asStateFlow()

    private val _isImdbTrailerActive = MutableStateFlow(false)
    val isImdbTrailerActive: StateFlow<Boolean> = _isImdbTrailerActive.asStateFlow()

    // Origen de Tráileres: "SMARTUBE" o "IMDb"
    private val _trailerSource = MutableStateFlow(
        prefs.getString("trailer_source", "SMARTUBE")?.let {
            if (it == "YOUTUBE") "SMARTUBE" else it
        } ?: "SMARTUBE"
    )
    val trailerSource: StateFlow<String> = _trailerSource.asStateFlow()

    fun setTrailerSource(source: String) {
        _trailerSource.value = source
        prefs.edit().putString("trailer_source", source).apply()
    }

    private val _isPlayerMaximized = MutableStateFlow(false)
    val isPlayerMaximized: StateFlow<Boolean> = _isPlayerMaximized.asStateFlow()

    fun setPlayerMaximized(maximized: Boolean) {
        _isPlayerMaximized.value = maximized
    }

    private suspend fun persistImdbDetails(cleanName: String, details: ImdbDetails) {
        try {
            repository.saveCachedMetadata(
                com.example.data.DbCachedMetadata(
                    cleanName = cleanName,
                    synopsis = details.synopsis,
                    rating = details.rating,
                    year = details.year,
                    director = details.director,
                    actors = details.actors,
                    trailerUrl = details.trailerUrl,
                    sourceUsed = details.sourceUsed,
                    posterUrl = details.posterUrl,
                    backdropUrl = details.backdropUrl
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchImdbAndTrailerDetails(channel: UiChannel) {
        val cleanName = cleanMovieNameForSearch(channel.name)
        val source = _trailerSource.value

        val cached = imdbCache.get(cleanName)
        if (cached != null) {
            _imdbDetailsState.value = cached
            _isLoadingImdb.value = false
            return
        }

        _isLoadingImdb.value = true
        _imdbDetailsState.value = null

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // First check persistent Room SQLite cache
            try {
                val dbCached = repository.getCachedMetadata(cleanName)
                if (dbCached != null) {
                    val details = ImdbDetails(
                        synopsis = dbCached.synopsis,
                        rating = dbCached.rating,
                        year = dbCached.year,
                        director = dbCached.director,
                        actors = dbCached.actors,
                        trailerUrl = dbCached.trailerUrl,
                        sourceUsed = dbCached.sourceUsed,
                        posterUrl = dbCached.posterUrl,
                        backdropUrl = dbCached.backdropUrl
                    )
                    imdbCache.put(cleanName, details)
                    _imdbDetailsState.value = details
                    _isLoadingImdb.value = false
                    return@launch
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                if (source == "TMDB") {
                    val apiKey = _tmdbApiKey.value
                    if (apiKey.trim().isNotEmpty()) {
                        val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=${android.net.Uri.encode(cleanName)}&language=es-ES"
                        val request = Request.Builder()
                            .url(searchUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val jsonStr = response.body?.string() ?: ""
                            val jsonObj = org.json.JSONObject(jsonStr)
                            val results = jsonObj.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                var bestMatch: org.json.JSONObject? = null
                                for (i in 0 until results.length()) {
                                    val item = results.getJSONObject(i)
                                    val mediaType = item.optString("media_type")
                                    if (mediaType == "movie" || mediaType == "tv") {
                                        bestMatch = item
                                        break
                                    }
                                }
                                if (bestMatch == null) {
                                    bestMatch = results.getJSONObject(0)
                                }

                                val mediaType = bestMatch.optString("media_type", "movie")
                                val id = bestMatch.optInt("id")
                                val overview = bestMatch.optString("overview")
                                val ratingVal = bestMatch.optDouble("vote_average", 0.0)
                                val voteStr = if (ratingVal > 0.0) String.format(java.util.Locale.US, "%.1f", ratingVal) else channel.rating

                                val releaseDate = if (mediaType == "tv") bestMatch.optString("first_air_date") else bestMatch.optString("release_date")
                                val yearStr = if (releaseDate != null && releaseDate.length >= 4) releaseDate.substring(0, 4) else channel.year

                                val posterPath = bestMatch.optString("poster_path")
                                val backdropPath = bestMatch.optString("backdrop_path")
                                val posterUrl = if (posterPath.isNotEmpty() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else ""
                                val backdropUrl = if (backdropPath.isNotEmpty() && backdropPath != "null") "https://image.tmdb.org/t/p/w780$backdropPath" else ""

                                var director = channel.director
                                var actors = channel.actors

                                // Credits (Director & Cast)
                                try {
                                    val creditsUrl = "https://api.themoviedb.org/3/$mediaType/$id/credits?api_key=$apiKey&language=es-ES"
                                    val creditsRequest = Request.Builder().url(creditsUrl).header("User-Agent", "Mozilla/5.0").build()
                                    val creditsResponse = okHttpClient.newCall(creditsRequest).execute()
                                    if (creditsResponse.isSuccessful) {
                                        val creditsJson = org.json.JSONObject(creditsResponse.body?.string() ?: "")
                                        val crew = creditsJson.optJSONArray("crew")
                                        if (crew != null) {
                                            val dirList = mutableListOf<String>()
                                            for (j in 0 until crew.length()) {
                                                val m = crew.getJSONObject(j)
                                                if (m.optString("job").equals("Director", ignoreCase = true)) {
                                                    dirList.add(m.optString("name"))
                                                }
                                            }
                                            if (dirList.isNotEmpty()) {
                                                director = dirList.joinToString(", ")
                                            }
                                        }
                                        val cast = creditsJson.optJSONArray("cast")
                                        if (cast != null) {
                                            val actList = mutableListOf<String>()
                                            val limit = minOf(cast.length(), 4)
                                            for (j in 0 until limit) {
                                                actList.add(cast.getJSONObject(j).optString("name"))
                                            }
                                            if (actList.isNotEmpty()) {
                                                actors = actList.joinToString(", ")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                // Videos for trailer
                                var trailerUrl = ""
                                try {
                                    val videosUrl = "https://api.themoviedb.org/3/$mediaType/$id/videos?api_key=$apiKey&language=es-ES"
                                    val videosRequest = Request.Builder().url(videosUrl).header("User-Agent", "Mozilla/5.0").build()
                                    val videosResponse = okHttpClient.newCall(videosRequest).execute()
                                    if (videosResponse.isSuccessful) {
                                        val videosJson = org.json.JSONObject(videosResponse.body?.string() ?: "")
                                        val videosArray = videosJson.optJSONArray("results")
                                        if (videosArray != null) {
                                            for (j in 0 until videosArray.length()) {
                                                val video = videosArray.getJSONObject(j)
                                                val site = video.optString("site")
                                                val key = video.optString("key")
                                                val type = video.optString("type")
                                                if (site.equals("YouTube", ignoreCase = true)) {
                                                    if (type.equals("Trailer", ignoreCase = true)) {
                                                        trailerUrl = key
                                                        break
                                                    } else if (trailerUrl.isEmpty()) {
                                                        trailerUrl = key
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                val details = ImdbDetails(
                                    synopsis = if (overview.isNotEmpty()) overview else channel.synopsis,
                                    rating = voteStr,
                                    year = yearStr,
                                    director = director,
                                    actors = actors,
                                    trailerUrl = trailerUrl,
                                    sourceUsed = "TMDB",
                                    posterUrl = posterUrl,
                                    backdropUrl = backdropUrl
                                )
                                imdbCache.put(cleanName, details)
                                _imdbDetailsState.value = details
                                persistImdbDetails(cleanName, details)
                                return@launch
                            }
                        }
                    }
                }

                // Fallback to IMDb or if selected source is "IMDb" (or TMDB query yielded nothing)
                val searchUrl = "https://www.imdb.com/find/?q=${android.net.Uri.encode(cleanName)}&s=tt"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .build()

                var rating = channel.rating
                var synopsis = channel.synopsis
                var director = channel.director
                var actors = channel.actors
                var year = channel.year
                var trailerUrl = ""

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    
                    // Slice HTML to find inside actual results container and avoid global navbar promos!
                    val itemIndex = html.indexOf("ipc-metadata-list-summary-item")
                    val subHtml = if (itemIndex != -1) {
                        html.substring(itemIndex)
                    } else {
                        val frIndex = html.indexOf("findResult")
                        if (frIndex != -1) html.substring(frIndex) else html
                    }

                    val ttMatch = "/title/(tt\\d+)".toRegex().find(subHtml) ?: "/title/(tt\\d+)".toRegex().find(html)
                    if (ttMatch != null) {
                        val ttId = ttMatch.groupValues[1]
                        val titleUrl = "https://www.imdb.com/title/$ttId/"
                        val titleRequest = Request.Builder()
                            .url(titleUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .header("Accept-Language", "es-ES,es;q=0.9")
                            .build()

                        val titleResponse = okHttpClient.newCall(titleRequest).execute()
                        if (titleResponse.isSuccessful) {
                            val titleHtml = titleResponse.body?.string() ?: ""
                            
                            val ratingMatch = "\"ratingValue\":\\s*\"?([0-9.]+)\"?".toRegex().find(titleHtml)
                                ?: "\"aggregateRating\":\\{[^\\}]*\"ratingValue\":\\s*\"?([0-9.]+)\"?".toRegex().find(titleHtml)
                            if (ratingMatch != null) {
                                rating = ratingMatch.groupValues[1]
                            }

                            val metaDescMatch = "<meta[^>]*property=\"og:description\"[^>]*content=\"([^\"]+)\"".toRegex().find(titleHtml)
                                ?: "<meta[^>]*name=\"description\"[^>]*content=\"([^\"]+)\"".toRegex().find(titleHtml)
                            if (metaDescMatch != null) {
                                val desc = htmlDecode(metaDescMatch.groupValues[1])
                                if (desc.isNotEmpty() && !desc.contains("IMDb", ignoreCase = true)) {
                                    synopsis = desc
                                }
                            }

                            val yearMatch = "\"releaseDate\":\\s*\"?([12][0-9]{3})".toRegex().find(titleHtml)
                                ?: "<title>[^<]*\\(([12][0-9]{3})\\)".toRegex().find(titleHtml)
                            if (yearMatch != null) {
                                year = yearMatch.groupValues[1]
                            }

                            val directorMatch = "\"director\":\\[?\\s*\\{[^\\}]*\"name\":\\s*\"([^\"]+)\"".toRegex().find(titleHtml)
                            if (directorMatch != null) {
                                director = htmlDecode(directorMatch.groupValues[1])
                            }
                        }
                    }
                }
                
                // Smartube / Fallback mp4 trailers
                val defaultTrailers = mapOf(
                    "TE VAN A MATAR" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    "THE PUNISHER" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "JACK RYAN" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "THE BOYS" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
                )
                
                val lowerClean = cleanName.uppercase()
                var matchedTrailer = ""
                for ((key, tUrl) in defaultTrailers) {
                    if (lowerClean.contains(key)) {
                        matchedTrailer = tUrl
                        break
                    }
                }
                if (matchedTrailer.isEmpty()) {
                    matchedTrailer = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                }
                
                trailerUrl = matchedTrailer

                val details = ImdbDetails(
                    synopsis = synopsis,
                    rating = rating,
                    year = year,
                    director = director,
                    actors = actors,
                    trailerUrl = trailerUrl,
                    sourceUsed = if (source == "TMDB") "TMDB" else "IMDb"
                )
                imdbCache.put(cleanName, details)
                _imdbDetailsState.value = details
                persistImdbDetails(cleanName, details)
            } catch (e: Exception) {
                e.printStackTrace()
                val fallbackDetails = ImdbDetails(
                    synopsis = channel.synopsis,
                    rating = channel.rating,
                    year = channel.year,
                    director = channel.director,
                    actors = channel.actors,
                    trailerUrl = if (cleanName.uppercase().contains("TE VAN A MATAR")) {
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                    } else "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    sourceUsed = source
                )
                imdbCache.put(cleanName, fallbackDetails)
                _imdbDetailsState.value = fallbackDetails
                persistImdbDetails(cleanName, fallbackDetails)
            } finally {
                _isLoadingImdb.value = false
            }
        }
    }

    fun setVideoFormat(format: String) {
        _videoFormat.value = format
        prefs.edit().putString("video_format", format).apply()
    }

    fun setVideoPlayerEngine(engine: String) {
        _videoPlayerEngine.value = engine
        prefs.edit().putString("video_player_engine", engine).apply()
    }

    fun setVideoLanguage(language: String) {
        _videoLanguage.value = language
        prefs.edit().putString("video_language", language).apply()
    }

    fun setVideoSubtitles(subtitles: String) {
        _videoSubtitles.value = subtitles
        prefs.edit().putString("video_subtitles", subtitles).apply()
    }

    fun setAudioFixIgnoreErrors(enabled: Boolean) {
        _audioFixIgnoreErrors.value = enabled
        prefs.edit().putBoolean("audio_fix_ignore_errors", enabled).apply()
    }

    fun setAudioFixChannels(channels: String) {
        _audioFixChannels.value = channels
        prefs.edit().putString("audio_fix_channels", channels).apply()
    }

    fun toggleDisableTrailers(disabled: Boolean) {
        _disableTrailers.value = disabled
        prefs.edit().putBoolean("disable_trailers", disabled).apply()
    }

    fun showTrailer(youtubeId: String) {
        if (_disableTrailers.value) {
            return
        }
        _isImdbTrailerActive.value = false
        _activeTrailerUrl.value = youtubeId
    }

    fun showTrailerForChannel(channel: UiChannel) {
        if (_disableTrailers.value) {
            return
        }
        val source = _trailerSource.value
        if (source == "IMDb") {
            _isImdbTrailerActive.value = true
            val currentDetails = _imdbDetailsState.value
            val trailerUrl = if (currentDetails != null && channel.name == _selectedChannel.value?.name) {
                currentDetails.trailerUrl
            } else {
                val cleanName = cleanMovieNameForSearch(channel.name).uppercase()
                when {
                    cleanName.contains("MATAR") -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                    cleanName.contains("PUNISHER") -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                    cleanName.contains("JACK RYAN") -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
                    cleanName.contains("THE BOYS") -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
                    else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                }
            }
            _activeTrailerUrl.value = trailerUrl
        } else {
            _isImdbTrailerActive.value = false
            
            val cleanName = cleanMovieNameForSearch(channel.name)
            val searchQuery = "$cleanName trailer"
            
            // Try TMDB cached real youtube key if available
            val currentDetails = _imdbDetailsState.value
            var youtubeId = if (currentDetails != null && channel.name == _selectedChannel.value?.name && 
                currentDetails.trailerUrl.isNotEmpty() && !currentDetails.trailerUrl.startsWith("http")) {
                currentDetails.trailerUrl
            } else {
                ""
            }
            
            val context = getApplication<Application>().applicationContext
            
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (youtubeId.isEmpty()) {
                    val fetchedId = fetchFirstYoutubeVideoId(searchQuery)
                    if (fetchedId != null) {
                        youtubeId = fetchedId
                    }
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    var launched = false
                    if (youtubeId.isNotEmpty() && youtubeId.length < 20) {
                        launched = launchSmartTubeIntent(context, youtubeId)
                    }
                    
                    // If we didn't have a video key or deep playing failed, run standard search intent
                    if (!launched) {
                        launched = launchSmartTubeSearchIntent(context, searchQuery)
                    }
                    
                    // If completely failed of external players, fallback to local Player
                    if (!launched) {
                        val fallbackId = if (youtubeId.isNotEmpty()) youtubeId else getYoutubeTvTrailerId(channel.name)
                        _activeTrailerUrl.value = fallbackId
                    }
                }
            }
        }
    }

    private fun fetchFirstYoutubeVideoId(searchQuery: String): String? {
        try {
            val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgIQAQ%253D%253D"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                val videoIdRegex = """\"videoId\"\s*:\s*\"([a-zA-Z0-9_-]{11})\"""".toRegex()
                val match = videoIdRegex.find(html)
                if (match != null) {
                    val id = match.groupValues[1]
                    if (id.isNotEmpty() && id != "dQw4w9WgXcQ") {
                        return id
                    }
                }
                
                val watchRegex = """/watch\?v=([a-zA-Z0-9_-]{11})""".toRegex()
                val watchMatch = watchRegex.find(html)
                if (watchMatch != null) {
                    val id = watchMatch.groupValues[1]
                    if (id.isNotEmpty() && id != "dQw4w9WgXcQ") {
                        return id
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TvViewModel", "Error fetching first youtube video: ${e.message}", e)
        }
        return null
    }

    private fun getYoutubeTvTrailerId(channelName: String): String {
        return when {
             channelName.contains("matar", ignoreCase = true) -> "BfK6GAbh3v8"
             channelName.contains("Punisher", ignoreCase = true) -> "L6P3O1DdqAc"
             channelName.contains("Jack Ryan", ignoreCase = true) -> "1K_C_76X-k0"
             channelName.contains("The Boys", ignoreCase = true) -> "M1bhOaLv4i4"
             channelName.contains("From", ignoreCase = true) -> "c947d-fQ8K0"
             channelName.contains("Cartoon", ignoreCase = true) || channelName.contains("Kids", ignoreCase = true) -> "r9H_S-b2J8s"
             channelName.contains("Anime", ignoreCase = true) -> "KUb9NCSv4hA"
             channelName.contains("Historia", ignoreCase = true) || channelName.contains("Explora", ignoreCase = true) -> "BfK6GAbh3v8"
             else -> "dQw4w9WgXcQ"
        }
    }

    private fun launchSmartTubeIntent(context: android.content.Context, videoId: String): Boolean {
        val pm = context.packageManager
        val smartTubePackages = listOf(
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videolauncher"
        )
        for (pkg in smartTubePackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                val uri = android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Not found or error launching
            }
        }
        try {
            val uri = android.net.Uri.parse("smarttube://play?video_id=$videoId")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Smarttube deep link registration failed
        }
        return false
    }

    private fun launchSmartTubeSearchIntent(context: android.content.Context, searchQuery: String): Boolean {
        val pm = context.packageManager
        val smartTubePackages = listOf(
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videolink",
            "com.teamsmart.videolauncher",
            "com.teamsmart.videomanager.tv"
        )
        
        for (pkg in smartTubePackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                
                // Intent 1: Web results URL targeting package (extremely secure trigger for TV search interception)
                val uri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(searchQuery))
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Ignore and try next
            }
            
            try {
                // Intent 2: Standard ACTION_SEARCH targeting package
                val intent = android.content.Intent(android.content.Intent.ACTION_SEARCH).apply {
                    setPackage(pkg)
                    putExtra("query", searchQuery)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Ignore and try next
            }
        }
        
        // Custom URL scheme play/search triggers
        try {
            val uri = android.net.Uri.parse("smarttube://search?q=" + android.net.Uri.encode(searchQuery))
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Ignore
        }

        try {
            val uri = android.net.Uri.parse("smarttube://search/" + android.net.Uri.encode(searchQuery))
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Ignore
        }

        // Global fallback URI
        try {
            val uri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(searchQuery))
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            // Ignore
        }
        
        return false
    }

    fun closeTrailer() {
        _activeTrailerUrl.value = null
    }

    fun toggleVlcPlayer(enabled: Boolean) {
        _useVlcPlayer.value = enabled
        prefs.edit().putBoolean("use_vlc_player", enabled).apply()
    }

    fun setLibVlcUtilityInstalled(installed: Boolean) {
        _libVlcUtilityInstalled.value = installed
        prefs.edit().putBoolean("libvlc_utility_installed", installed).apply()
    }

    fun setCodecsPackInstalled(installed: Boolean) {
        _codecsPackInstalled.value = installed
        prefs.edit().putBoolean("codecs_pack_installed", installed).apply()
    }

    fun clearPlaybackMessage() {
        _playbackMessage.value = null
    }

    fun updateSyncEmail(email: String) {
        _cloudSyncEmail.value = email
        prefs.edit().putString("sync_email", email).apply()
    }

    fun exportBackup(onResult: (Boolean, String, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonString = repository.exportBackupString()
                val rootJson = org.json.JSONObject(jsonString)
                val settingsJson = org.json.JSONObject()
                settingsJson.put("use_vlc_player", _useVlcPlayer.value)
                settingsJson.put("libvlc_utility_installed", _libVlcUtilityInstalled.value)
                settingsJson.put("codecs_pack_installed", _codecsPackInstalled.value)
                settingsJson.put("video_format", _videoFormat.value)
                settingsJson.put("video_player_engine", _videoPlayerEngine.value)
                settingsJson.put("video_language", _videoLanguage.value)
                settingsJson.put("video_subtitles", _videoSubtitles.value)
                settingsJson.put("audio_fix_ignore_errors", _audioFixIgnoreErrors.value)
                settingsJson.put("audio_fix_channels", _audioFixChannels.value)
                settingsJson.put("disable_trailers", _disableTrailers.value)
                settingsJson.put("sync_email", _cloudSyncEmail.value)
                rootJson.put("settings_prefs", settingsJson)

                val exportContent = rootJson.toString(4)
                val fileName = "blooders_tv_backup.json"
                val downloadDirs = listOf(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    java.io.File("/sdcard/Download"),
                    java.io.File("/storage/emulated/0/Download"),
                    getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                )
                
                var savedFile: java.io.File? = null
                for (dir in downloadDirs) {
                    if (dir != null) {
                        try {
                            if (!dir.exists()) {
                                dir.mkdirs()
                            }
                            val file = java.io.File(dir, fileName)
                            file.writeText(exportContent)
                            savedFile = file
                            break
                        } catch (e: Exception) {
                            // Try next
                        }
                    }
                }

                if (savedFile != null) {
                    onResult(true, "Copiado a Descargas: ${savedFile.absolutePath}", exportContent)
                } else {
                    onResult(false, "No se pudo guardar localmente", exportContent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Fallo al exportar: ${e.localizedMessage}", null)
            }
        }
    }

    fun importBackup(jsonString: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val rootJson = org.json.JSONObject(jsonString)
                if (rootJson.has("settings_prefs")) {
                    val settingsJson = rootJson.getJSONObject("settings_prefs")
                    val useVlc = settingsJson.optBoolean("use_vlc_player", false)
                    val vlcUtilInstalled = settingsJson.optBoolean("libvlc_utility_installed", false)
                    val codecsInstalled = settingsJson.optBoolean("codecs_pack_installed", false)
                    val format = settingsJson.optString("video_format", "forzar 16:9")
                    val engine = settingsJson.optString("video_player_engine", "Reproductor 2")
                    val lang = settingsJson.optString("video_language", "Español Latino")
                    val subs = settingsJson.optString("video_subtitles", "Desactivados")
                    val fixIgnore = settingsJson.optBoolean("audio_fix_ignore_errors", false)
                    val fixChan = settingsJson.optString("audio_fix_channels", "Stereo")
                    val noTrailers = settingsJson.optBoolean("disable_trailers", false)
                    val email = settingsJson.optString("sync_email", "")

                    _useVlcPlayer.value = useVlc
                    _libVlcUtilityInstalled.value = vlcUtilInstalled
                    _codecsPackInstalled.value = codecsInstalled
                    _videoFormat.value = format
                    _videoPlayerEngine.value = engine
                    _videoLanguage.value = lang
                    _videoSubtitles.value = subs
                    _audioFixIgnoreErrors.value = fixIgnore
                    _audioFixChannels.value = fixChan
                    _disableTrailers.value = noTrailers
                    _cloudSyncEmail.value = email

                    prefs.edit().apply {
                        putBoolean("use_vlc_player", useVlc)
                        putBoolean("libvlc_utility_installed", vlcUtilInstalled)
                        putBoolean("codecs_pack_installed", codecsInstalled)
                        putString("video_format", format)
                        putString("video_player_engine", engine)
                        putString("video_language", lang)
                        putString("video_subtitles", subs)
                        putBoolean("audio_fix_ignore_errors", fixIgnore)
                        putString("audio_fix_channels", fixChan)
                        putBoolean("disable_trailers", noTrailers)
                        putString("sync_email", email)
                        apply()
                    }
                }

                repository.importBackupString(jsonString)
                onComplete(true, "Importación exitosa")
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "Error de análisis: ${e.localizedMessage}")
            }
        }
    }

    // Screen States: "PROFILES", "MAIN", "DETAILS", "PLAYER", "SETTINGS"
    private val _currentScreen = MutableStateFlow("PROFILES")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    init {
        // Load history from SharedPreferences reactive to profile changes
        viewModelScope.launch {
            _currentProfile.collect { profile ->
                if (profile != null) {
                    val historyJson = prefs.getString("watch_history_json_${profile.id}", "") ?: ""
                    if (historyJson.isNotEmpty()) {
                        _watchHistory.value = deserializeChannelList(historyJson)
                    } else {
                        // Fallback to legacy urls if available
                        val historyStr = prefs.getString("watch_history_urls_${profile.id}", "") ?: ""
                        if (historyStr.isNotEmpty()) {
                            val urls = historyStr.split(",").filter { it.isNotEmpty() }
                            val matched = allChannels.value.filter { urls.contains(it.primaryStreamUrl) }
                            _watchHistory.value = matched
                        } else {
                            _watchHistory.value = emptyList()
                        }
                    }
                } else {
                    _watchHistory.value = emptyList()
                }
            }
        }

        pendingTvChannelUrlToRestore = prefs.getString("last_watched_tv_channel_url", null)

        viewModelScope.launch {
            allChannels.collect { channels ->
                val pendingUrl = pendingTvChannelUrlToRestore
                if (pendingUrl != null && channels.isNotEmpty()) {
                    val match = channels.find { it.primaryStreamUrl == pendingUrl }
                    if (match != null) {
                        _playingChannel.value = match
                        _activeStreamUrl.value = match.primaryStreamUrl
                        _activeCategory.value = match.groupTitle
                        pendingTvChannelUrlToRestore = null
                    }
                }
            }
        }

        // Keep last watched channel synced with active profile changes
        viewModelScope.launch {
            _currentProfile.collect { profile ->
                if (profile != null) {
                    val url = prefs.getString("last_watched_tv_channel_url_${profile.id}", null)
                        ?: prefs.getString("last_watched_tv_channel_url", null)
                    if (url != null) {
                        pendingTvChannelUrlToRestore = url
                        val match = allChannels.value.find { it.primaryStreamUrl == url }
                        if (match != null) {
                            _playingChannel.value = match
                            _activeStreamUrl.value = match.primaryStreamUrl
                            _activeCategory.value = match.groupTitle
                            pendingTvChannelUrlToRestore = null
                        }
                    }
                }
            }
        }

        // Prepopulate default profiles if empty (Extremely fast, local SQLite)
        viewModelScope.launch {
            repository.checkAndPrepopulateProfiles()
            
            val lastProfileId = prefs.getInt("last_watched_profile_id", -1)
            // Listen to profiles list; auto-set the last active profile or first profile as active once loaded
            profiles.firstOrNull { it.isNotEmpty() }?.let { loadedProfiles ->
                if (_currentProfile.value == null && loadedProfiles.isNotEmpty()) {
                    val targetProfile = loadedProfiles.find { it.id == lastProfileId } ?: loadedProfiles.first()
                    _currentProfile.value = targetProfile
                    // Keep the user on the PROFILES screen upon launch so they can choose or authenticate manually.
                    // Doing so prevents unrequested redirection loops, VLC auto-triggers, or automatic screen closures.
                }
            }
        }

        // Prepopulate default playlists in the background (Async network + SQLite insertion)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.checkAndPrepopulatePlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Start local TCP companion server for Chrome extension "Blooderscrap"
        startExtensionServer()
        loadChromeExtensions()

        // Autonomous internal scraper scheduler
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            combine(_searchQuery, _activeCategory, _blooderscrapIntegrado) { query, category, isEnabled ->
                Triple(query, category, isEnabled)
            }
            .debounce(400)
            .collectLatest { (query, category, isEnabled) ->
                if (!isEnabled) {
                    _scrapedCatalogItems.value = emptyList()
                    return@collectLatest
                }
                
                try {
                    if (query.isNotEmpty()) {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(query = query) } catch (e: Exception) { emptyList() }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(query = query) } catch (e: Exception) { emptyList() }
                        
                        val combined = ArrayList<UiChannel>()
                        
                        scrapedSM.forEach {
                            val isSeries = it.url.contains("/serie/") || it.url.contains("/series/") || it.url.contains("/temporada/") || it.url.contains("/episodio/") || it.url.contains("/capitulo/")
                            val isMovie = it.url.contains("/pelicula/") || it.url.contains("/movies/") || it.url.contains("/peli/")
                            
                            val itemGroup = if (isSeries) "SERIES" else "PELICULA"
                            val shouldAdd = when (category) {
                                "PELICULA" -> !isSeries
                                "SERIES" -> isSeries
                                else -> true
                            }
                            
                            if (shouldAdd) {
                                combined.add(UiChannel(
                                    name = it.title,
                                    groupTitle = itemGroup,
                                    logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                    primaryStreamUrl = it.url,
                                    sources = listOf(ChannelSource("BloodersTv", it.url)),
                                    synopsis = "Contenido de BloodersTv (SeriesMetro) disponible directamente.",
                                    rating = "8.8",
                                    year = it.year,
                                    director = "BloodersTv",
                                    actors = "Virtual",
                                    isEmbedText = true,
                                    originalGroup = "Búsqueda BloodersTv"
                                ))
                            }
                        }

                        scrapedPP.forEach {
                            val isSeries = it.url.contains("/serie/") || it.url.contains("/series/") || it.url.contains("/temporada/") || it.url.contains("/episodio/") || it.url.contains("/capitulo/")
                            val isMovie = it.url.contains("/pelicula/") || it.url.contains("/movies/") || it.url.contains("/peli/")
                            
                            val itemGroup = if (isSeries) "SERIES" else "PELICULA"
                            val shouldAdd = when (category) {
                                "PELICULA" -> !isSeries
                                "SERIES" -> isSeries
                                else -> true
                            }
                            
                            if (shouldAdd) {
                                combined.add(UiChannel(
                                    name = it.title,
                                    groupTitle = itemGroup,
                                    logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                    primaryStreamUrl = it.url,
                                    sources = listOf(ChannelSource("Blooders2", it.url)),
                                    synopsis = "Contenido de Blooders2 disponible directamente.",
                                    rating = "8.9",
                                    year = it.year,
                                    director = "Blooders2",
                                    actors = "Virtual",
                                    isEmbedText = true,
                                    originalGroup = "Búsqueda Blooders2"
                                ))
                            }
                        }
                        _scrapedCatalogItems.value = combined
                    } else if (category == "SERIES") {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1, categoryType = "SERIES") } catch (e: Exception) { emptyList() }
                        val filteredSM = scrapedSM.filter {
                            val isMovie = it.url.contains("/pelicula/") || it.url.contains("/movies/") || it.url.contains("/peli/")
                            !isMovie
                        }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        val filteredPP = scrapedPP.filter { it.url.contains("/serie/") || it.url.contains("/series/") }
                        
                        val combined = ArrayList<UiChannel>()
                        filteredSM.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "SERIES",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("BloodersTv", it.url)),
                                synopsis = "Contenido bajo de demanda de BloodersTv (SeriesMetro).",
                                rating = "8.8",
                                year = it.year,
                                director = "BloodersTv",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }
                        filteredPP.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "SERIES",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("Blooders2", it.url)),
                                synopsis = "Contenido de Blooders2 disponible directamente.",
                                rating = "8.9",
                                year = it.year,
                                director = "Blooders2",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }
                        _scrapedCatalogItems.value = combined
                    } else if (category == "PELICULA") {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1, categoryType = "PELICULA") } catch (e: Exception) { emptyList() }
                        val filteredSM = scrapedSM.filter { it.url.contains("/pelicula/") || it.url.contains("/movies/") || it.url.contains("/peli/") }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        val filteredPP = scrapedPP.filter { it.url.contains("/pelicula/") || it.url.contains("/movies/") || it.url.contains("/peli/") }

                        
                        val combined = ArrayList<UiChannel>()
                        filteredSM.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "PELICULA",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("BloodersTv", it.url)),
                                synopsis = "Contenido de BloodersTv (SeriesMetro) disponible directamente.",
                                rating = "8.8",
                                year = it.year,
                                director = "BloodersTv",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }
                        filteredPP.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "PELICULA",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("Blooders2", it.url)),
                                synopsis = "Contenido de Blooders2 disponible directamente.",
                                rating = "8.9",
                                year = it.year,
                                director = "Blooders2",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }

                        _scrapedCatalogItems.value = combined
                    } else if (category == "KIDS") {
                        val combined = ArrayList<UiChannel>()
                        val visitedUrls = HashSet<String>()
                        val kidsQueries = listOf("Disney", "Pixar", "Minions")
                        for (term in kidsQueries) {
                            try {
                                val results = PelisPlusScraper.scrapeList(query = term).take(10)
                                results.forEach {
                                    if (visitedUrls.add(it.url)) {
                                        combined.add(UiChannel(
                                            name = it.title,
                                            groupTitle = "KIDS",
                                            logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                            primaryStreamUrl = it.url,
                                            sources = listOf(ChannelSource("Blooders2", it.url)),
                                            synopsis = "Película o serie animada especial para niños y toda la familia.",
                                            rating = "9.2",
                                            year = it.year,
                                            director = "Blooders2 Kids",
                                            actors = "Infantil",
                                            isEmbedText = true,
                                            originalGroup = "BLOODERSTV_CATALOG"
                                        ))
                                    }
                                }

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        _scrapedCatalogItems.value = combined
                    } else if (category == "ANIME") {
                        val combined = ArrayList<UiChannel>()
                        val visitedUrls = HashSet<String>()
                        val animeQueries = listOf("Dragon Ball", "Naruto", "One Piece")
                        for (term in animeQueries) {
                            try {
                                val results = PelisPlusScraper.scrapeList(query = term).take(10)
                                results.forEach {
                                    if (visitedUrls.add(it.url)) {
                                        combined.add(UiChannel(
                                            name = it.title,
                                            groupTitle = "ANIME",
                                            logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                            primaryStreamUrl = it.url,
                                            sources = listOf(ChannelSource("Blooders2", it.url)),
                                            synopsis = "Disfruta de tus episodios y películas de anime de culto en Blooders2.",
                                            rating = "9.5",
                                            year = it.year,
                                            director = "Blooders2 Anime",
                                            actors = "Otaku",
                                            isEmbedText = true,
                                            originalGroup = "BLOODERSTV_CATALOG"
                                        ))
                                    }
                                }

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        _scrapedCatalogItems.value = combined
                    } else if (category == "EXTENSION") {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1) } catch (e: Exception) { emptyList() }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        
                        val combined = ArrayList<UiChannel>()
                        scrapedSM.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "EXTENSION",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("BloodersTv", it.url)),
                                synopsis = "Contenido bajo de demanda de BloodersTv (SeriesMetro).",
                                rating = "8.8",
                                year = it.year,
                                director = "BloodersTv",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }
                        scrapedPP.forEach {
                            combined.add(UiChannel(
                                name = it.title,
                                groupTitle = "EXTENSION",
                                logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
                                primaryStreamUrl = it.url,
                                sources = listOf(ChannelSource("Blooders2", it.url)),
                                synopsis = "Contenido de Blooders2 disponible directamente.",
                                rating = "8.9",
                                year = it.year,
                                director = "Blooders2",
                                actors = "Virtual",
                                isEmbedText = true,
                                originalGroup = "BLOODERSTV_CATALOG"
                            ))
                        }
                        _scrapedCatalogItems.value = combined
                    } else {
                        _scrapedCatalogItems.value = emptyList()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Unit
            }
        }

        // The last watched channel is restored automatically as a live preview highlighted on the dashboard.
    }

    private fun serializeChannelList(channels: List<UiChannel>): String {
        val array = JSONArray()
        for (ch in channels) {
            val obj = JSONObject()
            obj.put("name", ch.name)
            obj.put("groupTitle", ch.groupTitle)
            obj.put("logoUrl", ch.logoUrl)
            obj.put("primaryStreamUrl", ch.primaryStreamUrl)
            
            val sourcesArr = JSONArray()
            for (src in ch.sources) {
                val sObj = JSONObject()
                sObj.put("playlistName", src.playlistName)
                sObj.put("streamUrl", src.streamUrl)
                sourcesArr.put(sObj)
            }
            obj.put("sources", sourcesArr)
            obj.put("currentProgram", ch.currentProgram ?: "")
            obj.put("currentProgramDescription", ch.currentProgramDescription ?: "")
            obj.put("startEndText", ch.startEndText ?: "")
            obj.put("programProgress", ch.programProgress.toDouble())
            obj.put("isFavorite", ch.isFavorite)
            obj.put("rating", ch.rating)
            obj.put("year", ch.year)
            obj.put("director", ch.director)
            obj.put("actors", ch.actors)
            obj.put("synopsis", ch.synopsis)
            obj.put("originalGroup", ch.originalGroup)
            obj.put("isEmbedText", ch.isEmbedText)
            obj.put("adBlockerEnabled", ch.adBlockerEnabled)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeChannelList(jsonStr: String): List<UiChannel> {
        if (jsonStr.isEmpty()) return emptyList()
        val list = ArrayList<UiChannel>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "")
                val groupTitle = obj.optString("groupTitle", "")
                val logoUrl = obj.optString("logoUrl", "")
                val primaryStreamUrl = obj.optString("primaryStreamUrl", "")
                
                val sources = ArrayList<ChannelSource>()
                val sourcesArr = obj.optJSONArray("sources")
                if (sourcesArr != null) {
                    for (j in 0 until sourcesArr.length()) {
                        val sObj = sourcesArr.getJSONObject(j)
                        sources.add(
                            ChannelSource(
                                playlistName = sObj.optString("playlistName", ""),
                                streamUrl = sObj.optString("streamUrl", "")
                            )
                        )
                    }
                }
                
                list.add(
                    UiChannel(
                        name = name,
                        groupTitle = groupTitle,
                        logoUrl = logoUrl,
                        primaryStreamUrl = primaryStreamUrl,
                        sources = sources,
                        currentProgram = obj.optString("currentProgram").takeIf { it.isNotEmpty() },
                        currentProgramDescription = obj.optString("currentProgramDescription").takeIf { it.isNotEmpty() },
                        startEndText = obj.optString("startEndText").takeIf { it.isNotEmpty() },
                        programProgress = obj.optDouble("programProgress", 0.0).toFloat(),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        rating = obj.optString("rating", "8.2"),
                        year = obj.optString("year", "2026"),
                        director = obj.optString("director", "Blooders Creator"),
                        actors = obj.optString("actors", "Zazie Beetz..."),
                        synopsis = obj.optString("synopsis", ""),
                        originalGroup = obj.optString("originalGroup", "TV"),
                        isEmbedText = obj.optBoolean("isEmbedText", false),
                        adBlockerEnabled = obj.optBoolean("adBlockerEnabled", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addToHistory(channel: UiChannel) {
        // Only movies, series, and extension streams are allowed in the history
        if (channel.groupTitle != "PELICULA" && channel.groupTitle != "SERIES" && channel.groupTitle != "EXTENSION") {
            return
        }

        val currentList = _watchHistory.value.toMutableList()
        currentList.removeAll { it.primaryStreamUrl == channel.primaryStreamUrl || it.name == channel.name }
        currentList.add(0, channel)

        // Limit to 50 items
        val cappedList = if (currentList.size > 50) currentList.take(50) else currentList
        _watchHistory.value = cappedList
        val activeProfileId = _currentProfile.value?.id ?: 0
        prefs.edit().putString("watch_history_json_$activeProfileId", serializeChannelList(cappedList)).apply()
    }

    fun clearHistory() {
        _watchHistory.value = emptyList()
        val activeProfileId = _currentProfile.value?.id ?: 0
        prefs.edit().remove("watch_history_json_$activeProfileId").apply()
        prefs.edit().remove("watch_history_urls_$activeProfileId").apply()
    }

    // --- Profile Actions ---
    fun selectProfile(profile: Profile) {
        _navigationHistory.clear()
        _currentProfile.value = profile
        _currentScreen.value = "MAIN"
        // Update selected channel and reset player to avoid spill-over
        _selectedChannel.value = null
        
        val lastUrl = prefs.getString("last_watched_tv_channel_url_${profile.id}", null)
            ?: prefs.getString("last_watched_tv_channel_url", null)
        
        if (lastUrl != null) {
            pendingTvChannelUrlToRestore = lastUrl
            val match = allChannels.value.find { it.primaryStreamUrl == lastUrl }
            if (match != null) {
                _playingChannel.value = match
                _activeStreamUrl.value = match.primaryStreamUrl
                _activeCategory.value = match.groupTitle
                pendingTvChannelUrlToRestore = null
            } else {
                _playingChannel.value = null
                _activeStreamUrl.value = ""
            }
        } else {
            _playingChannel.value = null
            _activeStreamUrl.value = ""
            pendingTvChannelUrlToRestore = null
        }
        
        // Sync profile-specific M3U in background
        viewModelScope.launch {
            repository.syncProfileM3u(profile)
        }
    }

    fun createProfile(name: String, avatarUrl: String, pinCode: String? = null, customM3uUrl: String? = null, customEpgUrl: String? = null) {
        viewModelScope.launch {
            repository.addProfile(name, avatarUrl, pinCode, customM3uUrl, customEpgUrl)
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {
            repository.updateProfile(profile)
            if (_currentProfile.value?.id == profile.id) {
                _currentProfile.value = profile
                repository.syncProfileM3u(profile)
            }
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            if (_currentProfile.value?.id == profile.id) {
                val available = profiles.value.filter { it.id != profile.id }
                _currentProfile.value = available.firstOrNull()
            }
        }
    }

    // --- Playlist & Channels sync ---
    fun addPlaylist(name: String, url: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC", onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            try {
                repository.addPlaylist(name, url, classification, playbackMode)
                _cloudSyncStatus.value = "Sincronizado"
                onComplete(true, null)
            } catch (e: Exception) {
                _cloudSyncStatus.value = "Sincronizado"
                val errorMsg = e.localizedMessage ?: "Error al sincronizar"
                onComplete(false, errorMsg)
            }
        }
    }

    fun addLocalPlaylist(name: String, content: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC") {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            repository.addLocalPlaylist(name, content, classification, playbackMode)
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    fun addLocalTxtPlaylist(name: String, content: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC", onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            try {
                repository.addLocalTxtPlaylist(name, content, classification, playbackMode)
                _cloudSyncStatus.value = "Sincronizado"
                onComplete(true, null)
            } catch (e: Exception) {
                _cloudSyncStatus.value = "Sincronizado"
                val errorMsg = e.localizedMessage ?: "Error al procesar archivo TXT"
                onComplete(false, errorMsg)
            }
        }
    }

    fun updatePlaylistClassification(playlistId: Int, classification: String) {
        _cloudSyncStatus.value = "Actualizando clasificación..."
        viewModelScope.launch {
            repository.updatePlaylistClassification(playlistId, classification)
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    fun updatePlaylistPlaybackMode(playlistId: Int, playbackMode: String) {
        _cloudSyncStatus.value = "Actualizando modo de reproducción..."
        viewModelScope.launch {
            repository.updatePlaylistPlaybackMode(playlistId, playbackMode)
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    fun addDirectStreamChannel(
        name: String,
        streamUrl: String,
        category: String,
        isEmbedText: Boolean = false,
        adBlockerEnabled: Boolean = false
    ) {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            repository.addDirectStreamChannel(name, streamUrl, category, isEmbedText, adBlockerEnabled)
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    fun deletePlaylist(playlistId: Int) {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    fun refreshPlaylists() {
        _cloudSyncStatus.value = "Sincronizando..."
        viewModelScope.launch {
            repository.reloadAllPlaylists()
            _cloudSyncStatus.value = "Sincronizado"
        }
    }

    // --- Media playback ---
    fun playChannel(channel: UiChannel) {
        pushToNavigationHistory()
        val isMovieOrSeries = channel.groupTitle == "PELICULA" || channel.groupTitle == "SERIES"
        val resolvedStreamUrl = if (isMovieOrSeries && channel.sources.isNotEmpty()) {
            channel.sources[0].streamUrl
        } else {
            channel.primaryStreamUrl
        }

        _playingChannel.value = channel
        _activeStreamUrl.value = resolvedStreamUrl
        addToHistory(channel)

        // Save as last watched channel
        prefs.edit().apply {
            putString("last_watched_tv_channel_url", channel.primaryStreamUrl)
            _currentProfile.value?.let { profile ->
                putInt("last_watched_profile_id", profile.id)
                putString("last_watched_tv_channel_url_${profile.id}", channel.primaryStreamUrl)
            }
            apply()
        }

        viewModelScope.launch {
            val mode = repository.getPlaylistPlaybackModeForStream(channel.primaryStreamUrl)
            _isVlcAudioActive.value = false
            val shouldPlayWithVlc = when (mode) {
                "VLC" -> true
                "INTEGRATED" -> false
                else -> _useVlcPlayer.value
            }
            if (shouldPlayWithVlc) {
                launchVlcPlayer(resolvedStreamUrl)
            } else {
                if (isMovieOrSeries) {
                    _selectedChannel.value = channel
                    _isPlayerMaximized.value = true
                    _currentScreen.value = "DETAILS"
                } else {
                    _isPlayerMaximized.value = true
                    _currentScreen.value = "MAIN"
                }
            }
        }
    }

    private fun launchVlcPlayer(streamUrl: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(streamUrl), "video/*")
                setPackage("org.videolan.vlc")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", _playingChannel.value?.name ?: "Blooders TV")
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _playbackMessage.value = "VLC no está instalado. Iniciando reproductor interno..."
            _isPlayerMaximized.value = true
            _currentScreen.value = "MAIN"
        }
    }

    fun playPreviewChannel(channel: UiChannel) {
        _playingChannel.value = channel
        _activeStreamUrl.value = channel.primaryStreamUrl
        addToHistory(channel)

        // Save as last watched channel
        prefs.edit().apply {
            putString("last_watched_tv_channel_url", channel.primaryStreamUrl)
            _currentProfile.value?.let { profile ->
                putInt("last_watched_profile_id", profile.id)
                putString("last_watched_tv_channel_url_${profile.id}", channel.primaryStreamUrl)
            }
            apply()
        }
    }

    fun changeSource(streamUrl: String) {
        _activeStreamUrl.value = streamUrl
    }

    fun stopPlayback() {
        _playingChannel.value = null
        _activeStreamUrl.value = ""
    }

    // Real-time synchronization cache for seamless transition between mini and fullscreen players
    private val _livePlaybackPositions = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun setLivePlaybackPosition(streamUrl: String, position: Long) {
        if (streamUrl.isNotEmpty() && position > 0) {
            _livePlaybackPositions[streamUrl] = position
        }
    }

    fun getLivePlaybackPosition(streamUrl: String): Long {
        return _livePlaybackPositions[streamUrl] ?: 0L
    }

    // --- Navigation Flow control ---
    fun selectCategory(category: String) {
        _activeCategory.value = category
        _searchQuery.value = "" // Clear query when changing categories to restore dynamic list
    }

    fun selectSeason(seasonNum: Int) {
        pushToNavigationHistory()
        _selectedSeason.value = seasonNum
        val epInSeason = _seriesEpisodes.value.find { ch ->
            val info = com.example.ui.parseSeriesEpisode(ch)
            info != null && info.season == seasonNum
        }
        if (epInSeason != null) {
            _selectedChannel.value = epInSeason
            val info = com.example.ui.parseSeriesEpisode(epInSeason)
            if (info != null) {
                _selectedEpisodeNum.value = info.episode
                _activeStreamUrl.value = epInSeason.primaryStreamUrl
                _playingChannel.value = epInSeason
                addToHistory(epInSeason)
            }
        }
    }

    fun selectEpisode(episodeNum: Int) {
        pushToNavigationHistory()
        val isSeriesMetro = _seriesEpisodes.value.any { it.originalGroup == "BLOODERSCRAP_EP" }
        if (isSeriesMetro) {
            val ep = _seriesEpisodes.value.getOrNull(episodeNum - 1)
            if (ep != null) {
                _selectedChannel.value = ep
                _selectedEpisodeNum.value = episodeNum
                _playingChannel.value = ep
                addToHistory(ep)
                if (ep.primaryStreamUrl.contains("pelisplus.la") || ep.primaryStreamUrl.contains("poseidonhd") || ep.primaryStreamUrl.contains("poseidonhd2.co")) {
                    loadPelisPlusChapterSource(ep)
                } else {
                    loadSeriesMetroChapterSource(ep)
                }
            }
            return
        }

        val ep = _seriesEpisodes.value.find { ch ->
            val info = com.example.ui.parseSeriesEpisode(ch)
            info != null && info.season == _selectedSeason.value && info.episode == episodeNum
        }
        if (ep != null) {
            _selectedChannel.value = ep
            _selectedEpisodeNum.value = episodeNum
            _activeStreamUrl.value = ep.primaryStreamUrl
            _playingChannel.value = ep
            addToHistory(ep)
        }
    }

    fun parseSeriesEpisode(channel: UiChannel): SeriesEpisodeInfo? {
        return com.example.ui.parseSeriesEpisode(channel)
    }

    fun toggleBlooderscrapIntegrado() {
        val newValue = !_blooderscrapIntegrado.value
        prefs.edit().putBoolean("blooderscrap_integrado", newValue).apply()
        _blooderscrapIntegrado.value = newValue
    }

    fun loadChromeExtensions() {
        _chromeExtensions.value = ChromeExtensionManager.getExtensions(getApplication())
    }

    fun toggleChromeExtension(id: String) {
        ChromeExtensionManager.toggleExtension(getApplication(), id)
        loadChromeExtensions()
    }

    fun deleteChromeExtension(id: String) {
        ChromeExtensionManager.removeExtension(getApplication(), id)
        loadChromeExtensions()
    }

    fun addChromeExtension(name: String, description: String, matches: List<String>, jsCode: String) {
        val extension = ChromeExtension(
            id = "ext_" + System.currentTimeMillis(),
            name = name,
            version = "1.0.0",
            description = description,
            isEnabled = true,
            isBuiltIn = false,
            matches = matches,
            jsCode = jsCode
        )
        ChromeExtensionManager.addExtension(getApplication(), extension)
        loadChromeExtensions()
    }

    fun importUnpackedExtensionFromManifestAndScript(manifestJsonStr: String, contentScriptJsStr: String) : Boolean {
        return try {
            val obj = JSONObject(manifestJsonStr)
            val name = obj.optString("name", "Extensión Desempaquetada")
            val desc = obj.optString("description", "Importada por usuario.")
            val ver = obj.optString("version", "1.0.0")
            
            // Extract matches
            val matchesList = mutableListOf<String>()
            val contentScripts = obj.optJSONArray("content_scripts")
            if (contentScripts != null && contentScripts.length() > 0) {
                val firstScriptObj = contentScripts.optJSONObject(0)
                if (firstScriptObj != null) {
                    val matchesArray = firstScriptObj.optJSONArray("matches")
                    if (matchesArray != null) {
                        for (i in 0 until matchesArray.length()) {
                            matchesList.add(matchesArray.getString(i))
                        }
                    }
                }
            }
            if (matchesList.isEmpty()) {
                matchesList.add("*://*/*")
            }

            val extension = ChromeExtension(
                id = "ext_" + System.currentTimeMillis() + "_" + name.replace(" ", "_").lowercase(),
                name = name,
                version = ver,
                description = desc,
                isEnabled = true,
                isBuiltIn = false,
                matches = matchesList,
                jsCode = contentScriptJsStr
            )
            ChromeExtensionManager.addExtension(getApplication(), extension)
            loadChromeExtensions()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadSeriesMetroDetails(channel: UiChannel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (channel.primaryStreamUrl.contains("/serie/")) {
                    val detail = SeriesMetroScraper.scrapeSerieDetail(channel.primaryStreamUrl)
                    if (detail != null) {
                        val episodesList = detail.capitulos.map { cap ->
                            UiChannel(
                                name = cap.title,
                                groupTitle = "SERIES",
                                logoUrl = detail.img.ifEmpty { channel.logoUrl },
                                primaryStreamUrl = cap.url,
                                sources = listOf(ChannelSource("Blooderscrap Opción 1", cap.url)),
                                synopsis = detail.desc.ifEmpty { channel.synopsis },
                                rating = "8.8",
                                year = channel.year,
                                director = channel.name,
                                actors = "Capítulo de BloodersTv",
                                isEmbedText = true,
                                originalGroup = "BLOODERSCRAP_EP"
                            )
                        }

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _seriesEpisodes.value = episodesList
                            _selectedSeason.value = 1
                            _selectedEpisodeNum.value = 1
                            
                            _selectedChannel.value = channel.copy(
                                synopsis = detail.desc.ifEmpty { channel.synopsis },
                                logoUrl = detail.img.ifEmpty { channel.logoUrl }
                            )

                            if (episodesList.isNotEmpty()) {
                                loadSeriesMetroChapterSource(episodesList[0])
                            }
                        }
                    }
                } else if (channel.primaryStreamUrl.contains("/capitulo/") || 
                           channel.primaryStreamUrl.contains("/pelicula/") || 
                           channel.primaryStreamUrl.contains("/peli/")) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _seriesEpisodes.value = emptyList()
                        _selectedChannel.value = channel
                    }
                    loadSeriesMetroChapterSource(channel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _currentScreen.value = "DETAILS"
    }

    fun loadSeriesMetroChapterSource(chapterChannel: UiChannel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val capDetail = SeriesMetroScraper.scrapeCapituloDetail(chapterChannel.primaryStreamUrl)
                if (capDetail != null) {
                    val buildSources = ArrayList<ChannelSource>()
                    
                    coroutineScope {
                        val deferredList = ArrayList<kotlinx.coroutines.Deferred<ChannelSource>>()

                        if (capDetail.trid.isNotEmpty()) {
                            val numOptions = if (capDetail.opciones.isNotEmpty()) capDetail.opciones.size else 1
                            for (i in 0 until numOptions) {
                                val label = if (i < capDetail.opciones.size) capDetail.opciones[i].label else "Opción ${i + 1}"
                                val idx = if (i < capDetail.opciones.size) capDetail.opciones[i].index else i.toString()
                                val embedUrl = "https://www3.seriesmetro.net/?trembed=$idx&trid=${capDetail.trid}&trtype=2"
                                
                                deferredList.add(async {
                                    val resolvedUrl = SeriesMetroScraper.resolveDirectVideoIframe(embedUrl)
                                    ChannelSource(label, resolvedUrl)
                                })
                            }
                        }

                        capDetail.embedUrls.forEachIndexed { idx, url ->
                            deferredList.add(async {
                                val resolvedUrl = if (url.contains("trembed")) {
                                    SeriesMetroScraper.resolveDirectVideoIframe(url)
                                } else {
                                    url
                                }
                                ChannelSource("Enlace Directo ${idx + 1}", resolvedUrl)
                            })
                        }

                        // Concurrent parallel resolution across all available embed/transition options
                        val resolvedList = awaitAll(*deferredList.toTypedArray())
                        resolvedList.forEach { source ->
                            val cleanName = source.playlistName.lowercase().trim()
                            val cleanUrl = source.streamUrl.lowercase().trim()
                            
                            val isInvalidOpcionNumber = cleanName.matches("""opci[oó]n\s+\d+""".toRegex())
                            val isInvalidFastream = cleanUrl == "https://fastream.to/embed" || cleanUrl == "https://fastream.to/embed/" || cleanUrl.isEmpty()
                            
                            if (!isInvalidOpcionNumber && !isInvalidFastream) {
                                if (!buildSources.any { it.streamUrl == source.streamUrl }) {
                                    buildSources.add(source)
                                }
                            }
                        }
                    }

                    if (buildSources.isEmpty()) {
                        buildSources.add(ChannelSource("Original", chapterChannel.primaryStreamUrl))
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val updatedChannel = chapterChannel.copy(
                            sources = buildSources,
                            isEmbedText = true,
                            adBlockerEnabled = true
                        )
                        _selectedChannel.value = updatedChannel
                        _playingChannel.value = updatedChannel
                        _activeStreamUrl.value = buildSources[0].streamUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPelisPlusDetails(channel: UiChannel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (channel.primaryStreamUrl.contains("/serie/") || channel.primaryStreamUrl.contains("/series/")) {
                    val detail = PelisPlusScraper.scrapeDetail(channel.primaryStreamUrl)
                    if (detail != null) {
                        val episodesList = detail.capitulos.map { cap ->
                            UiChannel(
                                name = cap.title,
                                groupTitle = "SERIES",
                                logoUrl = detail.img.ifEmpty { channel.logoUrl },
                                primaryStreamUrl = cap.url,
                                sources = listOf(ChannelSource("Blooders2 Opción 1", cap.url)),
                                synopsis = detail.desc.ifEmpty { channel.synopsis },
                                rating = "8.9",
                                year = channel.year,
                                director = channel.name,
                                actors = "Capítulo de Blooders2",
                                isEmbedText = true,
                                originalGroup = "BLOODERSCRAP_EP"
                            )
                        }

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _seriesEpisodes.value = episodesList
                            _selectedSeason.value = 1
                            _selectedEpisodeNum.value = 1
                            
                            _selectedChannel.value = channel.copy(
                                synopsis = detail.desc.ifEmpty { channel.synopsis },
                                logoUrl = detail.img.ifEmpty { channel.logoUrl }
                            )

                            if (episodesList.isNotEmpty()) {
                                loadPelisPlusChapterSource(episodesList[0])
                            }
                        }
                    }
                } else if (channel.primaryStreamUrl.contains("/capitulo/") || 
                           channel.primaryStreamUrl.contains("/episodio/") || 
                           channel.primaryStreamUrl.contains("/pelicula/") || 
                           channel.primaryStreamUrl.contains("/movies/") || 
                           channel.primaryStreamUrl.contains("/peli/")) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _seriesEpisodes.value = emptyList()
                        _selectedChannel.value = channel
                    }
                    loadPelisPlusChapterSource(channel)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _currentScreen.value = "DETAILS"
    }

    fun loadPelisPlusChapterSource(chapterChannel: UiChannel) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val capDetail = PelisPlusScraper.scrapeCapituloDetail(chapterChannel.primaryStreamUrl)
                if (capDetail != null) {
                    val buildSources = ArrayList<ChannelSource>()
                    
                    coroutineScope {
                        val deferredList = ArrayList<kotlinx.coroutines.Deferred<ChannelSource>>()

                        if (capDetail.trid.isNotEmpty()) {
                            val numOptions = if (capDetail.opciones.isNotEmpty()) capDetail.opciones.size else 1
                            for (i in 0 until numOptions) {
                                val label = if (i < capDetail.opciones.size) capDetail.opciones[i].label else "Opción ${i + 1}"
                                val idx = if (i < capDetail.opciones.size) capDetail.opciones[i].index else i.toString()
                                val embedUrl = PelisPlusScraper.ensureAbsoluteUrl("/?trembed=$idx&trid=${capDetail.trid}")
                                
                                deferredList.add(async {
                                    val resolvedUrl = PelisPlusScraper.resolveDirectVideoIframe(embedUrl)
                                    val server = PelisPlusScraper.getServerName(resolvedUrl)
                                    val finalLabel = if (server != "Servidor") "$label ($server)" else label
                                    ChannelSource(finalLabel, resolvedUrl)
                                })
                            }
                        }

                        capDetail.embedUrls.forEachIndexed { idx, url ->
                            deferredList.add(async {
                                val resolvedUrl = if (url.contains("trembed") || url.contains("embed") || url.contains("player")) {
                                    PelisPlusScraper.resolveDirectVideoIframe(url)
                                } else {
                                    url
                                }
                                val server = PelisPlusScraper.getServerName(resolvedUrl)
                                val finalLabel = if (server != "Servidor") "Enlace $server" else "Blooders2 Enlace ${idx + 1}"
                                ChannelSource(finalLabel, resolvedUrl)
                            })
                        }

                        val resolvedList = awaitAll(*deferredList.toTypedArray())
                        resolvedList.forEach { source ->
                            val cleanName = source.playlistName.lowercase().trim()
                            val cleanUrl = source.streamUrl.lowercase().trim()
                            
                            val isInvalidOpcionNumber = cleanName.matches("""opci[oó]n\s+\d+""".toRegex())
                            val isInvalidFastream = cleanUrl == "https://fastream.to/embed" || cleanUrl == "https://fastream.to/embed/" || cleanUrl.isEmpty()
                            
                            if (!isInvalidOpcionNumber && !isInvalidFastream) {
                                if (!buildSources.any { it.streamUrl == source.streamUrl }) {
                                    buildSources.add(source)
                                }
                            }
                        }
                    }

                    if (buildSources.isEmpty()) {
                        buildSources.add(ChannelSource("Blooders2 Original", chapterChannel.primaryStreamUrl))
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val updatedChannel = chapterChannel.copy(
                            sources = buildSources,
                            isEmbedText = true,
                            adBlockerEnabled = true,
                            logoUrl = capDetail.img.ifEmpty { chapterChannel.logoUrl }
                        )
                        _selectedChannel.value = updatedChannel
                        _playingChannel.value = updatedChannel
                        _activeStreamUrl.value = buildSources[0].streamUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectPremiumSeriesByName(seriesName: String) {
        // 1. Try to find locally in allChannels
        val localEp = allChannels.value.find { 
            it.name.contains(seriesName, ignoreCase = true) && 
            (it.name.contains("E01", ignoreCase = true) || it.name.contains("S01 E01", ignoreCase = true) || it.name.contains("Capitulo 1", ignoreCase = true))
        } ?: allChannels.value.find { it.name.contains(seriesName, ignoreCase = true) }

        if (localEp != null) {
            selectChannelDetails(localEp)
        } else {
            // 2. If not found locally, construct the web series page URL so the live scraper can load all matching seasons and episodes
            val searchUrl = if (seriesName.contains("Breaking Bad", ignoreCase = true)) {
                "https://www3.seriesmetro.net/serie/breaking-bad/"
            } else if (seriesName.contains("Better Call Saul", ignoreCase = true)) {
                "https://www3.seriesmetro.net/serie/better-call-saul/"
            } else {
                ""
            }
            
            if (searchUrl.isNotEmpty()) {
                val syntheticChannel = UiChannel(
                    name = seriesName,
                    groupTitle = "SERIES",
                    logoUrl = if (seriesName.contains("Breaking Bad", ignoreCase = true)) {
                        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250"
                    } else {
                        "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=250"
                    },
                    primaryStreamUrl = searchUrl,
                    sources = listOf(ChannelSource("Blooderscrap Opción 1", searchUrl)),
                    synopsis = "Cargando todos los capítulos y temporadas de la serie desde el servidor seguro...",
                    originalGroup = "SERIES"
                )
                selectChannelDetails(syntheticChannel)
            }
        }
    }

    fun selectChannelDetails(channel: UiChannel?) {
        pushToNavigationHistory()
        try {
            _selectedChannel.value = channel
            _playingChannel.value = channel
            _imdbDetailsState.value = null
            _activeTrailerUrl.value = null
            if (channel != null) {
                if (channel.primaryStreamUrl.contains("seriesmetro.net")) {
                    loadSeriesMetroDetails(channel)
                } else if (channel.primaryStreamUrl.contains("pelisplus.la") || channel.primaryStreamUrl.contains("poseidonhd") || channel.primaryStreamUrl.contains("poseidonhd2.co")) {
                    loadPelisPlusDetails(channel)
                } else {
                    fetchImdbAndTrailerDetails(channel)
                    val epInfo = parseSeriesEpisode(channel)
                    if (epInfo != null) {
                        val sName = epInfo.seriesName
                        val eps = allChannels.value.filter { ch ->
                            val info = parseSeriesEpisode(ch)
                            info != null && info.seriesName.equals(sName, ignoreCase = true)
                        }.sortedWith(compareBy<UiChannel> {
                            val info = parseSeriesEpisode(it)
                            info?.season ?: 1
                        }.thenBy {
                            val info = parseSeriesEpisode(it)
                            info?.episode ?: 1
                        })
                        
                        _seriesEpisodes.value = eps
                        _selectedSeason.value = epInfo.season
                        _selectedEpisodeNum.value = epInfo.episode
                        _activeStreamUrl.value = channel.primaryStreamUrl
                        addToHistory(channel)
                    } else {
                        _seriesEpisodes.value = emptyList()
                        _activeStreamUrl.value = channel.primaryStreamUrl
                        addToHistory(channel)
                    }
                    _currentScreen.value = "DETAILS"
                }
            } else {
                _seriesEpisodes.value = emptyList()
                _activeStreamUrl.value = ""
                _isPlayerMaximized.value = false
                _currentScreen.value = "MAIN"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _seriesEpisodes.value = emptyList()
            _activeStreamUrl.value = channel?.primaryStreamUrl ?: ""
            _currentScreen.value = if (channel != null) "DETAILS" else "MAIN"
        }
    }

    fun setScreen(screenName: String) {
        if (_currentScreen.value != screenName) {
            pushToNavigationHistory()
            _currentScreen.value = screenName
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(channel: UiChannel) {
        val currentProf = _currentProfile.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(currentProf.id, channel)
            
            // Re-sync favorite indicator status for active channel preview / detail view
            val updatedAll = repository.getUiChannels(currentProf.id).first()
            val updatedSelf = updatedAll.find { it.name == channel.name }
            if (updatedSelf != null) {
                if (_selectedChannel.value?.name == channel.name) {
                    _selectedChannel.value = updatedSelf
                }
                if (_playingChannel.value?.name == channel.name) {
                    _playingChannel.value = updatedSelf
                }
            }
        }
    }

    // --- Playback Progress Flow Actions ---
    suspend fun getPlaybackProgress(streamUrl: String): PlaybackProgress? {
        val pId = currentProfile.value?.id ?: return null
        return repository.getPlaybackProgress(pId, streamUrl)
    }

    fun savePlaybackProgress(streamUrl: String, position: Long, duration: Long) {
        val pId = currentProfile.value?.id ?: return
        viewModelScope.launch {
            repository.savePlaybackProgress(
                PlaybackProgress(
                    profileId = pId,
                    streamUrl = streamUrl,
                    position = position,
                    duration = duration
                )
            )
        }
    }

    fun deletePlaybackProgress(streamUrl: String) {
        val pId = currentProfile.value?.id ?: return
        viewModelScope.launch {
            repository.deletePlaybackProgress(pId, streamUrl)
        }
    }

    // --- Chrome Extension Companion Scraper ("Blooderscrap") Server ---
    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun startExtensionServer() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(9999)
                while (true) {
                    val socket = serverSocket.accept()
                    handleSocketInput(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _extensionStatus.value = "Error: ${e.localizedMessage}"
            } finally {
                try { serverSocket?.close() } catch (ex: Exception) {}
            }
        }
    }

    private fun handleSocketInput(socket: Socket) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val out = socket.getOutputStream()
                
                var line = reader.readLine() ?: ""
                if (line.isEmpty()) {
                    socket.close()
                    return@launch
                }
                
                val parts = line.split(" ")
                if (parts.size < 3) {
                    socket.close()
                    return@launch
                }
                val method = parts[0]
                val path = parts[1]
                
                var contentLength = 0
                while (true) {
                    val headerLine = reader.readLine() ?: ""
                    if (headerLine.isEmpty()) break
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substring(15).trim().toIntOrNull() ?: 0
                    }
                }
                
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    val response = "HTTP/1.1 204 No Content\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Content-Type\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.flush()
                    socket.close()
                    return@launch
                }
                
                if (path.startsWith("/ping") || path.startsWith("/status")) {
                    _lastHeartbeat.value = System.currentTimeMillis()
                    _extensionStatus.value = "Activo (Conectado)"
                    val jsonResponse = "{\"status\":\"ok\",\"app\":\"blooderstv\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                } else if (method.equals("POST", ignoreCase = true) && path.startsWith("/scrap")) {
                    _lastHeartbeat.value = System.currentTimeMillis()
                    _extensionStatus.value = "Activo (Datos Recibidos)"
                    
                    val bodyChars = CharArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    val requestBody = String(bodyChars)
                    
                    try {
                        val jsonObj = JSONObject(requestBody)
                        val type = jsonObj.optString("type", "")
                        val title = jsonObj.optString("title", "Blooderscrap")
                        
                        _scrapedType.value = type
                        _scrapedTitle.value = title
                        
                        val newUiChannels = ArrayList<UiChannel>()
                        
                        if (type == "lista") {
                            val itemsArr = jsonObj.optJSONArray("items")
                            if (itemsArr != null) {
                                for (i in 0 until itemsArr.length()) {
                                    val item = itemsArr.optJSONObject(i) ?: continue
                                    val itemTitle = item.optString("title", "")
                                    val itemUrl = item.optString("url", "")
                                    val itemImg = item.optString("img", "")
                                    
                                    newUiChannels.add(
                                        UiChannel(
                                            name = itemTitle,
                                            groupTitle = "EXTENSION",
                                            logoUrl = itemImg,
                                            primaryStreamUrl = itemUrl,
                                            sources = listOf(ChannelSource("Ver en Web / Scraped", itemUrl)),
                                            currentProgram = "Página de Serie",
                                            currentProgramDescription = itemUrl,
                                            rating = "8.2",
                                            year = "2026",
                                            synopsis = "Enlace detectado por BloodersTv. Haz clic para detalles.",
                                            originalGroup = "Serie Buscada",
                                            isEmbedText = true
                                        )
                                    )
                                }
                            }
                        } else if (type == "serie") {
                            val img = jsonObj.optString("img", "")
                            val desc = jsonObj.optString("desc", "")
                            val capitulosArr = jsonObj.optJSONArray("capitulos")
                            
                            if (capitulosArr != null) {
                                for (i in 0 until capitulosArr.length()) {
                                    val cap = capitulosArr.optJSONObject(i) ?: continue
                                    val capTitle = cap.optString("titulo", "")
                                    val capUrl = cap.optString("url", "")
                                    
                                    newUiChannels.add(
                                        UiChannel(
                                            name = capTitle,
                                            groupTitle = "EXTENSION",
                                            logoUrl = img,
                                            primaryStreamUrl = capUrl,
                                            sources = listOf(ChannelSource("Ver en Web / Scraped", capUrl)),
                                            currentProgram = capTitle,
                                            currentProgramDescription = capUrl,
                                            rating = "8.2",
                                            year = "2026",
                                            synopsis = desc.ifEmpty { "Capítulo detectador por la extensión." },
                                            originalGroup = title,
                                            isEmbedText = true
                                        )
                                    )
                                }
                            }
                        } else if (type == "capitulo") {
                            val embedUrlsArr = jsonObj.optJSONArray("embedUrls")
                            val opcionesArr = jsonObj.optJSONArray("opciones")
                            val trid = jsonObj.optString("trid", "")
                            
                            val sourcesList = ArrayList<ChannelSource>()
                            
                            if (opcionesArr != null && opcionesArr.length() > 0) {
                                for (i in 0 until opcionesArr.length()) {
                                    val opt = opcionesArr.optJSONObject(i) ?: continue
                                    val label = opt.optString("label", "Opción ${i + 1}")
                                    val optIdx = opt.optInt("index", i)
                                    val embedUrl = if (trid.isNotEmpty()) {
                                        "https://www3.seriesmetro.net/?trembed=0&trid=$trid&trtype=${optIdx + 2}"
                                    } else {
                                        ""
                                    }
                                    if (embedUrl.isNotEmpty()) {
                                        sourcesList.add(ChannelSource(label, embedUrl))
                                    }
                                }
                            }
                            
                            if (embedUrlsArr != null) {
                                for (i in 0 until embedUrlsArr.length()) {
                                    val url = embedUrlsArr.optString(i, "")
                                    if (url.isNotEmpty() && !sourcesList.any { it.streamUrl == url }) {
                                        sourcesList.add(ChannelSource("Enlace Encontrado #${i + 1}", url))
                                    }
                                }
                            }
                            
                            if (sourcesList.isEmpty() && trid.isNotEmpty()) {
                                sourcesList.add(ChannelSource("Opción Latino", "https://www3.seriesmetro.net/?trembed=0&trid=$trid&trtype=2"))
                                sourcesList.add(ChannelSource("Opción Castellano", "https://www3.seriesmetro.net/?trembed=0&trid=$trid&trtype=3"))
                                sourcesList.add(ChannelSource("Opción Subtitulado", "https://www3.seriesmetro.net/?trembed=0&trid=$trid&trtype=4"))
                            }
                            
                            val finalSourcesList = sourcesList.filterNot { source ->
                                val cleanName = source.playlistName.lowercase().trim()
                                val cleanUrl = source.streamUrl.lowercase().trim()
                                val isInvalidOpcionNumber = cleanName.matches("""opci[oó]n\s+\d+""".toRegex())
                                val isInvalidFastream = cleanUrl == "https://fastream.to/embed" || cleanUrl == "https://fastream.to/embed/" || cleanUrl.isEmpty()
                                isInvalidOpcionNumber || isInvalidFastream
                            }
                            
                            if (finalSourcesList.isNotEmpty()) {
                                newUiChannels.add(
                                    UiChannel(
                                        name = title,
                                        groupTitle = "EXTENSION",
                                        logoUrl = "",
                                        primaryStreamUrl = finalSourcesList[0].streamUrl,
                                        sources = finalSourcesList,
                                        currentProgram = "Reproducir Capítulo",
                                        currentProgramDescription = "Contenido desde la extensión",
                                        rating = "8.5",
                                        year = "2026",
                                        synopsis = "$title. Enlaces multi-idioma listos para reproducir.",
                                        originalGroup = "Capítulos Directos",
                                        isEmbedText = true
                                    )
                                )
                            }
                        }
                        
                        _scrapedItems.value = newUiChannels
                        
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            _activeCategory.value = "EXTENSION"
                            _currentScreen.value = "MAIN"
                        }
                        
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                    
                    val jsonResponse = "{\"status\":\"ok\",\"message\":\"scraped_received\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                } else {
                    val jsonResponse = "{\"status\":\"error\",\"message\":\"not_found\"}"
                    val responseBytes = jsonResponse.toByteArray()
                    val response = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-Length: ${responseBytes.size}\r\n" +
                            "\r\n"
                    out.write(response.toByteArray())
                    out.write(responseBytes)
                    out.flush()
                }
                
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                try { socket.close() } catch (ex: Exception) {}
            }
        }
    }

    // Factory Class
    companion object {
        fun provideFactory(
            application: Application,
            repository: TvRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TvViewModel(application, repository) as T
            }
        }
    }
}
