package com.maratonTv.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import java.util.ArrayList

object SeriesMetroScraper {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val BASE_URL = "https://www3.seriesmetro.net"

    private val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "es-MX,es;q=0.9"
    )

    private fun getHtml(url: String): String {
        val requestBuilder = Request.Builder().url(url)
        HEADERS.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: ""
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e("SeriesMetroScraper", "Error fetching HTML: ${e.message}")
            ""
        }
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
        if (decoded.startsWith("/")) {
            return "$BASE_URL$decoded"
        }
        return "$BASE_URL/$decoded"
    }

    data class ScrapedItem(
        val title: String,
        val url: String,
        val img: String,
        val year: String = "2026"
    )

    // Parses BloodersTv list (e.g. cartelera, homepage, or search)
    fun scrapeSeriesList(query: String = "", page: Int = 1, categoryType: String = ""): List<ScrapedItem> {
        val url = if (query.isNotEmpty()) {
            "$BASE_URL/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        } else {
            val segment = when (categoryType) {
                "PELICULA" -> "peliculas"
                "SERIES" -> "cartelera-series"
                else -> "cartelera-series"
            }
            if (page == 1) "$BASE_URL/$segment/" else "$BASE_URL/$segment/page/$page/"
        }

        var html = getHtml(url)
        if (html.isEmpty() && !query.isNotEmpty()) {
            // Fallback for movies if /peliculas/ returned empty
            val fallbackSegment = when (categoryType) {
                "PELICULA" -> "cartelera-peliculas"
                else -> ""
            }
            if (fallbackSegment.isNotEmpty()) {
                val fallbackUrl = if (page == 1) "$BASE_URL/$fallbackSegment/" else "$BASE_URL/$fallbackSegment/page/$page/"
                html = getHtml(fallbackUrl)
            }
        }
        
        if (html.isEmpty()) return emptyList()

        val items = ArrayList<ScrapedItem>()
        val visitedUrls = HashSet<String>()

        // Match each <article> element or card element
        val articlePattern = Pattern.compile("<article[^>]*>(.*?)</article>", Pattern.DOTALL)
        val m = articlePattern.matcher(html)
        while (m.find()) {
            val articleHtml = m.group(1) ?: ""
            
            // Extract URL
            var linkUrl = ""
            val urlMatcher = Pattern.compile("href=\"([^\"]*(?:serie|pelicula|peli|capitulo)/[^\"]+)\"").matcher(articleHtml)
            if (urlMatcher.find()) {
                val rawLink = urlMatcher.group(1) ?: ""
                linkUrl = ensureAbsoluteUrl(rawLink)
            }
            
            if (linkUrl.isEmpty() || visitedUrls.contains(linkUrl)) continue
            
            // Extract Title
            var title = ""
            val titleMatcher = Pattern.compile("<h[23][^>]+class=\"entry-title\"[^>]*>(.*?)</h[23]>", Pattern.DOTALL).matcher(articleHtml)
            if (titleMatcher.find()) {
                title = titleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
            }
            
            if (title.isEmpty()) {
                val anyTitleMatcher = Pattern.compile("<h[23][^>]*>(.*?)</h[23]>", Pattern.DOTALL).matcher(articleHtml)
                if (anyTitleMatcher.find()) {
                    title = anyTitleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
                }
            }
            
            // Extract Image
            var imgSrc = ""
            val imgMatcher = Pattern.compile("src=\"([^\"]+)\"").matcher(articleHtml)
            if (imgMatcher.find()) {
                val rawImg = imgMatcher.group(1) ?: ""
                imgSrc = ensureAbsoluteUrl(rawImg)
            }
            
            // Extract Year
            var year = "2026"
            val yearMatcher = Pattern.compile("<span class=\"year\">(\\d+)</span>").matcher(articleHtml)
            if (yearMatcher.find()) {
                year = yearMatcher.group(1) ?: "2026"
            }
            
            title = decodeHtmlEntities(title)
            if (title.isNotEmpty()) {
                if (query.isNotEmpty()) {
                    val cleanQuery = query.lowercase().trim()
                    val cleanTitle = title.lowercase().trim()
                    val queryWords = cleanQuery.split("\\s+".toRegex()).filter { it.length > 2 }
                    val isRelated = if (queryWords.isEmpty()) {
                        cleanTitle.contains(cleanQuery)
                    } else {
                        queryWords.any { cleanTitle.contains(it) }
                    }
                    if (!isRelated) continue
                }
                visitedUrls.add(linkUrl)
                items.add(ScrapedItem(title, linkUrl, imgSrc, year))
            }
        }

        // Fallback to simpler regex if nothing matched
        if (items.isEmpty()) {
            val p = Pattern.compile("<a[^>]+href=\"([^\"]*(?:serie|pelicula|peli|capitulo)/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
            val m2 = p.matcher(html)
            while (m2.find()) {
                val rawLinkUrl = m2.group(1) ?: continue
                val linkUrl = ensureAbsoluteUrl(rawLinkUrl)
                if (visitedUrls.contains(linkUrl)) continue

                val innerHtml = m2.group(2) ?: ""
                var imgSrc = ""
                val imgMatcher = Pattern.compile("src=\"([^\"]+)\"").matcher(innerHtml)
                if (imgMatcher.find()) {
                    imgSrc = ensureAbsoluteUrl(imgMatcher.group(1) ?: "")
                }

                var title = ""
                val altMatcher = Pattern.compile("alt=\"([^\"]+)\"").matcher(innerHtml)
                if (altMatcher.find()) {
                    title = altMatcher.group(1) ?: ""
                }

                if (title.isEmpty()) {
                    title = innerHtml.replace("<[^>]+>".toRegex(), "").trim()
                }

                title = decodeHtmlEntities(title)
                if (title.isNotEmpty()) {
                    if (query.isNotEmpty()) {
                        val cleanQuery = query.lowercase().trim()
                        val cleanTitle = title.lowercase().trim()
                        val queryWords = cleanQuery.split("\\s+".toRegex()).filter { it.length > 2 }
                        val isRelated = if (queryWords.isEmpty()) {
                            cleanTitle.contains(cleanQuery)
                        } else {
                            queryWords.any { cleanTitle.contains(it) }
                        }
                        if (!isRelated) continue
                    }
                    visitedUrls.add(linkUrl)
                    items.add(ScrapedItem(title, linkUrl, imgSrc))
                }
            }
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

    fun scrapeSerieDetail(url: String): ScrapedSerieDetail? {
        val html = getHtml(url)
        if (html.isEmpty()) return null

        // Title from <h1>
        var title = ""
        val titleMatcher = Pattern.compile("<h1>(.*?)</h1>", Pattern.DOTALL).matcher(html)
        if (titleMatcher.find()) {
            title = titleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
        }
        title = decodeHtmlEntities(title)

        // Poster image
        var poster = ""
        val posterMatcher = Pattern.compile("src=\"([^\"]*tmdb[^\"]+)\"").matcher(html)
        if (posterMatcher.find()) {
            poster = ensureAbsoluteUrl(posterMatcher.group(1) ?: "")
        } else {
            val anyImgMatcher = Pattern.compile("src=\"([^\"]*(?:jpg|png|webp))[^\"]*\"").matcher(html)
            if (anyImgMatcher.find()) {
                poster = ensureAbsoluteUrl(anyImgMatcher.group(1) ?: "")
            }
        }

        // Description
        var desc = ""
        val descMatcher = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL).matcher(html)
        if (descMatcher.find()) {
            desc = descMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
        }
        desc = decodeHtmlEntities(desc)

        // Chapters
        val capitulos = ArrayList<ScrapedCapitulo>()
        val visitedCaps = HashSet<String>()

        // 1. Try to parse using <article> tags (modern WordPress theme layout)
        val articlePattern = Pattern.compile("<article[^>]*>(.*?)</article>", Pattern.DOTALL)
        val m = articlePattern.matcher(html)
        while (m.find()) {
            val articleHtml = m.group(1) ?: ""
            val urlMatcher = Pattern.compile("href=\"([^\"]*capitulo/[^\"]+)\"").matcher(articleHtml)
            if (urlMatcher.find()) {
                val rawCap = urlMatcher.group(1) ?: ""
                val capUrl = ensureAbsoluteUrl(rawCap)
                if (visitedCaps.add(capUrl)) {
                    var capText = ""
                    // Try to extract num-epi
                    var numEpi = ""
                    val numMatcher = Pattern.compile("<span class=\"num-epi\">([^<]+)</span>").matcher(articleHtml)
                    if (numMatcher.find()) {
                        numEpi = numMatcher.group(1)?.trim() ?: ""
                    }
                    
                    val titleMatcher = Pattern.compile("<h[23][^>]+class=\"entry-title\"[^>]*>(.*?)</h[23]>", Pattern.DOTALL).matcher(articleHtml)
                    if (titleMatcher.find()) {
                        capText = titleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
                    }
                    
                    if (capText.isEmpty()) {
                        val anyTitleMatcher = Pattern.compile("<h[23][^>]*>(.*?)</h[23]>", Pattern.DOTALL).matcher(articleHtml)
                        if (anyTitleMatcher.find()) {
                            capText = anyTitleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
                        }
                    }
                    
                    if (capText.isEmpty()) {
                        capText = capUrl.removeSuffix("/").substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
                    }
                    
                    capText = decodeHtmlEntities(capText)
                    val finalTitle = if (numEpi.isNotEmpty()) "[$numEpi] $capText" else capText
                    capitulos.add(ScrapedCapitulo(finalTitle, capUrl))
                }
            }
        }

        // 2. Fallback to general link scraping
        if (capitulos.isEmpty()) {
            val capMatcher = Pattern.compile("<a[^>]+href=\"([^\"]*capitulo/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(html)
            while (capMatcher.find()) {
                val rawCapUrl = capMatcher.group(1) ?: continue
                val capUrl = ensureAbsoluteUrl(rawCapUrl)
                if (visitedCaps.contains(capUrl)) continue
                visitedCaps.add(capUrl)

                var capText = capMatcher.group(2)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
                if (capText.isEmpty()) {
                    capText = capUrl.removeSuffix("/").substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
                }
                capText = decodeHtmlEntities(capText)
                capitulos.add(ScrapedCapitulo(capText, capUrl))
            }
        }

        if (title.isEmpty()) {
            val urlParts = url.removeSuffix("/").split("/")
            title = urlParts.lastOrNull()?.replace("-", " ")?.replaceFirstChar { it.uppercase() } ?: "Serie"
        }

        return ScrapedSerieDetail(title, poster, desc, capitulos)
    }

    data class ScrapedCapituloDetail(
        val title: String,
        val trid: String,
        val embedUrls: List<String>,
        val opciones: List<ScrapedOption>
    )

    data class ScrapedOption(
        val label: String,
        val index: Int
    )

    fun scrapeCapituloDetail(url: String): ScrapedCapituloDetail? {
        val html = getHtml(url)
        if (html.isEmpty()) return null

        // Title
        var title = ""
        val titleMatcher = Pattern.compile("<h1>(.*?)</h1>", Pattern.DOTALL).matcher(html)
        if (titleMatcher.find()) {
            title = titleMatcher.group(1)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
        }
        title = decodeHtmlEntities(title)

        // trid
        var trid = ""
        val tridMatcher = Pattern.compile("trid[=&](\\d+)").matcher(html)
        if (tridMatcher.find()) {
            trid = tridMatcher.group(1) ?: ""
        }

        // Language Options (e.g. Latino/Castellano/Subtitulado)
        val opciones = ArrayList<ScrapedOption>()
        val optMatcher = Pattern.compile("options-(\\d+)[^\"]*\"[^>]*>(.*?)</a>").matcher(html)
        while (optMatcher.find()) {
            val idx = optMatcher.group(1)?.toIntOrNull() ?: opciones.size
            var label = optMatcher.group(2)?.replace("<[^>]+>".toRegex(), "")?.trim() ?: ""
            label = decodeHtmlEntities(label)
            if (label.isNotEmpty() && !opciones.any { it.label == label }) {
                opciones.add(ScrapedOption(label, idx))
            }
        }

        // If options are missing, let's create default language indices based on label regex inside anchors or divs
        if (opciones.isEmpty()) {
            val customLangMatcher = Pattern.compile("<a[^>]*href=\"#options-(\\d+)\"[^>]*>([^<]+)</a>").matcher(html)
            while (customLangMatcher.find()) {
                val idx = customLangMatcher.group(1)?.toIntOrNull() ?: opciones.size
                var label = customLangMatcher.group(2)?.trim() ?: ""
                label = decodeHtmlEntities(label)
                if (label.isNotEmpty() && !opciones.any { it.label == label }) {
                    opciones.add(ScrapedOption(label, idx))
                }
            }
        }

        // Direct stream URLs or player embeds
        val embedUrls = ArrayList<String>()
        val embedMatcher = Pattern.compile("(?:href|src)=\"([^\"]*trembed[^\"]*)\"").matcher(html)
        while (embedMatcher.find()) {
            val rawMatchUrl = embedMatcher.group(1) ?: continue
            val matchUrl = ensureAbsoluteUrl(rawMatchUrl)
            if (!embedUrls.contains(matchUrl)) {
                embedUrls.add(matchUrl)
            }
        }

        // If no embeds were found but we have a trid, generate fallback standard embeds dynamically
        if (embedUrls.isEmpty() && trid.isNotEmpty()) {
            // Options is empty or loop through options or standard indices 0..2
            if (opciones.isNotEmpty()) {
                opciones.forEach { opt ->
                    val embed = "$BASE_URL/?trembed=${opt.index}&trid=$trid&trtype=2"
                    embedUrls.add(embed)
                }
            } else {
                for (i in 0..2) {
                    val embed = "$BASE_URL/?trembed=$i&trid=$trid&trtype=2"
                    embedUrls.add(embed)
                }
            }
        }

        return ScrapedCapituloDetail(title, trid, embedUrls, opciones)
    }

    fun resolveDirectVideoIframe(trembedUrl: String): String {
        if (!trembedUrl.contains("trembed")) return trembedUrl
        val html = getHtml(trembedUrl)
        if (html.isEmpty()) return trembedUrl

        // 1. Check for iframe src that is external with double quotes
        val iframeMatcher = Pattern.compile("<iframe[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
        while (iframeMatcher.find()) {
            val src = iframeMatcher.group(1) ?: ""
            if (src.isNotEmpty() && !src.contains("seriesmetro") && !src.contains("about:blank")) {
                return ensureAbsoluteUrl(src)
            }
        }
        
        // 2. Check for iframe src with single quotes
        val iframeMatcherSingle = Pattern.compile("<iframe[^>]+src='([^']+)'", Pattern.CASE_INSENSITIVE).matcher(html)
        while (iframeMatcherSingle.find()) {
            val src = iframeMatcherSingle.group(1) ?: ""
            if (src.isNotEmpty() && !src.contains("seriesmetro") && !src.contains("about:blank")) {
                return ensureAbsoluteUrl(src)
            }
        }

        // 3. Check for alternative scripting redirections or properties
        val scriptMatcher = Pattern.compile("(?:url|src|file|link)\\s*[:=]\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
        while (scriptMatcher.find()) {
            val link = scriptMatcher.group(1) ?: ""
            if (link.startsWith("http") && !link.contains("seriesmetro") && !link.contains("wp-content") && !link.contains("wp-includes")) {
                return link
            }
        }

        return trembedUrl
    }
}

