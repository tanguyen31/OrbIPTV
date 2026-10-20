package com.orbit.iptv.data

import java.io.BufferedReader

object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)="([^"]*)"""")
    private val yearRegex = Regex("""\((19|20)\d{2}\)""")

    fun parse(reader: BufferedReader): Catalog {
        val live = ArrayList<Media>()
        val movies = ArrayList<Media>()
        val seen = HashSet<String>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var tvgId: String? = null

        reader.useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val comma = nameComma(line)
                        val meta = if (comma >= 0) line.substring(0, comma) else line
                        name = if (comma >= 0) line.substring(comma + 1).trim() else null
                        logo = null
                        group = null
                        tvgId = null
                        var tvgName: String? = null
                        for (m in attrRegex.findAll(meta)) {
                            val value = m.groupValues[2].trim()
                            when (m.groupValues[1].lowercase()) {
                                "tvg-logo" -> logo = value.nullIfBlank()
                                "group-title" -> group = value.nullIfBlank()
                                "tvg-name" -> tvgName = value.nullIfBlank()
                                "tvg-id" -> tvgId = value.nullIfBlank()
                            }
                        }
                        if (name.isNullOrBlank()) name = tvgName
                    }
                    line.startsWith("#EXTGRP", ignoreCase = true) -> {
                        val g = line.substringAfter(':', "").trim()
                        if (g.isNotEmpty()) group = g
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        val url = line
                        if (seen.add(url)) {
                            val title = name?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/')
                            val isVod = url.contains("/movie/") || url.contains("/series/") ||
                                url.substringBefore('?').let { u ->
                                    u.endsWith(".mp4", true) || u.endsWith(".mkv", true) || u.endsWith(".avi", true)
                                }
                            val media = Media(
                                id = url,
                                type = if (isVod) MediaType.MOVIE else MediaType.LIVE,
                                title = title,
                                poster = logo,
                                category = group ?: DEFAULT_GROUP,
                                url = url,
                                year = yearRegex.find(title)?.value?.trim('(', ')'),
                                num = if (isVod) 0 else live.size + 1,
                            )
                            if (isVod) movies.add(media) else live.add(media)
                        }
                        name = null
                        logo = null
                        group = null
                        tvgId = null
                    }
                }
            }
        }
        return Catalog(
            live = live,
            movies = movies,
            liveCategories = orderCategories(emptyList(), live),
            movieCategories = orderCategories(emptyList(), movies),
        )
    }

    /** Virgule qui sépare les attributs du nom, en ignorant celles entre guillemets. */
    private fun nameComma(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) return i
        }
        return -1
    }
}
