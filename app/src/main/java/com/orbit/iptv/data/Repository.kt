package com.orbit.iptv.data

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.IOException

object Repository {
    private lateinit var prefs: SharedPreferences
    private lateinit var cacheFile: File
    private val favorites = LinkedHashSet<String>()

    @Volatile
    var channels: List<Channel> = emptyList()

    /** Liste en cours de lecture, pour zapper dans le lecteur. */
    @Volatile
    var playQueue: List<Channel> = emptyList()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("orbit", Context.MODE_PRIVATE)
        cacheFile = File(context.filesDir, "channels.json")
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
    }

    // ---- Source -----------------------------------------------------------

    fun source(): Source? = when (prefs.getString("type", null)) {
        "m3u" -> prefs.getString("m3u_url", null)?.takeIf { it.isNotBlank() }?.let { Source.M3u(it) }
        "xtream" -> {
            val server = prefs.getString("server", "").orEmpty()
            val user = prefs.getString("user", "").orEmpty()
            val pass = prefs.getString("pass", "").orEmpty()
            if (server.isNotBlank()) Source.Xtream(server, user, pass) else null
        }
        else -> null
    }

    fun saveSource(source: Source) {
        val e = prefs.edit().clear().putStringSet("favorites", HashSet(favorites))
        when (source) {
            is Source.M3u -> e.putString("type", "m3u").putString("m3u_url", source.url)
            is Source.Xtream -> e.putString("type", "xtream")
                .putString("server", source.server)
                .putString("user", source.user)
                .putString("pass", source.pass)
        }
        e.apply()
        channels = emptyList()
        cacheFile.delete()
    }

    fun xtreamClient(): XtreamClient? =
        (source() as? Source.Xtream)?.let { XtreamClient(it.server, it.user, it.pass) }

    // ---- Chargement (à appeler hors du thread principal) ----------------------

    fun fetch(source: Source): List<Channel> = when (source) {
        is Source.M3u -> {
            val c = Http.open(source.url)
            try {
                M3uParser.parse(c.inputStream.bufferedReader(Charsets.UTF_8))
            } finally {
                c.disconnect()
            }
        }
        is Source.Xtream -> {
            val client = XtreamClient(source.server, source.user, source.pass)
            client.authenticate()
            val live = client.liveStreams()
            val movies = try {
                client.movies()
            } catch (e: IOException) {
                emptyList()
            }
            live + movies
        }
    }

    fun saveCache() {
        val list = channels
        val tmp = File(cacheFile.path + ".tmp")
        JsonWriter(tmp.bufferedWriter(Charsets.UTF_8)).use { w ->
            w.beginArray()
            for (c in list) {
                w.beginObject()
                w.name("i").value(c.id)
                w.name("n").value(c.name)
                w.name("l").value(c.logo)
                w.name("g").value(c.group)
                w.name("u").value(c.url)
                w.name("k").value(c.kind.name)
                w.name("s").value(c.streamId)
                w.endObject()
            }
            w.endArray()
        }
        tmp.renameTo(cacheFile)
    }

    fun readCache(): List<Channel> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            JsonReader(cacheFile.bufferedReader(Charsets.UTF_8)).use { r ->
                val out = ArrayList<Channel>()
                r.beginArray()
                while (r.hasNext()) {
                    var id = ""
                    var name = ""
                    var logo: String? = null
                    var group = DEFAULT_GROUP
                    var url = ""
                    var kind = Kind.LIVE
                    var streamId: String? = null
                    r.beginObject()
                    while (r.hasNext()) {
                        val key = r.nextName()
                        if (r.peek() == JsonToken.NULL) {
                            r.nextNull()
                            continue
                        }
                        when (key) {
                            "i" -> id = r.nextString()
                            "n" -> name = r.nextString()
                            "l" -> logo = r.nextString()
                            "g" -> group = r.nextString()
                            "u" -> url = r.nextString()
                            "k" -> {
                                val k = r.nextString()
                                kind = if (k == Kind.MOVIE.name) Kind.MOVIE else Kind.LIVE
                            }
                            "s" -> streamId = r.nextString()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (url.isNotEmpty()) out.add(Channel(id, name, logo, group, url, kind, streamId))
                }
                r.endArray()
                out
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- Favoris ------------------------------------------------------------

    @Synchronized
    fun favoriteIds(): Set<String> = HashSet(favorites)

    @Synchronized
    fun isFavorite(id: String): Boolean = id in favorites

    /** Retourne true si la chaîne est maintenant en favori. */
    @Synchronized
    fun toggleFavorite(id: String): Boolean {
        val added = if (id in favorites) {
            favorites.remove(id)
            false
        } else {
            favorites.add(id)
            true
        }
        prefs.edit().putStringSet("favorites", HashSet(favorites)).apply()
        return added
    }
}
