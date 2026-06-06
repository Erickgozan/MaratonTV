package com.maratonTv.data.repository

import com.maratonTv.data.model.ChannelSource
import com.maratonTv.data.model.UiChannel
import com.maratonTv.data.remote.scrapers.PelisPlusScraper
import com.maratonTv.data.remote.scrapers.SeriesMetroScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScraperRepository {

    suspend fun getScrapedItems(query: String, category: String): List<UiChannel> = withContext(Dispatchers.IO) {
        val combined = ArrayList<UiChannel>()
        try {
            if (query.isNotEmpty()) {
                val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(query = query) } catch (e: Exception) { emptyList() }
                val scrapedPP = try { PelisPlusScraper.scrapeList(query = query) } catch (e: Exception) { emptyList() }

                scrapedSM.forEach {
                    val isSeries = isSeriesUrl(it.url)
                    val itemGroup = if (isSeries) "SERIES" else "PELICULA"
                    val shouldAdd = when (category) {
                        "PELICULA" -> !isSeries
                        "SERIES" -> isSeries
                        else -> true
                    }
                    if (shouldAdd) {
                        combined.add(mapSeriesMetroToUi(it, itemGroup, "BloodersTv", "Búsqueda BloodersTv"))
                    }
                }

                scrapedPP.forEach {
                    val isSeries = isSeriesUrl(it.url)
                    val itemGroup = if (isSeries) "SERIES" else "PELICULA"
                    val shouldAdd = when (category) {
                        "PELICULA" -> !isSeries
                        "SERIES" -> isSeries
                        else -> true
                    }
                    if (shouldAdd) {
                        combined.add(mapPelisPlusToUi(it, itemGroup, "Blooders2", "Búsqueda Blooders2"))
                    }
                }
            } else {
                when (category) {
                    "SERIES" -> {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1, categoryType = "SERIES") } catch (e: Exception) { emptyList() }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        
                        scrapedSM.filter { !isMovieUrl(it.url) }.forEach {
                            combined.add(mapSeriesMetroToUi(it, "SERIES", "BloodersTv", "BLOODERSTV_CATALOG"))
                        }
                        scrapedPP.filter { it.url.contains("/serie/") || it.url.contains("/series/") }.forEach {
                            combined.add(mapPelisPlusToUi(it, "SERIES", "Blooders2", "BLOODERSTV_CATALOG"))
                        }
                    }
                    "PELICULA" -> {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1, categoryType = "PELICULA") } catch (e: Exception) { emptyList() }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        
                        scrapedSM.filter { isMovieUrl(it.url) }.forEach {
                            combined.add(mapSeriesMetroToUi(it, "PELICULA", "BloodersTv", "BLOODERSTV_CATALOG"))
                        }
                        scrapedPP.filter { isMovieUrl(it.url) }.forEach {
                            combined.add(mapPelisPlusToUi(it, "PELICULA", "Blooders2", "BLOODERSTV_CATALOG"))
                        }
                    }
                    "KIDS" -> {
                        val visitedUrls = HashSet<String>()
                        listOf("Disney", "Pixar", "Minions").forEach { term ->
                            try {
                                PelisPlusScraper.scrapeList(query = term).take(10).forEach {
                                    if (visitedUrls.add(it.url)) {
                                        combined.add(mapPelisPlusToUi(it, "KIDS", "Blooders2 Kids", "BLOODERSTV_CATALOG"))
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    "ANIME" -> {
                        val visitedUrls = HashSet<String>()
                        listOf("Dragon Ball", "Naruto", "One Piece").forEach { term ->
                            try {
                                PelisPlusScraper.scrapeList(query = term).take(10).forEach {
                                    if (visitedUrls.add(it.url)) {
                                        combined.add(mapPelisPlusToUi(it, "ANIME", "Blooders2 Anime", "BLOODERSTV_CATALOG"))
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    "EXTENSION" -> {
                        val scrapedSM = try { SeriesMetroScraper.scrapeSeriesList(page = 1) } catch (e: Exception) { emptyList() }
                        val scrapedPP = try { PelisPlusScraper.scrapeList(page = 1) } catch (e: Exception) { emptyList() }
                        scrapedSM.forEach { combined.add(mapSeriesMetroToUi(it, "EXTENSION", "BloodersTv", "BLOODERSTV_CATALOG")) }
                        scrapedPP.forEach { combined.add(mapPelisPlusToUi(it, "EXTENSION", "Blooders2", "BLOODERSTV_CATALOG")) }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        combined
    }

    private fun isSeriesUrl(url: String): Boolean {
        return url.contains("/serie/") || url.contains("/series/") || url.contains("/temporada/") || url.contains("/episodio/") || url.contains("/capitulo/")
    }

    private fun isMovieUrl(url: String): Boolean {
        return url.contains("/pelicula/") || url.contains("/movies/") || url.contains("/peli/")
    }

    private fun mapSeriesMetroToUi(it: SeriesMetroScraper.ScrapedItem, group: String, sourceName: String, originalGroup: String): UiChannel {
        return UiChannel(
            name = it.title,
            groupTitle = group,
            logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
            primaryStreamUrl = it.url,
            sources = listOf(ChannelSource(sourceName, it.url)),
            synopsis = "Contenido de $sourceName disponible directamente.",
            rating = "8.8",
            year = it.year,
            director = sourceName,
            actors = "Virtual",
            isEmbedText = true,
            originalGroup = originalGroup
        )
    }

    private fun mapPelisPlusToUi(it: PelisPlusScraper.ScrapedItem, group: String, sourceName: String, originalGroup: String): UiChannel {
        return UiChannel(
            name = it.title,
            groupTitle = group,
            logoUrl = it.img.ifEmpty { "https://cdn-icons-png.flaticon.com/512/864/864383.png" },
            primaryStreamUrl = it.url,
            sources = listOf(ChannelSource(sourceName, it.url)),
            synopsis = "Contenido de $sourceName disponible directamente.",
            rating = "8.9",
            year = it.year,
            director = sourceName,
            actors = "Virtual",
            isEmbedText = true,
            originalGroup = originalGroup
        )
    }
}
