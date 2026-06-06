package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.data.SeriesMetroScraper
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testScraperList() {
    println("--- START TEST SCRAPER LIST ---")
    val items = SeriesMetroScraper.scrapeSeriesList()
    println("Total series scraped: ${items.size}")
    assertTrue(items.isNotEmpty())
    items.take(3).forEach { item ->
        println("Item: ${item.title} -> ${item.url}, img=${item.img}")
    }
  }


  @Test
  fun testPoseidonHDScraperList() = kotlinx.coroutines.runBlocking {
    println("--- START TEST POSEIDONHD SCRAPER LIST ---")
    val items = com.example.data.PelisPlusScraper.scrapeList()
    println("Total PoseidonHD items scraped: ${items.size}")
    assertTrue(items.isNotEmpty())
    items.take(3).forEach { item ->
        println("Item: ${item.title} -> ${item.url}, img=${item.img}")
        assertTrue(item.url.contains("poseidonhd2"))
    }
  }

  @Test
  fun testPoseidonHDScraperSearch() = kotlinx.coroutines.runBlocking {
    println("--- START TEST POSEIDONHD SCRAPER SEARCH ---")
    val items = com.example.data.PelisPlusScraper.scrapeList(query = "rebelde")
    println("Total Search results: ${items.size}")
    assertTrue(items.isNotEmpty())
    items.take(3).forEach { item ->
        println("Search Match: ${item.title} -> ${item.url}, img=${item.img}")
        assertTrue(item.url.contains("poseidonhd2"))
    }
  }

  @Test
  fun testPoseidonHDScraperDetailSeries() = kotlinx.coroutines.runBlocking {
    println("--- START TEST POSEIDONHD SCRAPER DETAIL SERIES ---")
    val url = "https://www.poseidonhd2.co/serie/224941/the-boroughs-jubilacion-rebelde"
    val detail = com.example.data.PelisPlusScraper.scrapeDetail(url)
    assertNotNull(detail)
    println("Detail Title: ${detail!!.title}")
    println("Detail Description Snippet: ${detail.desc.take(150)}")
    println("Detail ChaptersCount: ${detail.capitulos.size}")
    assertTrue(detail.capitulos.isNotEmpty())
    detail.capitulos.take(3).forEach { cap ->
        println(" - Chapter: ${cap.title} -> ${cap.url}")
        assertTrue(cap.url.contains("/temporada/"))
    }
  }

  @Test
  fun testPoseidonHDScraperDetailMovie() = kotlinx.coroutines.runBlocking {
    println("--- START TEST POSEIDONHD SCRAPER DETAIL MOVIE ---")
    val url = "https://www.poseidonhd2.co/pelicula/8961/bad-boys-2"
    val detail = com.example.data.PelisPlusScraper.scrapeDetail(url)
    assertNotNull(detail)
    println("Detail Title: ${detail!!.title}")
    println("Detail Description Snippet: ${detail.desc.take(150)}")
    println("Detail ChaptersCount: ${detail.capitulos.size}")
    assertEquals(1, detail.capitulos.size)
    println(" - Movie Chapter Url: ${detail.capitulos[0].url}")
  }

  @Test
  fun testPoseidonHDScraperChapterDetail() = kotlinx.coroutines.runBlocking {
    println("--- START TEST POSEIDONHD SCRAPER CHAPTER DETAIL ---")
    val url = "https://www.poseidonhd2.co/serie/224941/the-boroughs-jubilacion-rebelde/temporada/1/episodio/8"
    val capDetail = com.example.data.PelisPlusScraper.scrapeCapituloDetail(url)
    assertNotNull(capDetail)
    println("Chapter Title: ${capDetail!!.title}")
    println("Chapter Poster: ${capDetail.img}")
    println("Chapter Embeds count: ${capDetail.embedUrls.size}")
    assertTrue(capDetail.embedUrls.isNotEmpty())
    capDetail.embedUrls.forEach { embed ->
        println(" - Raw Embed: $embed")
        val resolved = com.example.data.PelisPlusScraper.resolveDirectVideoIframe(embed)
        val server = com.example.data.PelisPlusScraper.getServerName(resolved)
        println("   -> Resolved iframe Url: $resolved (Server: $server)")
    }
  }

  @Test
  fun testScraperDetail() {
    println("--- START TEST SCRAPER DETAIL ---")
    val url = "https://www3.seriesmetro.net/serie/kurukshetra-the-great-war-of-mahabharata/"
    val detail = SeriesMetroScraper.scrapeSerieDetail(url)
    assertNotNull(detail)
    println("Detail Title: ${detail!!.title}")
    println("Detail Chapters: ${detail.capitulos.size}")
    assertTrue(detail.capitulos.isNotEmpty())
    detail.capitulos.take(3).forEach { cap ->
        println("Chapter: ${cap.title} -> ${cap.url}")
    }
  }

  @Test
  fun testScraperChapter() {
    println("--- START TEST SCRAPER CHAPTER ---")
    val url = "https://www3.seriesmetro.net/capitulo/kurukshetra-the-great-war-of-mahabharata-temporada-1-capitulo-1/"
    val capDetail = SeriesMetroScraper.scrapeCapituloDetail(url)
    assertNotNull(capDetail)
    println("Chapter Title: ${capDetail!!.title}")
    println("Chapter Trid: ${capDetail.trid}")
    println("Chapter Embeds: ${capDetail.embedUrls.size}")
    assertTrue(capDetail.embedUrls.isNotEmpty())
    capDetail.embedUrls.forEach { embed ->
        println("Embed url: $embed")
        assertFalse(embed.contains("&#038;")) // Verify HTML entity escaping works!
    }
  }
}


