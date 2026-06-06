package com.maratonTv.data.repository

import android.content.Context
import com.maratonTv.data.local.dao.TvDao
import com.maratonTv.data.local.entities.*
import com.maratonTv.data.model.*
import com.maratonTv.data.remote.parsers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext

class TvRepository(
    private val tvDao: TvDao,
    private val context: Context
) {
    val okHttpClient: OkHttpClient = createUnsafeOkHttpClient()

    private val uiChannelsCacheLock = Any()
    @Volatile
    private var cachedUiChannels: List<UiChannel>? = null
    private var cachedDbChannelsSize: Int = -1
    private var cachedDbChannelsFirstUrl: String? = null
    private var cachedFavoritesHash: Int = -1
    private var cachedPlaylistsHash: Int = -1

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            OkHttpClient.Builder()
                .cache(okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), 100 * 1024 * 1024L))
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    var request = chain.request()
                    
                    // Sanitize incoming request url first
                    var originalUrl = request.url.toString()
                    if (originalUrl.startsWith("https://", ignoreCase = true)) {
                        if (originalUrl.contains(":80/") || originalUrl.contains(":8080/") || originalUrl.contains(":80?") || originalUrl.contains(":8080?")) {
                            originalUrl = originalUrl.replace("https://", "http://", ignoreCase = true)
                            request = request.newBuilder().url(originalUrl).build()
                        }
                    }
                    
                    var response = try {
                        chain.proceed(request)
                    } catch (e: Exception) {
                        if (isSslOrTlsError(e) && request.url.isHttps) {
                            val fallbackUrl = request.url.toString().replace("https://", "http://", ignoreCase = true)
                            val fallbackRequest = request.newBuilder().url(fallbackUrl).build()
                            chain.proceed(fallbackRequest)
                        } else {
                            throw e
                        }
                    }
                    
                    // Handle manual follow of redirects
                    var redirectCount = 0
                    while ((response.code == 301 || response.code == 302 || response.code == 303 || response.code == 307 || response.code == 308) && redirectCount < 5) {
                        redirectCount++
                        val location = response.header("Location") ?: break
                        response.close()
                        
                        val resolvedUrl = try {
                            request.url.resolve(location)?.toString() ?: location
                        } catch (e: Exception) {
                            location
                        }
                        
                        var sanitizedUrl = resolvedUrl
                        if (sanitizedUrl.startsWith("https://", ignoreCase = true)) {
                            if (sanitizedUrl.contains(":80/") || sanitizedUrl.contains(":8080/") || sanitizedUrl.contains(":80?") || sanitizedUrl.contains(":8080?")) {
                                sanitizedUrl = sanitizedUrl.replace("https://", "http://", ignoreCase = true)
                            }
                        }
                        
                        val redirectRequest = request.newBuilder().url(sanitizedUrl).build()
                        response = try {
                            chain.proceed(redirectRequest)
                        } catch (e: Exception) {
                            if (isSslOrTlsError(e) && redirectRequest.url.isHttps) {
                                val fallbackUrl = redirectRequest.url.toString().replace("https://", "http://", ignoreCase = true)
                                val fallbackRequest = redirectRequest.newBuilder().url(fallbackUrl).build()
                                chain.proceed(fallbackRequest)
                            } else {
                                throw e
                            }
                        }
                    }
                    response
                }
                .build()
        } catch (e: Exception) {
            OkHttpClient.Builder()
                .cache(okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), 100 * 1024 * 1024L))
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }

    private fun isSslOrTlsError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return e is javax.net.ssl.SSLException || 
               e is javax.net.ssl.SSLHandshakeException || 
               e is javax.net.ssl.SSLPeerUnverifiedException || 
               e is javax.net.ssl.SSLProtocolException ||
               message.contains("ssl") || 
               message.contains("tls") || 
               message.contains("handshake") || 
               message.contains("certignore") || 
               message.contains("certificate") ||
               message.contains("tls packet header")
    }

    // Exposed Flows
    val playlists: Flow<List<Playlist>> = tvDao.getAllPlaylists().map { list ->
        list.filter { it.id != 9999 }
    }.catch { e ->
        e.printStackTrace()
        emit(emptyList())
    }
    val profiles: Flow<List<Profile>> = tvDao.getAllProfiles().catch { e ->
        e.printStackTrace()
        emit(emptyList())
    }

    // Get profiles with default backup
    suspend fun checkAndPrepopulateProfiles() = withContext(Dispatchers.IO) {
        try {
            val count = tvDao.getAllProfiles().first().size
            if (count == 0) {
                tvDao.insertProfile(Profile(name = "Blooders Admin", avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150"))
                tvDao.insertProfile(Profile(name = "Papá", avatarUrl = "https://images.unsplash.com/photo-1560250097-0b93528c311a?w=150"))
                tvDao.insertProfile(Profile(name = "Niños", avatarUrl = "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04?w=150"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Get playlists with default backup if empty on startup
    suspend fun checkAndPrepopulatePlaylists() = withContext(Dispatchers.IO) {
        val playlists = tvDao.getAllPlaylists().first()
        
        // Active purge of RedWorld playlist from the database as requested by the user
        val rwUrl = "http://redworld.pro:8880/get.php?username=Paty0600&password=F3vLcb95wqKY&type=m3u_plus"
        playlists.forEach { pl ->
            if (pl.url == rwUrl || pl.url.trim() == rwUrl || pl.name.contains("RedWorld", ignoreCase = true)) {
                try {
                    deletePlaylist(pl.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Active purge of integrated pluto list (playlistId = 9999 & 8888) as requested by the user
        try {
            tvDao.deletePlaylistById(9999)
            tvDao.deleteChannelsByPlaylist(9999)
            tvDao.deletePlaylistById(8888)
            tvDao.deleteChannelsByPlaylist(8888)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val remainingPlaylists = tvDao.getAllPlaylists().first()
        if (remainingPlaylists.isEmpty()) {
            insertPreloadedPlaylist()
        }
    }

    // Combine channels with favorites and playlists
    fun getUiChannels(profileId: Int): Flow<List<UiChannel>> {
        return combine(
            tvDao.getAllChannels(),
            tvDao.getFavoritesForProfile(profileId),
            tvDao.getAllPlaylists()
        ) { dbChannels, favorites, playlistList ->
            val filteredDb = dbChannels.filter { it.playlistId >= 0 || it.playlistId == -profileId }
            buildUiChannels(filteredDb, favorites, playlistList)
        }
    }

    private fun mapGroupToCategory(group: String, name: String): String {
        val staticSeriesRegex = "(?i)(?:S[0-9]+|T[0-9]+|E[0-9]+|TEMPORADA\\s*[0-9]+|CAPITULO\\s*[0-9]+|[0-9]+x[0-9]+|CAP\\.?\\s*[0-9]+|EP\\.?\\s*[0-9]+)".toRegex()
        val staticYearRegex = "(?i)\\b(20[0-2][0-9]|19[8-9][0-9])\\b".toRegex()
        
        val upperGroup = group.uppercase()
        val upperName = name.uppercase()

        // 1. Series detection markers in Name:
        // S01E01, T01E01, Temporada 1, Cap. 1, Episode, 1x01, etc.
        val hasSeriesMarkerInName = staticSeriesRegex.containsMatchIn(name)
        val isSeriesGroup = upperGroup.contains("SERIE") || upperGroup.contains("SHOW") || upperGroup.contains("TEMPORADA")

        if (hasSeriesMarkerInName || isSeriesGroup) {
            return "SERIES"
        }

        // 2. Movie detection markers in Name or Group:
        val movieKeywords = listOf("1080P", "720P", "BLURAY", "BDRIP", "DVDRIP", "HDRIP", "CAMRIP", "H264", "H265", "HEVC", "X264", "X265", "COMPLETE", "MULTILANG", "DUAL", "LATINO", "CASTELLANO", "SUB", "UNRATED", "DIRECTORS CUT")
        val isMovieGroup = upperGroup.contains("MOVIE") || upperGroup.contains("PELI") || upperGroup.contains("CINE") || upperGroup.contains("FILM")
        val hasMovieMarkerInName = movieKeywords.any { upperName.contains(it) } || staticYearRegex.containsMatchIn(upperName) // Years like 2023, 1999

        if (isMovieGroup || hasMovieMarkerInName) {
            val isLiveTvKeyword = upperName.contains("CANAL") || upperName.contains("TV") || upperName.contains("LIVE") || upperName.contains("NOTICIAS") || upperName.contains("DEPORTES")
            if (!isLiveTvKeyword) {
                return "PELICULA"
            }
        }

        // 3. Keep kids & anime groups or refine them
        if (upperGroup.contains("KIDS") || upperGroup.contains("NINO") || upperGroup.contains("INFANTIL") || upperGroup.contains("CARTOON")) {
            return "KIDS"
        }
        if (upperGroup.contains("ANIME") || upperGroup.contains("MANGA")) {
            return "ANIME"
        }

        // 4. Fallback groups
        if (upperGroup.contains("DESTACADO") || upperGroup.contains("FAV") || upperGroup.contains("POPULAR")) {
            return "TV"
        }
        if (upperGroup.contains("EXPLORA") || upperGroup.contains("NEWS") || upperGroup.contains("DOCUMENTAL")) {
            return "TV"
        }

        // Default: TV or original group if valid
        return if (group == "TV" || group == "PELICULA" || group == "SERIES" || group == "KIDS" || group == "ANIME") {
            group
        } else {
            "TV"
        }
    }



    private fun buildUiChannels(
        dbChannels: List<DbChannel>,
        favorites: List<Favorite>,
        playlistList: List<Playlist>
    ): List<UiChannel> {
        val currentDbSize = dbChannels.size
        val currentDbFirstUrl = dbChannels.firstOrNull()?.streamUrl
        val currentFavsHash = favorites.hashCode()
        val currentPlaylistsHash = playlistList.hashCode()

        synchronized(uiChannelsCacheLock) {
            val cached = cachedUiChannels
            if (cached != null &&
                cachedDbChannelsSize == currentDbSize &&
                cachedDbChannelsFirstUrl == currentDbFirstUrl &&
                cachedFavoritesHash == currentFavsHash &&
                cachedPlaylistsHash == currentPlaylistsHash
            ) {
                return cached
            }
        }

        val playlistMap = playlistList.associateBy { it.id }
        
        val favoritesSet = HashSet<String>(favorites.size)
        for (i in 0 until favorites.size) {
            favoritesSet.add(favorites[i].streamUrl)
        }

        // Group channels by name (case-insensitive) using a pre-sized HashMap for maximum efficiency
        val grouped = java.util.LinkedHashMap<String, ArrayList<DbChannel>>(dbChannels.size / 2)
        for (i in 0 until dbChannels.size) {
            val ch = dbChannels[i]
            val key = ch.name.lowercase()
            var list = grouped[key]
            if (list == null) {
                list = ArrayList()
                grouped[key] = list
            }
            list.add(ch)
        }

        val result = ArrayList<UiChannel>(grouped.size)
        for ((_, channelsWithSameName) in grouped) {
            var bestRep = channelsWithSameName[0]
            var hasLogo = bestRep.logoUrl.isNotEmpty()
            for (i in 1 until channelsWithSameName.size) {
                val ch = channelsWithSameName[i]
                if (ch.logoUrl.isNotEmpty() && !hasLogo) {
                    bestRep = ch
                    hasLogo = true
                }
            }
            val representative = bestRep

            val sources = ArrayList<ChannelSource>(channelsWithSameName.size)
            for (i in 0 until channelsWithSameName.size) {
                val ch = channelsWithSameName[i]
                val plName = playlistMap[ch.playlistId]?.name ?: "Lista ${ch.playlistId}"
                sources.add(ChannelSource(playlistName = plName, streamUrl = ch.streamUrl))
            }

            var hasFavorite = false
            for (i in 0 until channelsWithSameName.size) {
                if (favoritesSet.contains(channelsWithSameName[i].streamUrl)) {
                    hasFavorite = true
                    break
                }
            }

            val finalGroup = mapGroupToCategory(representative.groupTitle, representative.name)
            val epgInfo = getDummyEpgForChannel(representative.name, finalGroup)

            val mappedOriginalGroup = if (representative.originalGroup.isEmpty()) "TV" else representative.originalGroup

            result.add(
                UiChannel(
                    name = representative.name,
                    groupTitle = finalGroup,
                    logoUrl = if (representative.logoUrl.isEmpty()) getFallbackLogo(finalGroup) else representative.logoUrl,
                    primaryStreamUrl = representative.streamUrl,
                    sources = sources,
                    currentProgram = epgInfo.programTitle,
                    currentProgramDescription = epgInfo.programDesc,
                    startEndText = epgInfo.timeText,
                    programProgress = epgInfo.progress,
                    isFavorite = hasFavorite,
                    rating = epgInfo.rating,
                    year = epgInfo.year,
                    director = epgInfo.director,
                    actors = epgInfo.actors,
                    synopsis = epgInfo.synopsis,
                    originalGroup = mappedOriginalGroup,
                    isEmbedText = representative.isEmbedText,
                    adBlockerEnabled = representative.adBlockerEnabled
                )
            )
        }

        synchronized(uiChannelsCacheLock) {
            cachedUiChannels = result
            cachedDbChannelsSize = currentDbSize
            cachedDbChannelsFirstUrl = currentDbFirstUrl
            cachedFavoritesHash = currentFavsHash
            cachedPlaylistsHash = currentPlaylistsHash
        }

        return result
    }

    private fun getFallbackLogo(category: String): String {
        return when (category) {
            "TV" -> "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=150"
            "PELICULA" -> "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=150"
            "SERIES" -> "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=150"
            "KIDS" -> "https://images.unsplash.com/photo-1485546246426-74dc88dec4d9?w=150"
            "ANIME" -> "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=150"
            else -> "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150"
        }
    }

    // --- Profile Management ---
    suspend fun addProfile(name: String, avatarUrl: String, pinCode: String? = null, customM3uUrl: String? = null, customEpgUrl: String? = null) {
        tvDao.insertProfile(Profile(name = name, avatarUrl = avatarUrl, pinCode = pinCode, customM3uUrl = customM3uUrl, customEpgUrl = customEpgUrl))
    }

    suspend fun updateProfile(profile: Profile) {
        tvDao.insertProfile(profile)
    }

    suspend fun deleteProfile(profile: Profile) {
        tvDao.deleteProfile(profile)
        tvDao.deleteChannelsByPlaylist(-profile.id)
    }

    suspend fun syncProfileM3u(profile: Profile) = withContext(Dispatchers.IO) {
        val m3uUrl = profile.customM3uUrl ?: return@withContext
        if (m3uUrl.isBlank()) {
            tvDao.deleteChannelsByPlaylist(-profile.id)
            return@withContext
        }
        try {
            val playlistId = -profile.id
            val parsed = parseAndSaveM3uFromUrl(playlistId, m3uUrl, "GENERAL")
            if (parsed.isNotEmpty()) {
                tvDao.deleteChannelsByPlaylist(playlistId)
                tvDao.insertChannels(parsed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Playlist & parsing Management ---
    suspend fun addPlaylist(name: String, url: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC") = withContext(Dispatchers.IO) {
        // Direct M3U url download. Credentials verification is performed directly from the response of the main get.php / playlist url.
        val playlistId = tvDao.insertPlaylist(Playlist(name = name, url = url, classification = classification, playbackMode = playbackMode)).toInt()
        try {
            var parsed = parseAndSaveM3uFromUrl(playlistId, url, classification)
            
            // Accept single channel M3U8 streaming links (not an actual playlist) and wrap them into a playable guide item
            if (parsed.isEmpty()) {
                val urlLower = url.lowercase(java.util.Locale.ROOT)
                if (urlLower.contains(".m3u8") || urlLower.contains(".ts") || urlLower.contains(".mp4") || urlLower.contains(".mkv") || urlLower.contains("/play/") || urlLower.contains("/stream/")) {
                    val finalGroup = when (classification.uppercase()) {
                        "PELICULAS" -> "PELICULA"
                        "TV" -> "TV"
                        "SERIES" -> "SERIES"
                        "KIDS" -> "KIDS"
                        else -> "TV"
                    }
                    parsed = listOf(
                        DbChannel(
                            streamUrl = url,
                            name = name,
                            groupTitle = finalGroup,
                            logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
                            playlistId = playlistId,
                            originalGroup = "TV"
                        )
                    )
                }
            }

            if (parsed.isNotEmpty()) {
                tvDao.insertChannels(parsed)
            } else {
                tvDao.deletePlaylistById(playlistId)
                throw IOException("No se encontraron canales en el enlace proporcionado.")
            }
        } catch (e: Exception) {
            tvDao.deletePlaylistById(playlistId)
            throw e
        }
    }

    suspend fun addLocalPlaylist(name: String, content: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC") = withContext(Dispatchers.IO) {
        val playlistId = tvDao.insertPlaylist(Playlist(name = name, url = "local://archivo_subido", classification = classification, playbackMode = playbackMode)).toInt()
        val parsed = M3uParser.parse(content, playlistId, classification)
        if (parsed.isNotEmpty()) {
            tvDao.insertChannels(parsed)
        }
    }

    suspend fun addLocalTxtPlaylist(name: String, content: String, classification: String = "GENERAL", playbackMode: String = "AUTOMATIC") = withContext(Dispatchers.IO) {
        val playlistId = tvDao.insertPlaylist(Playlist(name = name, url = "local://archivo_txt_subido", classification = classification, playbackMode = playbackMode)).toInt()
        val parsed = TxtParser.parse(content, playlistId, classification)
        if (parsed.isNotEmpty()) {
            tvDao.insertChannels(parsed)
        } else {
            tvDao.deletePlaylistById(playlistId)
            throw java.io.IOException("No se encontraron enlaces válidos, archivos M3U/M3U8 ni códigos embed en el archivo de texto.")
        }
    }

    suspend fun addDirectStreamChannel(
        name: String,
        streamUrl: String,
        category: String,
        isEmbedText: Boolean = false,
        adBlockerEnabled: Boolean = false
    ) = withContext(Dispatchers.IO) {
        var manualPlaylist = tvDao.getAllPlaylists().first().find { it.url == "manual://links" }
        val playlistId = if (manualPlaylist == null) {
            tvDao.insertPlaylist(Playlist(name = "Enlaces Directos", url = "manual://links")).toInt()
        } else {
            manualPlaylist.id
        }

        val dbChannel = DbChannel(
            streamUrl = streamUrl,
            name = name,
            groupTitle = if (category.isEmpty()) "TV" else category,
            logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
            playlistId = playlistId,
            originalGroup = if (category.isEmpty()) "TV" else category,
            isEmbedText = isEmbedText,
            adBlockerEnabled = adBlockerEnabled
        )
        tvDao.insertChannels(listOf(dbChannel))
    }

    suspend fun reloadAllPlaylists() = withContext(Dispatchers.IO) {
        val allPl = tvDao.getAllPlaylists().first()
        for (pl in allPl) {
            val url = pl.url
            if (url.startsWith("http://") || url.startsWith("https://")) {
                try {
                    var parsed = parseAndSaveM3uFromUrl(pl.id, url, pl.classification)
                    
                    if (parsed.isEmpty()) {
                        val urlLower = url.lowercase(java.util.Locale.ROOT)
                        if (urlLower.contains(".m3u8") || urlLower.contains(".ts") || urlLower.contains(".mp4") || urlLower.contains(".mkv") || urlLower.contains("/play/") || urlLower.contains("/stream/")) {
                            val finalGroup = when (pl.classification.uppercase()) {
                                "PELICULAS" -> "PELICULA"
                                "TV" -> "TV"
                                "SERIES" -> "SERIES"
                                "KIDS" -> "KIDS"
                                else -> "TV"
                            }
                            parsed = listOf(
                                DbChannel(
                                    streamUrl = url,
                                    name = pl.name,
                                    groupTitle = finalGroup,
                                    logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
                                    playlistId = pl.id,
                                    originalGroup = "TV"
                                )
                            )
                        }
                    }

                    if (parsed.isNotEmpty()) {
                        tvDao.deleteChannelsByPlaylist(pl.id)
                        tvDao.insertChannels(parsed)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun updatePlaylistClassification(playlistId: Int, classification: String) = withContext(Dispatchers.IO) {
        val playlists = tvDao.getAllPlaylists().first()
        val pl = playlists.find { it.id == playlistId } ?: return@withContext
        val updatedPl = pl.copy(classification = classification)
        tvDao.insertPlaylist(updatedPl)
        
        val allChannelsResult = tvDao.getAllChannels().first()
        val channelsToUpdate = allChannelsResult.filter { it.playlistId == playlistId }
        if (channelsToUpdate.isNotEmpty()) {
            val updatedChannels = channelsToUpdate.map { ch ->
                val finalGroup = when (classification.uppercase()) {
                    "PELICULAS" -> "PELICULA"
                    "TV" -> "TV"
                    "SERIES" -> "SERIES"
                    "KIDS" -> "KIDS"
                    "PELICULAS_SERIES" -> {
                        val nameLower = ch.name.lowercase()
                        val groupLower = ch.originalGroup.lowercase()
                        if (nameLower.contains("temporada") || nameLower.contains("capitulo") || nameLower.contains("serie") || nameLower.contains("episodio") || nameLower.contains("s01") || nameLower.contains("s02") || nameLower.contains("ep ") ||
                            groupLower.contains("serie") || groupLower.contains("show") || groupLower.contains("temporada")
                        ) {
                            "SERIES"
                        } else {
                            "PELICULA"
                        }
                    }
                    else -> M3uParser.mapGroupToCategory(ch.originalGroup)
                }
                ch.copy(groupTitle = finalGroup)
            }
            tvDao.insertChannels(updatedChannels)
        }
    }

    private suspend fun parseAndSaveM3uFromUrl(playlistId: Int, url: String, classification: String): List<DbChannel> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw IOException("Error de autenticación: El servidor IPTV rechazó tus credenciales (Código ${response.code}).")
                }
                if (!response.isSuccessful) {
                    throw IOException("Error al recibir la lista (Código ${response.code})")
                }
                
                val bodyStream = response.body?.byteStream() ?: throw IOException("Cuerpo de respuesta vacío.")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(bodyStream, java.nio.charset.StandardCharsets.UTF_8))
                
                // Read the first 4KB to check for any authenticating errors
                reader.mark(4096)
                val buffer = CharArray(2048)
                val readChars = reader.read(buffer, 0, 2048)
                val preview = if (readChars > 0) String(buffer, 0, readChars) else ""
                val previewLower = preview.trim().lowercase()
                
                if (previewLower.isNotEmpty()) {
                    if (previewLower == "access denied" || previewLower.contains("invalid username") || previewLower.contains("auth failed") || previewLower.contains("incorrect password")) {
                        throw IOException("Error de autenticación: Usuario o contraseña incorrectos.")
                    }
                    if (previewLower.contains("\"auth\":0") || previewLower.contains("\"auth\": 0")) {
                        throw IOException("Error de autenticación: Usuario o contraseña incorrectos.")
                    }
                }
                
                // Reset the reader back to the start of the stream
                try {
                    reader.reset()
                } catch (e: Exception) {
                    // Fallback
                }
                
                M3uParser.parse(reader, playlistId, classification)
            }
        }
    }

    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        tvDao.deletePlaylistById(playlistId)
        tvDao.deleteChannelsByPlaylist(playlistId)
    }

    suspend fun getPlaylistPlaybackModeForStream(streamUrl: String): String = withContext(Dispatchers.IO) {
        val channels = tvDao.getAllChannels().first()
        val channel = channels.find { it.streamUrl == streamUrl } ?: return@withContext "AUTOMATIC"
        val playlists = tvDao.getAllPlaylists().first()
        val playlist = playlists.find { it.id == channel.playlistId } ?: return@withContext "AUTOMATIC"
        playlist.playbackMode
    }

    suspend fun updatePlaylistPlaybackMode(playlistId: Int, playbackMode: String) = withContext(Dispatchers.IO) {
        val playlists = tvDao.getAllPlaylists().first()
        val pl = playlists.find { it.id == playlistId } ?: return@withContext
        val updatedPl = pl.copy(playbackMode = playbackMode)
        tvDao.insertPlaylist(updatedPl)
    }

    // --- Favorite Management ---
    suspend fun toggleFavorite(profileId: Int, channel: UiChannel) {
        withContext(Dispatchers.IO) {
            // Check if favorited currently
            if (channel.isFavorite) {
                // Remove all sources from favorites
                channel.sources.forEach { source ->
                    tvDao.deleteFavoriteByKeys(profileId, source.streamUrl)
                }
            } else {
                // Add the primary stream URL to favorites
                tvDao.insertFavorite(Favorite(profileId = profileId, streamUrl = channel.primaryStreamUrl))
            }
        }
    }

    // --- Preloading initial playlists ---
    private suspend fun insertPreloadedPlaylist() = withContext(Dispatchers.IO) {
        val defaultUrl = "https://www.m3u.cl/lista/MX.m3u"
        val playlistId = tvDao.insertPlaylist(Playlist(name = "Lista por Defecto (MX)", url = defaultUrl)).toInt()

        try {
            val parsed = parseAndSaveM3uFromUrl(playlistId, defaultUrl, "GENERAL")
            if (parsed.isNotEmpty()) {
                tvDao.insertChannels(parsed)
                return@withContext
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val defaultChannels = listOf(
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                name = "A&E HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "A&E HD", // Source 2 for A&E HD to showcase Opciones de Reproductor
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                name = "A&E HD", // Source 3 for A&E HD to showcase Opciones de Reproductor
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "6 A&E FHD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                name = "7 AMC HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1560169897-fc0cdbdfa4d5?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "8 AMC BO HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1550759786422-c6035ce3168b?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "9 NetFlix Eventos",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "10 AXN HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                name = "10 AXN HD", // Source 2 for AXN HD to showcase multi links
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                name = "11 AXN WHITE HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                name = "12 AXN ESPAÑA HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1598257006458-087169a1f08d?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                name = "13 Adult Swim",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                name = "Meridiano TV Deportes",
                groupTitle = "Deportes",
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                name = "TLT Deportes HD",
                groupTitle = "Deportes",
                logoUrl = "https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "Venevisión Premium",
                groupTitle = "Venezuela",
                logoUrl = "https://images.unsplash.com/photo-1485546246426-74dc88dec4d9?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                name = "Televen FHD Col",
                groupTitle = "Venezuela",
                logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "VTV Canal 8 Noticias",
                groupTitle = "Venezuela",
                logoUrl = "https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "🔞 Playboy TV HD",
                groupTitle = "18+",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "🔞 Venus Especial VIP",
                groupTitle = "18+",
                logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=150",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "Blooders TV HD",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=200",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                name = "Te van a matar",
                groupTitle = "PELICULA",
                logoUrl = "https://images.unsplash.com/photo-1626814026160-2237a95fc5a0?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                name = "The Punisher",
                groupTitle = "PELICULA",
                logoUrl = "https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Jack Ryan",
                groupTitle = "PELICULA",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "The Boys T5-E1",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "The Boys T5-E2",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "The Boys T5-E3",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "The Boys T5-E4",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "The Boys T5-E5",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "The Boys T5-E6",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "The Boys T5-E7",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                name = "The Boys T5-E8",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1540759786422-c6035ce3168b?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Breaking Bad *back-tem S01 E01",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Breaking Bad *back-tem S01 E02",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Breaking Bad *back-tem S01 E03",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Breaking Bad *back-tem S01 E04",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Breaking Bad *back-tem S01 E05",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Breaking Bad *back-tem S01 E06",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Better Call Saul *back-tem S01 E01",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Better Call Saul *back-tem S01 E02",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                name = "Better Call Saul *back-tem S01 E03",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                name = "Better Call Saul *back-tem S01 E04",
                groupTitle = "SERIES",
                logoUrl = "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                name = "Action Space 4K",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                name = "Kids Cartoon Zone",
                groupTitle = "KIDS",
                logoUrl = "https://images.unsplash.com/photo-1560169897-fc0cdbdfa4d5?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                name = "Anime Central",
                groupTitle = "ANIME",
                logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=250",
                playlistId = playlistId
            ),
            DbChannel(
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                name = "Explora Historia",
                groupTitle = "TV",
                logoUrl = "https://images.unsplash.com/photo-1461360370896-922624d12aa1?w=250",
                playlistId = playlistId
            )
        )
        tvDao.insertChannels(defaultChannels)
    }

    // --- Dynamic EPG Generator helper ---
    data class DummyEpg(
        val programTitle: String,
        val programDesc: String,
        val timeText: String,
        val progress: Float,
        val rating: String,
        val year: String,
        val director: String,
        val actors: String,
        val synopsis: String
    )

    private fun getDummyEpgForChannel(name: String, group: String): DummyEpg {
        val now = System.currentTimeMillis()
        val currentHourMillis = 3600000L
        val hourProgressFraction = (now % currentHourMillis).toFloat() / currentHourMillis

        val nameUpper = name.uppercase()
        if (nameUpper.contains("BREAKING BAD")) {
            return DummyEpg(
                programTitle = "Breaking Bad",
                programDesc = "La obra maestra de Vince Gilligan",
                timeText = "Serie Completa",
                progress = 0.5f,
                rating = "9.5",
                year = "2008",
                director = "Vince Gilligan",
                actors = "Bryan Cranston, Aaron Paul, Bob Odenkirk, Anna Gunn, Dean Norris",
                synopsis = "Un profesor de química con cáncer terminal se asocia con un antiguo alumno para asegurar el futuro financiero de su familia fabricando y distribuyendo metanfetamina."
            )
        }
        if (nameUpper.contains("BETTER CALL SAUL")) {
            return DummyEpg(
                programTitle = "Better Call Saul",
                programDesc = "El origen del infame Saul Goodman",
                timeText = "Serie Completa",
                progress = 0.5f,
                rating = "9.0",
                year = "2015",
                director = "Vince Gilligan, Peter Gould",
                actors = "Bob Odenkirk, Jonathan Banks, Rhea Seehorn, Giancarlo Esposito",
                synopsis = "Jimmy McGill lucha por triunfar frente a la adversidad para convertirse en el infame abogado defensor penal Saul Goodman."
            )
        }

        // Generate names of programs based on group
        return when (group) {
            "TV" -> DummyEpg(
                programTitle = "Señal en Vivo",
                programDesc = "Disfruta de la mejor programación en vivo y en directo.",
                timeText = "Transmisión 24/7",
                progress = hourProgressFraction,
                rating = "8.0",
                year = "2026",
                director = "TV en vivo",
                actors = "Varios Presentadores",
                synopsis = "Transmisión en directo con señal optimizada y en alta fidelidad."
            )
            "DESTACADOS" -> DummyEpg(
                programTitle = "Mega Event Eventos de Deportes Premium",
                programDesc = "Los partidos y competiciones más importantes del mundo con los comentarios destacados.",
                timeText = "09:30 PM - 11:30 PM",
                progress = 0.65f,
                rating = "9.0",
                year = "2026",
                director = "Federación Blooders",
                actors = "Leo Messi, Cristiano Ronaldo, Kylian Mbappé",
                synopsis = "Toda la pasión internacional deportiva transmitida en vivo a sus pantallas con bitrate óptimo optimizado para 4K."
            )
            "PELICULA" -> DummyEpg(
                programTitle = "The Punisher: One Last Kill",
                programDesc = "El justiciero regresa para limpiar las calles en una de las entregas más intensas y emocionantes del cine de acción.",
                timeText = "11:00 PM - 01:00 AM",
                progress = 0.12f,
                rating = "8.4",
                year = "2024",
                director = "Frank Castle",
                actors = "Jon Bernthal, Ben Barnes",
                synopsis = "Un justiciero despiadado busca venganza por el asesinato de su familia, desatando una cacería implacable contra el crimen organizado global."
            )
            "SERIES" -> DummyEpg(
                programTitle = "The Boys - Temporada 4",
                programDesc = "Los súper héroes son expuestos como villanos corruptos por un grupo de vigilantes sin escrúpulos.",
                timeText = "08:15 PM - 09:15 PM",
                progress = 0.45f,
                rating = "8.9",
                year = "2024",
                director = "Eric Kripke",
                actors = "Karl Urban, Jack Quaid, Antony Starr",
                synopsis = "En una sociedad donde los súper héroes abusan de sus poderes bajo una fachada corporativa, una brigada de humanos sin poderes se une para combatirlos."
            )
            "KIDS" -> DummyEpg(
                programTitle = "Aventura de Monstruos Amigables",
                programDesc = "Dibujos animados divertidos, coloridos and educativos para disfrutar con toda la familia.",
                timeText = "07:00 PM - 08:30 PM",
                progress = 0.8f,
                rating = "9.2",
                year = "2025",
                director = "Kiyoshi Sato",
                actors = "Doraemon, Nobita, Shizuka",
                synopsis = "Las fantásticas travesías animadas que educan y entretienen a los más pequeños del hogar con risas y diversión garantizada."
            )
            "ANIME" -> DummyEpg(
                programTitle = "Naruto Shippuden: El Camino del Héroe",
                programDesc = "El joven ninja luchará contra las fuerzas Akatsuki para proteger a su aldea y conseguir su sueño.",
                timeText = "10:30 PM - 11:30 PM",
                progress = 0.2f,
                rating = "8.6",
                year = "2023",
                director = "Hayao Miyazaki",
                actors = "Naruto Uzumaki, Sasuke Uchiha",
                synopsis = "Aventuras de ninjas en el épico viaje de Naruto para ser reconocido por toda su aldea shinobi, superando batallas y lazos rotos."
            )
            else -> DummyEpg(
                programTitle = "Exploración Cósmica Salvaje",
                programDesc = "Misterios del universo, los agujeros negros y la fascinante vida interplanetaria.",
                timeText = "11:00 PM - 11:55 PM",
                progress = 0.72f,
                rating = "7.9",
                year = "2025",
                director = "Albert Einstein",
                actors = "Neil deGrasse Tyson",
                synopsis = "Un asombroso viaje a través del cosmos revelando los mayores secretos físicos y estelares descubiertos por la ciencia moderna."
            )
        }
    }

    suspend fun exportBackupString(): String = withContext(Dispatchers.IO) {
        val json = org.json.JSONObject()
        json.put("version", 1)

        // 1. Playlists
        val playlistList = tvDao.getAllPlaylists().first()
        val playlistsArray = org.json.JSONArray()
        for (pl in playlistList) {
            val plObj = org.json.JSONObject()
            plObj.put("id", pl.id)
            plObj.put("name", pl.name)
            plObj.put("url", pl.url)
            plObj.put("classification", pl.classification)
            plObj.put("playback_mode", pl.playbackMode)
            playlistsArray.put(plObj)
        }
        json.put("playlists", playlistsArray)

        // 2. Profiles
        val profileList = tvDao.getAllProfiles().first()
        val profilesArray = org.json.JSONArray()
        for (prof in profileList) {
            val profObj = org.json.JSONObject()
            profObj.put("id", prof.id)
            profObj.put("name", prof.name)
            profObj.put("avatarUrl", prof.avatarUrl)
            profilesArray.put(profObj)
        }
        json.put("profiles", profilesArray)

        // 3. Channels
        val channelList = tvDao.getAllChannels().first()
        val channelsArray = org.json.JSONArray()
        for (ch in channelList) {
            val chObj = org.json.JSONObject()
            chObj.put("streamUrl", ch.streamUrl)
            chObj.put("name", ch.name)
            chObj.put("groupTitle", ch.groupTitle)
            chObj.put("logoUrl", ch.logoUrl)
            chObj.put("playlistId", ch.playlistId)
            channelsArray.put(chObj)
        }
        json.put("channels", channelsArray)

        // 4. Favorites
        val favoritesList = tvDao.getAllFavoritesDirect()
        val favsArray = org.json.JSONArray()
        for (fav in favoritesList) {
            val favObj = org.json.JSONObject()
            favObj.put("profileId", fav.profileId)
            favObj.put("streamUrl", fav.streamUrl)
            favsArray.put(favObj)
        }
        json.put("favorites", favsArray)

        json.toString(4) // pretty print
    }

    suspend fun importBackupString(jsonString: String) = withContext(Dispatchers.IO) {
        val json = org.json.JSONObject(jsonString)

        // 1. Clear current database data
        tvDao.clearAllPlaylists()
        tvDao.clearAllProfiles()
        tvDao.clearAllChannels()
        tvDao.clearAllFavorites()

        // 2. Import Playlists
        if (json.has("playlists")) {
            val playlistsArray = json.getJSONArray("playlists")
            for (i in 0 until playlistsArray.length()) {
                val plObj = playlistsArray.getJSONObject(i)
                val playlist = Playlist(
                    id = plObj.optInt("id", 0),
                    name = plObj.optString("name", "Restored List"),
                    url = plObj.optString("url", ""),
                    classification = plObj.optString("classification", "GENERAL"),
                    playbackMode = plObj.optString("playback_mode", "AUTOMATIC")
                )
                tvDao.insertPlaylist(playlist)
            }
        }

        // 3. Import Profiles
        if (json.has("profiles")) {
            val profilesArray = json.getJSONArray("profiles")
            for (i in 0 until profilesArray.length()) {
                val profObj = profilesArray.getJSONObject(i)
                val profile = Profile(
                    id = profObj.optInt("id", 0),
                    name = profObj.optString("name", "Restored Profile"),
                    avatarUrl = profObj.optString("avatarUrl", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150")
                )
                tvDao.insertProfile(profile)
            }
        }

        // 4. Import Channels
        if (json.has("channels")) {
            val channelsArray = json.getJSONArray("channels")
            val dbChannels = mutableListOf<DbChannel>()
            for (i in 0 until channelsArray.length()) {
                val chObj = channelsArray.getJSONObject(i)
                dbChannels.add(
                    DbChannel(
                        streamUrl = chObj.optString("streamUrl", ""),
                        name = chObj.optString("name", "Restored Channel"),
                        groupTitle = chObj.optString("groupTitle", "TV"),
                        logoUrl = chObj.optString("logoUrl", ""),
                        playlistId = chObj.optInt("playlistId", 0),
                        originalGroup = chObj.optString("originalGroup", chObj.optString("groupTitle", "TV"))
                    )
                )
            }
            if (dbChannels.isNotEmpty()) {
                tvDao.insertChannels(dbChannels)
            }
        }

        // 5. Import Favorites
        if (json.has("favorites")) {
            val favsArray = json.getJSONArray("favorites")
            for (i in 0 until favsArray.length()) {
                val favObj = favsArray.getJSONObject(i)
                val favorite = Favorite(
                    profileId = favObj.optInt("profileId", 0),
                    streamUrl = favObj.optString("streamUrl", "")
                )
                tvDao.insertFavorite(favorite)
            }
        }
    }

    suspend fun getPlaybackProgress(profileId: Int, streamUrl: String): PlaybackProgress? = withContext(Dispatchers.IO) {
        tvDao.getProgress(profileId, streamUrl)
    }

    suspend fun savePlaybackProgress(progress: PlaybackProgress) = withContext(Dispatchers.IO) {
        tvDao.saveProgress(progress)
    }

    suspend fun deletePlaybackProgress(profileId: Int, streamUrl: String) = withContext(Dispatchers.IO) {
        tvDao.deleteProgress(profileId, streamUrl)
    }

    suspend fun getCachedMetadata(cleanName: String): DbCachedMetadata? = withContext(Dispatchers.IO) {
        tvDao.getCachedMetadata(cleanName)
    }

    suspend fun saveCachedMetadata(metadata: DbCachedMetadata) = withContext(Dispatchers.IO) {
        tvDao.insertCachedMetadata(metadata)
    }

    suspend fun clearAllCachedMetadata() = withContext(Dispatchers.IO) {
        tvDao.clearAllCachedMetadata()
    }
}
