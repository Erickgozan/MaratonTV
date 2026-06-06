package com.maratonTv.data.repository

import android.net.Uri
import android.util.LruCache
import com.maratonTv.data.local.entities.DbCachedMetadata
import com.maratonTv.data.model.ImdbDetails
import com.maratonTv.data.model.UiChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class MediaMetadataRepository(
    private val tvRepository: TvRepository,
    private val okHttpClient: OkHttpClient
) {
    private val imdbCache = LruCache<String, ImdbDetails>(500)

    suspend fun fetchMetadata(channel: UiChannel, tmdbApiKey: String, source: String): ImdbDetails = withContext(Dispatchers.IO) {
        val cleanName = cleanMovieNameForSearch(channel.name)

        val cached = imdbCache.get(cleanName)
        if (cached != null) return@withContext cached

        // 1. Check DB Cache
        try {
            val dbCached = tvRepository.getCachedMetadata(cleanName)
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
                return@withContext details
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch from External API
        var result: ImdbDetails? = null
        try {
            if (source == "TMDB" && tmdbApiKey.trim().isNotEmpty()) {
                result = fetchFromTmdb(cleanName, tmdbApiKey, channel)
            }

            if (result == null) {
                result = fetchFromImdb(cleanName, channel, source)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            result = createFallbackDetails(cleanName, channel, source)
        }

        val finalResult = result ?: createFallbackDetails(cleanName, channel, source)
        
        imdbCache.put(cleanName, finalResult)
        persistMetadata(cleanName, finalResult)

        return@withContext finalResult
    }

    private fun fetchFromTmdb(cleanName: String, apiKey: String, channel: UiChannel): ImdbDetails? {
        try {
            val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=${Uri.encode(cleanName)}&language=es-ES"
            val request = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: ""
                val jsonObj = JSONObject(jsonStr)
                val results = jsonObj.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    var bestMatch: JSONObject? = null
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val mediaType = item.optString("media_type")
                        if (mediaType == "movie" || mediaType == "tv") {
                            bestMatch = item
                            break
                        }
                    }
                    if (bestMatch == null) bestMatch = results.getJSONObject(0)

                    val mediaType = bestMatch.optString("media_type", "movie")
                    val id = bestMatch.optInt("id")
                    val overview = bestMatch.optString("overview")
                    val ratingVal = bestMatch.optDouble("vote_average", 0.0)
                    val voteStr = if (ratingVal > 0.0) String.format(Locale.US, "%.1f", ratingVal) else channel.rating

                    val releaseDate = if (mediaType == "tv") bestMatch.optString("first_air_date") else bestMatch.optString("release_date")
                    val yearStr = if (releaseDate != null && releaseDate.length >= 4) releaseDate.substring(0, 4) else channel.year

                    val posterPath = bestMatch.optString("poster_path")
                    val backdropPath = bestMatch.optString("backdrop_path")
                    val posterUrl = if (posterPath.isNotEmpty() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else ""
                    val backdropUrl = if (backdropPath.isNotEmpty() && backdropPath != "null") "https://image.tmdb.org/t/p/w780$backdropPath" else ""

                    var director = channel.director
                    var actors = channel.actors

                    // Credits
                    try {
                        val creditsUrl = "https://api.themoviedb.org/3/$mediaType/$id/credits?api_key=$apiKey&language=es-ES"
                        val creditsRequest = Request.Builder().url(creditsUrl).header("User-Agent", "Mozilla/5.0").build()
                        val creditsResponse = okHttpClient.newCall(creditsRequest).execute()
                        if (creditsResponse.isSuccessful) {
                            val creditsJson = JSONObject(creditsResponse.body?.string() ?: "")
                            val crew = creditsJson.optJSONArray("crew")
                            if (crew != null) {
                                val dirList = mutableListOf<String>()
                                for (j in 0 until crew.length()) {
                                    val m = crew.getJSONObject(j)
                                    if (m.optString("job").equals("Director", ignoreCase = true)) {
                                        dirList.add(m.optString("name"))
                                    }
                                }
                                if (dirList.isNotEmpty()) director = dirList.joinToString(", ")
                            }
                            val cast = creditsJson.optJSONArray("cast")
                            if (cast != null) {
                                val actList = mutableListOf<String>()
                                val limit = minOf(cast.length(), 4)
                                for (j in 0 until limit) {
                                    actList.add(cast.getJSONObject(j).optString("name"))
                                }
                                if (actList.isNotEmpty()) actors = actList.joinToString(", ")
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    // Videos for trailer
                    var trailerUrl = ""
                    try {
                        val videosUrl = "https://api.themoviedb.org/3/$mediaType/$id/videos?api_key=$apiKey&language=es-ES"
                        val videosRequest = Request.Builder().url(videosUrl).header("User-Agent", "Mozilla/5.0").build()
                        val videosResponse = okHttpClient.newCall(videosRequest).execute()
                        if (videosResponse.isSuccessful) {
                            val videosJson = JSONObject(videosResponse.body?.string() ?: "")
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
                    } catch (e: Exception) { e.printStackTrace() }

                    return ImdbDetails(
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
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun fetchFromImdb(cleanName: String, channel: UiChannel, source: String): ImdbDetails {
        val searchUrl = "https://www.imdb.com/find/?q=${Uri.encode(cleanName)}&s=tt"
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

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val itemIndex = html.indexOf("ipc-metadata-list-summary-item")
                val subHtml = if (itemIndex != -1) html.substring(itemIndex) else {
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
                        if (ratingMatch != null) rating = ratingMatch.groupValues[1]

                        val metaDescMatch = "<meta[^>]*property=\"og:description\"[^>]*content=\"([^\"]+)\"".toRegex().find(titleHtml)
                            ?: "<meta[^>]*name=\"description\"[^>]*content=\"([^\"]+)\"".toRegex().find(titleHtml)
                        if (metaDescMatch != null) {
                            val desc = htmlDecode(metaDescMatch.groupValues[1])
                            if (desc.isNotEmpty() && !desc.contains("IMDb", ignoreCase = true)) synopsis = desc
                        }

                        val yearMatch = "\"releaseDate\":\\s*\"?([12][0-9]{3})".toRegex().find(titleHtml)
                            ?: "<title>[^<]*\\(([12][0-9]{3})\\)".toRegex().find(titleHtml)
                        if (yearMatch != null) year = yearMatch.groupValues[1]

                        val directorMatch = "\"director\":\\[?\\s*\\{[^\\}]*\"name\":\\s*\"([^\"]+)\"".toRegex().find(titleHtml)
                        if (directorMatch != null) director = htmlDecode(directorMatch.groupValues[1])
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Fallback mp4 trailers
        val defaultTrailers = mapOf(
            "TE VAN A MATAR" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "THE PUNISHER" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "JACK RYAN" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            "THE BOYS" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"
        )
        val lowerClean = cleanName.uppercase()
        trailerUrl = defaultTrailers.entries.find { lowerClean.contains(it.key) }?.value 
            ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        return ImdbDetails(
            synopsis = synopsis,
            rating = rating,
            year = year,
            director = director,
            actors = actors,
            trailerUrl = trailerUrl,
            sourceUsed = if (source == "TMDB") "TMDB" else "IMDb"
        )
    }

    private fun createFallbackDetails(cleanName: String, channel: UiChannel, source: String): ImdbDetails {
        val trailerUrl = if (cleanName.uppercase().contains("TE VAN A MATAR")) {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        } else "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        return ImdbDetails(
            synopsis = channel.synopsis,
            rating = channel.rating,
            year = channel.year,
            director = channel.director,
            actors = channel.actors,
            trailerUrl = trailerUrl,
            sourceUsed = source
        )
    }

    private suspend fun persistMetadata(cleanName: String, details: ImdbDetails) {
        try {
            tvRepository.saveCachedMetadata(
                DbCachedMetadata(
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
        } catch (e: Exception) { e.printStackTrace() }
    }

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

    fun cleanMovieNameForSearch(name: String): String {
        var title = name.trim()
        title = title.replace("^\\d+\\s+".toRegex(), "")
        title = title.replace("^🔞\\s+".toRegex(), "")
        val tags = listOf(
            "1080P", "720P", "2160P", "4K", "HD", "FHD", "UHD", "BLURAY", "BDRIP", "DVDRIP", "HDRIP", "CAMRIP",
            "H264", "H265", "HEVC", "X264", "X265", "LATINO", "CASTELLANO", "SUB", "MULTILANG", "DUAL", "SPA", "ENG"
        )
        for (tag in tags) {
            title = title.replace("(?i)\\b$tag\\b".toRegex(), "")
        }
        title = title.replace("(?i)\\b(?:S[0-9]+|T[0-9]+|E[0-9]+|TEMPORADA\\s*[0-9]+|CAPITULO\\s*[0-9]+|[0-9]+x[0-9]+|CAP\\.?\\s*[0-9]+|EP\\.?\\s*[0-9]+).*$".toRegex(), "")
        title = title.replace("\\([12][0-9]{3}\\)".toRegex(), "")
        title = title.replace("\\[[12][0-9]{3}\\]".toRegex(), "")
        title = title.replace("\\b[12][0-9]{3}\\b".toRegex(), "")
        title = title.replace("[:\\-*\\s]+$".toRegex(), "").trim()
        return title
    }

    suspend fun fetchFirstYoutubeVideoId(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val match = "/watch\\?v=([a-zA-Z0-9_-]{11})".toRegex().find(html)
                return@withContext match?.groupValues?.get(1)
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }
}
