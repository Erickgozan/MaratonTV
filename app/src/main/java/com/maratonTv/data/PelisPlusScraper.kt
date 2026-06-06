package com.maratonTv.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import java.util.ArrayList

object PelisPlusScraper {
    @Volatile
    var appContext: android.content.Context? = null

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    var CURRENT_WORKING_HOST = "www.poseidonhd2.co"

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "es-MX,es;q=0.9"
    )

    fun getServerName(url: String): String {
        val hashIdx = url.indexOf('#')
        var suffix = ""
        var cleanUrl = url
        var fragmentVal = ""
        if (hashIdx >= 0) {
            val fragment = url.substring(hashIdx + 1)
            fragmentVal = fragment
            cleanUrl = url.substring(0, hashIdx)
            if (fragment.isNotEmpty()) {
                suffix = " (" + fragment.replace("-", " ") + ")"
            }
        }
        val lower = cleanUrl.lowercase()
        val fragLower = fragmentVal.lowercase()
        val baseName = when {
            lower.contains("voe.sx") || lower.contains("voe") || fragLower.contains("voe") -> "Voe"
            lower.contains("fembed") || lower.contains("fembad") || lower.contains("fevv") || fragLower.contains("fembed") -> "Fembed"
            lower.contains("mixdrop") || fragLower.contains("mixdrop") -> "MixDrop"
            lower.contains("streamtape") || fragLower.contains("streamtape") -> "Streamtape"
            lower.contains("upstream") || fragLower.contains("upstream") -> "Upstream"
            lower.contains("dood") || fragLower.contains("dood") -> "Doodstream"
            lower.contains("fastplay") || fragLower.contains("fastplay") -> "FastPlay"
            lower.contains("uqload") || fragLower.contains("uqload") -> "Uqload"
            lower.contains("filemoon") || lower.contains("bysejikuar") || fragLower.contains("filemoon") || fragLower.contains("moon") -> "Filemoon"
            lower.contains("ok.ru") || fragLower.contains("ok.ru") || fragLower.contains("okru") -> "OK.ru"
            lower.contains("vidoza") || fragLower.contains("vidoza") -> "Vidoza"
            lower.contains("streamsb") || fragLower.contains("streamsb") -> "StreamSB"
            lower.contains("streamwish") || lower.contains("wish") || fragLower.contains("wish") || fragLower.contains("streamwish") -> "StreamWish"
            lower.contains("vidhide") || lower.contains("hide") || fragLower.contains("hide") || fragLower.contains("vidhide") -> "VidHide"
            else -> "Servidor"
        }
        return "$baseName$suffix"
    }

    private suspend fun getHtmlViaWebView(url: String): String {
        // No WebView challenge needed for Blooders2, we can directly request standard HTML
        return getHtml(url)
    }

    private suspend fun getHtml(url: String): String {
        Log.i("PelisPlusScraper", "Retrieving HTML for Blooders2: $url")
        val requestBuilder = Request.Builder().url(url)
        HEADERS.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        try {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string() ?: ""
                    } else {
                        ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PelisPlusScraper", "Error fetching Blooders2 HTML: ${e.message}")
        }
        return ""
    }

    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    fun ensureAbsoluteUrl(url: String): String {
        if (url.isEmpty()) return ""
        val decoded = decodeHtmlEntities(url)
        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
            return decoded
        }
        if (decoded.startsWith("//")) {
            return "https:$decoded"
        }
        val prefix = "https://$CURRENT_WORKING_HOST"
        if (decoded.startsWith("/")) {
            return "$prefix$decoded"
        }
        return "$prefix/$decoded"
    }

    fun formatSlugToUrl(slug: String): String {
        var s = slug.removePrefix("/")
        if (s.startsWith("series/")) {
            s = s.replaceFirst("series/", "serie/")
        } else if (s.startsWith("movies/")) {
            s = s.replaceFirst("movies/", "pelicula/")
        }
        s = s.replace("/seasons/", "/temporada/")
        s = s.replace("/episodes/", "/episodio/")
        return "https://$CURRENT_WORKING_HOST/$s"
    }

    private fun getNextDataJson(html: String): org.json.JSONObject? {
        val m = Pattern.compile("<script[^>]+id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html)
        if (m.find()) {
            val content = m.group(1) ?: return null
            return try {
                org.json.JSONObject(content)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun findJsonKeyRecursively(obj: org.json.JSONObject, keyToFind: String): org.json.JSONObject? {
        if (obj.has(keyToFind)) {
            val res = obj.optJSONObject(keyToFind)
            if (res != null) return res
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = obj.optJSONObject(key)
            if (child != null) {
                val found = findJsonKeyRecursively(child, keyToFind)
                if (found != null) return found
            }
            val array = obj.optJSONArray(key)
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    if (item != null) {
                        val found = findJsonKeyRecursively(item, keyToFind)
                        if (found != null) return found
                    }
                }
            }
        }
        return null
    }

    data class ScrapedItem(
        val title: String,
        val url: String,
        val img: String,
        val year: String = "2026"
    )

    suspend fun scrapeList(query: String = "", page: Int = 1): List<ScrapedItem> {
        val baseUrl = "https://$CURRENT_WORKING_HOST"
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        } else {
            baseUrl
        }

        val html = getHtml(url)
        if (html.isEmpty()) return emptyList()

        val nextJson = getNextDataJson(html) ?: return emptyList()
        val props = nextJson.optJSONObject("props")
        val pageProps = props?.optJSONObject("pageProps") ?: return emptyList()

        val items = ArrayList<ScrapedItem>()
        val visitedUrls = HashSet<String>()

        fun parseItemArray(arr: org.json.JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optJSONObject("titles")?.optString("name") ?: obj.optString("title") ?: ""
                val img = obj.optJSONObject("images")?.optString("poster") ?: obj.optString("image") ?: ""
                val slugObj = obj.optJSONObject("url")
                val slug = slugObj?.optString("slug") ?: ""
                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    val fullUrl = formatSlugToUrl(slug)
                    if (visitedUrls.add(fullUrl)) {
                        val date = obj.optString("releaseDate")
                        val year = if (date.length >= 4) date.substring(0, 4) else "2026"
                        items.add(ScrapedItem(decodeHtmlEntities(title), fullUrl, ensureAbsoluteUrl(img), year))
                    }
                }
            }
        }

        if (query.isNotEmpty()) {
            // Search matches might be in "movies", "series", "results", etc. Parse all arrays dynamically to be robust
            val keys = pageProps.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = pageProps.optJSONArray(key)
                if (array != null) {
                    parseItemArray(array)
                }
            }
        } else {
            // Homepage lists multiple categories nicely
            parseItemArray(pageProps.optJSONArray("tabLastMovies"))
            parseItemArray(pageProps.optJSONArray("tabLastReleasedMovies"))
            parseItemArray(pageProps.optJSONArray("series"))
        }

        return items
    }

    data class ScrapedSerieDetail(
        val title: String,
        val img: String,
        val desc: String,
        val capitulos: List<ScrapedCapitulo>
    )

    data class ScrapedCapitulo(
        val title: String,
        val url: String
    )

    suspend fun scrapeDetail(url: String): ScrapedSerieDetail? {
        val html = getHtml(url)
        if (html.isEmpty()) return null

        val nextJson = getNextDataJson(html) ?: return null
        val props = nextJson.optJSONObject("props")
        val pageProps = props?.optJSONObject("pageProps") ?: return null

        val isMovie = url.contains("/pelicula/") || url.contains("/movies/") || url.contains("/peli/")

        if (isMovie) {
            val movieObj = pageProps.optJSONObject("thisMovie") ?: pageProps.optJSONObject("movie")
            if (movieObj != null) {
                val title = movieObj.optJSONObject("titles")?.optString("name") ?: movieObj.optString("title") ?: ""
                val img = movieObj.optJSONObject("images")?.optString("poster") ?: movieObj.optString("image") ?: ""
                val desc = movieObj.optString("overview") ?: ""
                val singleCap = ScrapedCapitulo(title, url)
                return ScrapedSerieDetail(decodeHtmlEntities(title), ensureAbsoluteUrl(img), decodeHtmlEntities(desc), listOf(singleCap))
            }
        } else {
            val serieObj = pageProps.optJSONObject("thisSerie") ?: pageProps.optJSONObject("serie")
            if (serieObj != null) {
                val title = serieObj.optJSONObject("titles")?.optString("name") ?: serieObj.optString("name") ?: ""
                val img = serieObj.optJSONObject("images")?.optString("poster") ?: serieObj.optString("image") ?: ""
                val desc = serieObj.optString("overview") ?: ""

                val capitulos = ArrayList<ScrapedCapitulo>()
                val seasons = serieObj.optJSONArray("seasons")
                if (seasons != null) {
                    for (i in 0 until seasons.length()) {
                        val seasonObj = seasons.optJSONObject(i) ?: continue
                        val episodes = seasonObj.optJSONArray("episodes") ?: continue
                        for (j in 0 until episodes.length()) {
                            val epObj = episodes.optJSONObject(j) ?: continue
                            val epTitle = epObj.optString("title") ?: ""
                            val epSlugObj = epObj.optJSONObject("url")
                            val epSlug = epSlugObj?.optString("slug") ?: ""
                            if (epSlug.isNotEmpty()) {
                                val epUrl = formatSlugToUrl(epSlug)
                                capitulos.add(ScrapedCapitulo(decodeHtmlEntities(epTitle.ifEmpty { "Capítulo ${j + 1}" }), epUrl))
                            }
                        }
                    }
                }
                return ScrapedSerieDetail(decodeHtmlEntities(title), ensureAbsoluteUrl(img), decodeHtmlEntities(desc), capitulos)
            }
        }

        return null
    }

    data class ScrapedCapituloDetail(
        val title: String,
        val trid: String,
        val embedUrls: List<String>,
        val opciones: List<ScrapedOption>,
        val img: String = ""
    )

    data class ScrapedOption(
        val label: String,
        val index: Int
    )

    suspend fun scrapeCapituloDetail(url: String): ScrapedCapituloDetail? {
        val html = getHtml(url)
        if (html.isEmpty()) return null

        val nextJson = getNextDataJson(html) ?: return null
        val props = nextJson.optJSONObject("props")
        val pageProps = props?.optJSONObject("pageProps") ?: return null

        val videosObj = findJsonKeyRecursively(pageProps, "videos")
        val embedUrls = ArrayList<String>()

        if (videosObj != null) {
            val keys = videosObj.keys()
            while (keys.hasNext()) {
                val lang = keys.next()
                val videoList = videosObj.optJSONArray(lang) ?: continue
                for (i in 0 until videoList.length()) {
                    val videoItem = videoList.optJSONObject(i) ?: continue
                    val cyberlocker = videoItem.optString("cyberlocker") ?: ""
                    val resultUrl = videoItem.optString("result") ?: ""
                    if (resultUrl.isNotEmpty()) {
                        val styledLang = lang.replaceFirstChar { it.uppercase() }
                        embedUrls.add("$resultUrl#$styledLang-$cyberlocker")
                    }
                }
            }
        }

        var title = ""
        val thisEpisode = pageProps.optJSONObject("episode")
        if (thisEpisode != null) {
            title = thisEpisode.optString("title") ?: ""
        }
        if (title.isEmpty()) {
            val thisMovie = pageProps.optJSONObject("thisMovie") ?: pageProps.optJSONObject("movie")
            if (thisMovie != null) {
                title = thisMovie.optJSONObject("titles")?.optString("name") ?: thisMovie.optString("title") ?: ""
            }
        }
        if (title.isEmpty()) {
            val ogTitleMatcher = Pattern.compile("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]+)\"").matcher(html)
            if (ogTitleMatcher.find()) {
                title = ogTitleMatcher.group(1) ?: ""
            }
        }

        var poster = ""
        val ogImageMatcher = Pattern.compile("<meta[^>]+property=\"og:image\"[^>]+content=\"([^\"]+)\"").matcher(html)
        if (ogImageMatcher.find()) {
            poster = ensureAbsoluteUrl(ogImageMatcher.group(1) ?: "")
        }
        if (poster.isEmpty() && thisEpisode != null) {
            poster = thisEpisode.optString("image") ?: ""
        }

        return ScrapedCapituloDetail(decodeHtmlEntities(title), "", embedUrls, emptyList(), ensureAbsoluteUrl(poster))
    }

    suspend fun resolveDirectVideoIframe(embedUrl: String): String {
        val hashIdx = embedUrl.indexOf('#')
        val cleanUrl = if (hashIdx >= 0) embedUrl.substring(0, hashIdx) else embedUrl
        val fragment = if (hashIdx >= 0) embedUrl.substring(hashIdx) else ""

        val html = getHtml(cleanUrl)
        if (html.isEmpty()) return embedUrl

        // Try extracting js redirection variable 'var url = ...' first
        val varUrlMatcher = Pattern.compile("var\\s+url\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html)
        if (varUrlMatcher.find()) {
            val directUrl = varUrlMatcher.group(1) ?: ""
            if (directUrl.isNotEmpty()) {
                return ensureAbsoluteUrl(directUrl) + fragment
            }
        }

        val iframeMatcher = Pattern.compile("<iframe[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
        while (iframeMatcher.find()) {
            val src = iframeMatcher.group(1) ?: ""
            if (src.isNotEmpty() && !src.contains("pelisplus") && !src.contains("about:blank")) {
                return ensureAbsoluteUrl(src) + fragment
            }
        }

        return embedUrl
    }
}
