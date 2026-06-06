package com.maratonTv.data.remote.parsers

import android.util.LruCache
import com.maratonTv.data.model.SeriesEpisodeInfo
import com.maratonTv.data.model.UiChannel

object SeriesEpisodeParser {
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

    private val parseSeriesEpisodeCache = LruCache<String, SeriesEpisodeInfo>(2000)
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

    private fun parseSeriesEpisodeUncached(channel: UiChannel): SeriesEpisodeInfo? {
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
}
