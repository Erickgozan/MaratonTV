package com.maratonTv.data.remote.parsers

import com.maratonTv.data.local.entities.DbChannel
import java.io.BufferedReader
import java.io.StringReader
import java.io.Reader

object M3uParser {
    fun parse(content: String, playlistId: Int, classification: String = "GENERAL"): List<DbChannel> {
        return parse(StringReader(content), playlistId, classification)
    }

    fun parse(reader: Reader, playlistId: Int, classification: String = "GENERAL"): List<DbChannel> {
        val channels = mutableListOf<DbChannel>()
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        var line: String? = bufferedReader.readLine()
        
        var currentLogo = ""
        var currentGroup = ""
        var currentName = ""

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse #EXTINF attributes
                currentLogo = extractAttribute(trimmed, "tvg-logo")
                if (currentLogo.isEmpty()) {
                    currentLogo = extractAttribute(trimmed, "logo")
                }
                if (currentLogo.isEmpty()) {
                    currentLogo = extractAttribute(trimmed, "logo-url")
                }
                if (currentLogo.isEmpty()) {
                    currentLogo = extractAttribute(trimmed, "icon")
                }
                
                // Fallback for groups
                currentGroup = extractAttribute(trimmed, "group-title")
                if (currentGroup.isEmpty()) {
                    currentGroup = "TV" // Default
                }

                // Extract channel name (after last comma)
                val commaIndex = trimmed.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Canal " + (channels.size + 1)
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // This is a stream URL line
                if (currentName.isNotEmpty()) {
                    val finalGroup = when (classification.uppercase()) {
                        "PELICULAS" -> "PELICULA"
                        "TV" -> "TV"
                        "SERIES" -> "SERIES"
                        "KIDS" -> "KIDS"
                        "PELICULAS_SERIES" -> {
                            val nameLower = currentName.lowercase()
                            val groupLower = currentGroup.lowercase()
                            if (nameLower.contains("temporada") || nameLower.contains("capitulo") || nameLower.contains("serie") || nameLower.contains("episodio") || nameLower.contains("s01") || nameLower.contains("s02") || nameLower.contains("ep ") ||
                                groupLower.contains("serie") || groupLower.contains("show") || groupLower.contains("temporada")
                            ) {
                                "SERIES"
                            } else {
                                "PELICULA"
                            }
                        }
                        else -> mapGroupToCategory(currentGroup)
                    }

                    channels.add(
                        DbChannel(
                            streamUrl = trimmed,
                            name = currentName,
                            groupTitle = finalGroup,
                            logoUrl = currentLogo,
                            playlistId = playlistId,
                            originalGroup = currentGroup.ifEmpty { "TV" }
                        )
                    )
                }
                // Reset temporary fields
                currentLogo = ""
                currentGroup = ""
                currentName = ""
            }
            line = bufferedReader.readLine()
        }
        return channels
    }

    private fun extractAttribute(line: String, key: String): String {
        val keyWithEquals = "$key=\""
        val startIndex = line.indexOf(keyWithEquals)
        if (startIndex == -1) return ""
        val valueStart = startIndex + keyWithEquals.length
        val endIndex = line.indexOf('"', valueStart)
        if (endIndex == -1) return ""
        return line.substring(valueStart, endIndex)
    }

    // Map any custom category name to our canonical side-menu groups:
    // "TV", "PELICULA", "SERIES", "KIDS", "ANIME"
    fun mapGroupToCategory(group: String): String {
        val upper = group.uppercase()
        return when {
            upper.contains("DESTACADO") || upper.contains("FAV") || upper.contains("POPULAR") -> "TV"
            upper.contains("MOVIE") || upper.contains("PELI") || upper.contains("CINE") -> "PELICULA"
            upper.contains("SERIE") || upper.contains("SHOW") -> "SERIES"
            upper.contains("KIDS") || upper.contains("NINO") || upper.contains("INFANTIL") || upper.contains("CARTOON") -> "KIDS"
            upper.contains("ANIME") || upper.contains("MANGA") -> "ANIME"
            upper.contains("EXPLORA") || upper.contains("NEWS") || upper.contains("DOCUMENTAL") -> "TV"
            else -> "TV"
        }
    }
}
