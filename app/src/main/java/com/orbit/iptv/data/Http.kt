package com.orbit.iptv.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    const val USER_AGENT = "OrbitIPTV/1.0 (Linux; Android)"

    /** Ouvre une connexion en suivant les redirections, y compris http <-> https. */
    fun open(url: String): HttpURLConnection {
        var current = url
        for (attempt in 0 until 6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", USER_AGENT)
            val code = c.responseCode
            when {
                code in 300..399 -> {
                    val location = c.getHeaderField("Location")
                    c.disconnect()
                    if (location.isNullOrBlank()) throw IOException("Redirection invalide")
                    current = URL(URL(current), location).toString()
                }
                code in 200..299 -> return c
                else -> {
                    c.disconnect()
                    throw IOException(
                        when (code) {
                            401, 403 -> "Accès refusé par le serveur (HTTP $code)"
                            404 -> "Adresse introuvable (HTTP 404)"
                            else -> "Erreur du serveur (HTTP $code)"
                        }
                    )
                }
            }
        }
        throw IOException("Trop de redirections")
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
