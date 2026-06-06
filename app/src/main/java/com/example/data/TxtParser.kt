package com.example.data

import java.io.BufferedReader
import java.io.StringReader

object TxtParser {
    fun parse(content: String, playlistId: Int, classification: String = "GENERAL"): List<DbChannel> {
        val trimmedContent = content.trim()
        
        // 1. If it looks like an M3U content, delegate to M3uParser
        if (trimmedContent.startsWith("#EXTM3U", ignoreCase = true) || trimmedContent.contains("#EXTINF", ignoreCase = true)) {
            return M3uParser.parse(content, playlistId, classification)
        }

        val channels = mutableListOf<DbChannel>()
        val reader = BufferedReader(StringReader(content))
        val lines = mutableListOf<String>()
        var lineRaw = reader.readLine()
        while (lineRaw != null) {
            val trimmed = lineRaw.trim()
            if (trimmed.isNotEmpty()) {
                lines.add(trimmed)
            }
            lineRaw = reader.readLine()
        }

        var i = 0
        while (i < lines.size) {
            val currentLine = lines[i]
            
            // Try to split by common separators first
            val delimiters = listOf("|", ",", "=", " - ")
            var foundDelimiter = false
            for (delim in delimiters) {
                val idx = currentLine.indexOf(delim)
                if (idx != -1) {
                    val part1 = currentLine.substring(0, idx).trim()
                    val part2 = currentLine.substring(idx + delim.length).trim()
                    
                    if (isUrlOrEmbed(part2)) {
                        val isEmbed = isEmbedCode(part2)
                        val group = classificationToGroup(classification, part1)
                        
                        channels.add(
                            DbChannel(
                                streamUrl = part2,
                                name = part1.ifEmpty { "Canal Directo ${channels.size + 1}" },
                                groupTitle = group,
                                logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
                                playlistId = playlistId,
                                originalGroup = classification,
                                isEmbedText = isEmbed,
                                adBlockerEnabled = isEmbed // AdBlocker enabled by default for embed codes!
                            )
                        )
                        foundDelimiter = true
                        break
                    }
                }
            }

            if (!foundDelimiter) {
                // If it is a lone URL or embed code, try to find a name from the previous line if it was not a URL/embed!
                if (isUrlOrEmbed(currentLine)) {
                    val prevLine = if (i > 0) lines[i - 1] else ""
                    val finalName = if (prevLine.isNotEmpty() && !isUrlOrEmbed(prevLine) && prevLine.length < 100) {
                        prevLine
                    } else {
                        "Enlace Extraído ${channels.size + 1}"
                    }
                    
                    val isEmbed = isEmbedCode(currentLine)
                    val group = classificationToGroup(classification, finalName)
                    
                    channels.add(
                        DbChannel(
                            streamUrl = currentLine,
                            name = finalName,
                            groupTitle = group,
                            logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
                            playlistId = playlistId,
                            originalGroup = classification,
                            isEmbedText = isEmbed,
                            adBlockerEnabled = isEmbed
                        )
                    )
                }
            }
            i++
        }
        
        return channels
    }

    private fun isUrlOrEmbed(text: String): Boolean {
        val low = text.trim().lowercase()
        return text.startsWith("http://") || 
               text.startsWith("https://") || 
               text.startsWith("rtmp://") || 
               text.startsWith("rtsp://") || 
               text.startsWith("<") || 
               low.contains("iframe") || 
               low.contains("script") ||
               low.contains("embed")
    }

    private fun isEmbedCode(text: String): Boolean {
        val low = text.trim().lowercase()
        return text.startsWith("<") || 
               low.contains("iframe") || 
               low.contains("script") || 
               low.contains("div class=") || 
               low.contains("video id=")
    }

    private fun classificationToGroup(classification: String, channelName: String): String {
        return when (classification.uppercase()) {
            "PELICULAS", "PELICULA" -> "PELICULA"
            "SERIES" -> "SERIES"
            "KIDS" -> "KIDS"
            "TV" -> "TV"
            "ANIME" -> "ANIME"
            "EXPLORAR" -> "TV"
            "DESTACADOS" -> "TV"
            "AUTOMATIC" -> {
                val groupFromParser = M3uParser.mapGroupToCategory(channelName)
                if (groupFromParser == "TV") {
                    // Try to guess based on name
                    val lowName = channelName.lowercase()
                    if (lowName.contains("pelicula") || lowName.contains("movie") || lowName.contains("cine")) "PELICULA"
                    else if (lowName.contains("serie") || lowName.contains("capitulo") || lowName.contains("temporada")) "SERIES"
                    else "TV"
                } else {
                    groupFromParser
                }
            }
            else -> "TV"
        }
    }
}
