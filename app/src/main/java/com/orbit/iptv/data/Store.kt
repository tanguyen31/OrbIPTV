package com.orbit.iptv.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persistance locale : sources, favoris, historique, réglages et cache du catalogue. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("orbit2", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("orbit", Context.MODE_PRIVATE)
    private val dir: File = context.filesDir

    // ---- Migration depuis la version 1 ---------------------------------------

    fun migrateV1() {
        if (prefs.getBoolean("migrated", false)) return
        val e = prefs.edit().putBoolean("migrated", true)
        when (legacy.getString("type", null)) {
            "xtream" -> {
                val server = legacy.getString("server", "").orEmpty()
                if (server.isNotBlank()) {
                    val s = Source(
                        id = UUID.randomUUID().toString(), name = "Ma source", kind = SourceKind.XTREAM, url = server,
                        user = legacy.getString("user", "").orEmpty(), pass = legacy.getString("pass", "").orEmpty(),
                    )
                    e.putString("sources", JSONArray().put(sourceToJson(s)).toString()).putString("active", s.id)
                }
            }
            "m3u" -> {
                val url = legacy.getString("m3u_url", "").orEmpty()
                if (url.isNotBlank()) {
                    val s = Source(UUID.randomUUID().toString(), "Ma playlist", SourceKind.M3U, url)
                    e.putString("sources", JSONArray().put(sourceToJson(s)).toString()).putString("active", s.id)
                }
            }
        }
        e.apply()
    }

    // ---- Sources ------------------------------------------------------------

    fun sources(): List<Source> {
        val arr = runCatching { JSONArray(prefs.getString("sources", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::sourceFromJson) }
    }

    fun saveSources(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { arr.put(sourceToJson(it)) }
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    var activeId: String?
        get() = prefs.getString("active", null)
        set(value) = prefs.edit().putString("active", value).apply()

    private fun sourceToJson(s: Source) = JSONObject()
        .put("id", s.id).put("name", s.name).put("kind", s.kind.name).put("url", s.url)
        .put("user", s.user).put("pass", s.pass)
        .put("expiry", s.expiry ?: JSONObject.NULL).put("max", s.maxConnections ?: JSONObject.NULL)

    private fun sourceFromJson(o: JSONObject): Source? {
        val id = o.str("id").nullIfBlank() ?: return null
        return Source(
            id = id,
            name = o.str("name").ifBlank { "Source" },
            kind = if (o.str("kind") == SourceKind.M3U.name) SourceKind.M3U else SourceKind.XTREAM,
            url = o.str("url"),
            user = o.str("user"),
            pass = o.str("pass"),
            expiry = o.str("expiry").toLongOrNull(),
            maxConnections = o.str("max").toIntOrNull(),
        )
    }

    // ---- Favoris et chaînes récentes -------------------------------------------

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun saveFavorites(set: Set<String>) = prefs.edit().putStringSet("favorites", HashSet(set)).apply()

    fun recentLive(): List<String> = prefs.getString("recent_live", "")!!.split('\n').filter { it.isNotBlank() }
    fun saveRecentLive(list: List<String>) = prefs.edit().putString("recent_live", list.joinToString("\n")).apply()

    // ---- Historique de lecture --------------------------------------------------

    fun progress(): List<WatchProgress> {
        val arr = runCatching { JSONArray(prefs.getString("progress", "[]")) }.getOrElse { JSONArray() }
        val out = ArrayList<WatchProgress>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.str("url").nullIfBlank() ?: continue
            val type = runCatching { MediaType.valueOf(o.str("type")) }.getOrDefault(MediaType.MOVIE)
            out.add(
                WatchProgress(
                    playable = Playable(
                        id = o.str("id"), type = type, title = o.str("title"), subtitle = o.str("sub").nullIfBlank(),
                        url = url, poster = o.str("poster").nullIfBlank(), streamId = o.str("sid").nullIfBlank(),
                        seriesId = o.str("ser").nullIfBlank(), category = o.str("cat").nullIfBlank(),
                    ),
                    positionMs = o.optLong("pos"),
                    durationMs = o.optLong("dur"),
                    updatedAt = o.optLong("at"),
                )
            )
        }
        return out
    }

    fun saveProgress(list: List<WatchProgress>) {
        val arr = JSONArray()
        for (w in list) {
            val p = w.playable
            arr.put(
                JSONObject()
                    .put("id", p.id).put("type", p.type.name).put("title", p.title).put("sub", p.subtitle ?: "")
                    .put("url", p.url).put("poster", p.poster ?: "").put("sid", p.streamId ?: "")
                    .put("ser", p.seriesId ?: "").put("cat", p.category ?: "")
                    .put("pos", w.positionMs).put("dur", w.durationMs).put("at", w.updatedAt)
            )
        }
        prefs.edit().putString("progress", arr.toString()).apply()
    }

    // ---- Réglages ---------------------------------------------------------------

    fun settings() = Settings(
        autoplayNext = prefs.getBoolean("autoplay", true),
        resizeMode = prefs.getInt("resize", 0),
        hideAdult = prefs.getBoolean("hide_adult", true),
        pin = prefs.getString("pin", null),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putBoolean("autoplay", s.autoplayNext)
            .putInt("resize", s.resizeMode)
            .putBoolean("hide_adult", s.hideAdult)
            .putString("pin", s.pin)
            .apply()
    }

    // ---- Cache du catalogue --------------------------------------------------------

    private fun catalogFile(sourceId: String) = File(dir, "catalog_$sourceId.json")

    fun deleteCatalog(sourceId: String) {
        catalogFile(sourceId).delete()
    }

    fun writeCatalog(sourceId: String, c: Catalog) {
        val file = catalogFile(sourceId)
        val tmp = File(file.path + ".tmp")
        JsonWriter(tmp.bufferedWriter(Charsets.UTF_8)).use { w ->
            w.beginObject()
            writeList(w, "live", c.live)
            writeList(w, "movies", c.movies)
            writeList(w, "series", c.series)
            writeStrings(w, "lc", c.liveCategories)
            writeStrings(w, "mc", c.movieCategories)
            writeStrings(w, "sc", c.seriesCategories)
            w.endObject()
        }
        tmp.renameTo(file)
    }

    private fun writeStrings(w: JsonWriter, name: String, list: List<String>) {
        w.name(name).beginArray()
        list.forEach { w.value(it) }
        w.endArray()
    }

    private fun writeList(w: JsonWriter, name: String, list: List<Media>) {
        w.name(name).beginArray()
        for (m in list) {
            w.beginObject()
            w.name("i").value(m.id)
            w.name("n").value(m.title)
            w.name("c").value(m.category)
            if (m.poster != null) w.name("p").value(m.poster)
            if (m.url != null) w.name("u").value(m.url)
            if (m.streamId != null) w.name("s").value(m.streamId)
            if (m.rating != null) w.name("r").value(m.rating)
            if (m.year != null) w.name("y").value(m.year)
            if (m.added != 0L) w.name("a").value(m.added)
            if (m.backdrop != null) w.name("b").value(m.backdrop)
            if (m.num != 0) w.name("m").value(m.num.toLong())
            w.endObject()
        }
        w.endArray()
    }

    fun readCatalog(sourceId: String): Catalog {
        val file = catalogFile(sourceId)
        if (!file.exists()) return Catalog()
        return try {
            JsonReader(file.bufferedReader(Charsets.UTF_8)).use { r ->
                var live: List<Media> = emptyList()
                var movies: List<Media> = emptyList()
                var series: List<Media> = emptyList()
                var lc: List<String> = emptyList()
                var mc: List<String> = emptyList()
                var sc: List<String> = emptyList()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "live" -> live = readList(r, MediaType.LIVE)
                        "movies" -> movies = readList(r, MediaType.MOVIE)
                        "series" -> series = readList(r, MediaType.SERIES)
                        "lc" -> lc = readStrings(r)
                        "mc" -> mc = readStrings(r)
                        "sc" -> sc = readStrings(r)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                Catalog(live, movies, series, lc, mc, sc)
            }
        } catch (e: Exception) {
            Catalog()
        }
    }

    private fun readStrings(r: JsonReader): List<String> {
        val out = ArrayList<String>()
        r.beginArray()
        while (r.hasNext()) out.add(r.nextString())
        r.endArray()
        return out
    }

    private fun readList(r: JsonReader, type: MediaType): List<Media> {
        val out = ArrayList<Media>()
        r.beginArray()
        while (r.hasNext()) {
            var id = ""
            var title = ""
            var category = DEFAULT_GROUP
            var poster: String? = null
            var url: String? = null
            var streamId: String? = null
            var rating: Double? = null
            var year: String? = null
            var added = 0L
            var backdrop: String? = null
            var num = 0
            r.beginObject()
            while (r.hasNext()) {
                val key = r.nextName()
                if (r.peek() == JsonToken.NULL) {
                    r.nextNull()
                    continue
                }
                when (key) {
                    "i" -> id = r.nextString()
                    "n" -> title = r.nextString()
                    "c" -> category = r.nextString()
                    "p" -> poster = r.nextString()
                    "u" -> url = r.nextString()
                    "s" -> streamId = r.nextString()
                    "r" -> rating = r.nextDouble()
                    "y" -> year = r.nextString()
                    "a" -> added = r.nextLong()
                    "b" -> backdrop = r.nextString()
                    "m" -> num = r.nextInt()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (id.isNotEmpty()) out.add(Media(id, type, title, poster, category, url, streamId, rating, year, added, backdrop, num))
        }
        r.endArray()
        return out
    }
}
