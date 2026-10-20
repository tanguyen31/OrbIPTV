package com.orbit.iptv.data

import java.io.BufferedReader

object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null

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
                        var tvgName: String? = null
                        for (m in attrRegex.findAll(meta)) {
                            val value = m.groupValues[2].trim()
                            when (m.groupValues[1].lowercase()) {
                                "tvg-logo" -> logo = value.ifBlank { null }
                                "group-title" -> group = value.ifBlank { null }
                                "tvg-name" -> tvgName = value.ifBlank { null }
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
                        val n = name?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/')
                        val kind = if (url.contains("/movie/") || url.contains("/series/")) Kind.MOVIE else Kind.LIVE
                        out.add(
                            Channel(
                                id = url,
                                name = n,
                                logo = logo,
                                group = group ?: DEFAULT_GROUP,
                                url = url,
                                kind = kind,
                            )
                        )
                        name = null
                        logo = null
                        group = null
                    }
                }
            }
        }
        return out
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
