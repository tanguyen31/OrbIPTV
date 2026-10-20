package com.orbit.iptv.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class XtreamClient(server: String, private val user: String, private val pass: String) {

    val base: String = normalize(server)
    private var liveExt = "m3u8"

    private fun api(action: String? = null, extra: String = ""): String {
        val sb = StringBuilder(base)
            .append("/player_api.php?username=").append(enc(user))
            .append("&password=").append(enc(pass))
        if (action != null) sb.append("&action=").append(action)
        return sb.append(extra).toString()
    }

    // ---- Compte -------------------------------------------------------------

    fun authenticate(): AccountInfo {
        val text = Http.getText(api())
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("Ce serveur ne répond pas comme un serveur Xtream Codes")
        }
        val info = json.optJSONObject("user_info")
            ?: throw IOException("Réponse inattendue du serveur")
        if (info.optInt("auth", 0) != 1) throw IOException("Identifiant ou mot de passe refusé")
        val status = info.str("status")
        if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
            throw IOException("Abonnement inactif ($status)")
        }
        val formats = info.optJSONArray("allowed_output_formats")
        if (formats != null && formats.length() > 0) {
            val list = (0 until formats.length()).map { formats.optString(it) }
            liveExt = if ("m3u8" in list || "ts" !in list) "m3u8" else "ts"
        }
        return AccountInfo(
            expiry = info.str("exp_date").toLongOrNull()?.let { it * 1000 },
            maxConnections = info.str("max_connections").toIntOrNull(),
            status = status.ifEmpty { "Active" },
        )
    }

    // ---- Catalogue ------------------------------------------------------------

    fun catalog(): Catalog {
        val live = liveStreams()
        val movies = try { movies() } catch (e: IOException) { Pair(emptyList(), emptyList()) }
        val series = try { series() } catch (e: IOException) { Pair(emptyList(), emptyList()) }
        return Catalog(
            live = live.first,
            movies = movies.first,
            series = series.first,
            liveCategories = live.second,
            movieCategories = movies.second,
            seriesCategories = series.second,
        )
    }

    private fun liveStreams(): Pair<List<Media>, List<String>> {
        val (cats, order) = categories("get_live_categories")
        val arr = getArray(api("get_live_streams"))
        val out = ArrayList<Media>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id")
            if (id.isEmpty()) continue
            out.add(
                Media(
                    id = "live:$id",
                    type = MediaType.LIVE,
                    title = o.str("name").trim().ifEmpty { "Chaîne $id" },
                    poster = o.str("stream_icon").nullIfBlank(),
                    category = cats[o.str("category_id")] ?: DEFAULT_GROUP,
                    url = "$base/live/$user/$pass/$id.$liveExt",
                    streamId = id,
                    added = o.str("added").toLongOrNull()?.times(1000) ?: 0L,
                    num = o.str("num").toIntOrNull() ?: (i + 1),
                )
            )
        }
        return out to orderCategories(order, out)
    }

    private fun movies(): Pair<List<Media>, List<String>> {
        val (cats, order) = categories("get_vod_categories")
        val arr = getArray(api("get_vod_streams"))
        val out = ArrayList<Media>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id")
            if (id.isEmpty()) continue
            val ext = o.str("container_extension").ifBlank { "mp4" }
            val title = o.str("name").trim().ifEmpty { "Film $id" }
            out.add(
                Media(
                    id = "movie:$id",
                    type = MediaType.MOVIE,
                    title = title,
                    poster = o.str("stream_icon").nullIfBlank(),
                    category = cats[o.str("category_id")] ?: DEFAULT_GROUP,
                    url = "$base/movie/$user/$pass/$id.$ext",
                    streamId = id,
                    rating = rating(o),
                    year = o.str("year").take(4).nullIfBlank() ?: yearIn(title),
                    added = o.str("added").toLongOrNull()?.times(1000) ?: 0L,
                )
            )
        }
        return out to orderCategories(order, out)
    }

    private fun series(): Pair<List<Media>, List<String>> {
        val (cats, order) = categories("get_series_categories")
        val arr = getArray(api("get_series"))
        val out = ArrayList<Media>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("series_id")
            if (id.isEmpty()) continue
            val title = o.str("name").trim().ifEmpty { "Série $id" }
            val release = o.str("releaseDate").ifBlank { o.str("release_date") }
            out.add(
                Media(
                    id = "series:$id",
                    type = MediaType.SERIES,
                    title = title,
                    poster = o.str("cover").nullIfBlank(),
                    category = cats[o.str("category_id")] ?: DEFAULT_GROUP,
                    streamId = id,
                    rating = rating(o),
                    year = release.take(4).nullIfBlank() ?: yearIn(title),
                    added = o.str("last_modified").toLongOrNull()?.times(1000) ?: 0L,
                    backdrop = firstUrl(o, "backdrop_path"),
                )
            )
        }
        return out to orderCategories(order, out)
    }

    // ---- Fiches ---------------------------------------------------------------

    fun vodInfo(streamId: String): MovieInfo {
        val json = JSONObject(Http.getText(api("get_vod_info", "&vod_id=$streamId")))
        val info = json.optJSONObject("info") ?: JSONObject()
        val secs = info.str("duration_secs").toLongOrNull()
        return MovieInfo(
            plot = info.str("plot").ifBlank { info.str("description") }.nullIfBlank(),
            cast = info.str("cast").ifBlank { info.str("actors") }.nullIfBlank(),
            director = info.str("director").nullIfBlank(),
            genre = info.str("genre").nullIfBlank(),
            duration = formatDuration(info.str("duration"), secs),
            rating = info.str("rating").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
            year = info.str("releasedate").ifBlank { info.str("release_date") }.take(4).nullIfBlank(),
            backdrop = firstUrl(info, "backdrop_path"),
            poster = info.str("movie_image").ifBlank { info.str("cover_big") }.nullIfBlank(),
        )
    }

    fun seriesInfo(seriesId: String): SeriesInfo {
        val json = JSONObject(Http.getText(api("get_series_info", "&series_id=$seriesId")))
        val info = json.optJSONObject("info") ?: JSONObject()
        val bySeason = sortedMapOf<Int, MutableList<Episode>>()

        fun addEpisode(o: JSONObject, seasonHint: Int?, position: Int) {
            val id = o.str("id")
            if (id.isEmpty()) return
            val season = o.str("season").toIntOrNull() ?: seasonHint ?: 1
            val number = o.str("episode_num").toIntOrNull() ?: (position + 1)
            val ext = o.str("container_extension").ifBlank { "mp4" }
            val epInfo = o.optJSONObject("info")
            val secs = epInfo?.str("duration_secs")?.toLongOrNull()
            bySeason.getOrPut(season) { ArrayList() }.add(
                Episode(
                    id = "episode:$id",
                    title = o.str("title").trim().ifEmpty { "Épisode $number" },
                    season = season,
                    number = number,
                    url = "$base/series/$user/$pass/$id.$ext",
                    plot = epInfo?.str("plot")?.nullIfBlank(),
                    image = epInfo?.str("movie_image")?.nullIfBlank(),
                    duration = formatDuration(epInfo?.str("duration").orEmpty(), secs),
                )
            )
        }

        val obj = json.optJSONObject("episodes")
        if (obj != null) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = obj.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, key.toIntOrNull(), i) }
            }
        } else {
            val outer = json.optJSONArray("episodes")
            if (outer != null) {
                for (s in 0 until outer.length()) {
                    val arr = outer.optJSONArray(s) ?: continue
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, s + 1, i) }
                }
            }
        }

        val seasons = LinkedHashMap<Int, List<Episode>>()
        for ((k, v) in bySeason) seasons[k] = v.sortedBy { it.number }
        return SeriesInfo(
            plot = info.str("plot").nullIfBlank(),
            cast = info.str("cast").nullIfBlank(),
            genre = info.str("genre").nullIfBlank(),
            rating = info.str("rating").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
            year = info.str("releaseDate").ifBlank { info.str("release_date") }.take(4).nullIfBlank(),
            backdrop = firstUrl(info, "backdrop_path"),
            poster = info.str("cover").nullIfBlank(),
            seasons = seasons,
        )
    }

    /** Programmes en cours et à venir d'une chaîne. */
    fun shortEpg(streamId: String, limit: Int = 6): List<Program> {
        val text = Http.getText(api("get_short_epg", "&stream_id=$streamId&limit=$limit"))
        val arr = try {
            JSONObject(text).optJSONArray("epg_listings")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<Program>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = decode(o.str("title")).trim()
            val start = o.str("start_timestamp").toLongOrNull()?.times(1000) ?: continue
            val end = o.str("stop_timestamp").toLongOrNull()?.times(1000) ?: continue
            if (title.isEmpty() || end <= now) continue
            out.add(Program(title, decode(o.str("description")).trim().nullIfBlank(), start, end))
        }
        return out.sortedBy { it.start }
    }

    // ---- Outils ---------------------------------------------------------------

    private fun categories(action: String): Pair<Map<String, String>, List<String>> {
        val arr = try {
            getArray(api(action))
        } catch (e: IOException) {
            JSONArray()
        }
        val map = HashMap<String, String>()
        val order = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("category_name").trim().ifBlank { DEFAULT_GROUP }
            map[o.str("category_id")] = name
            order.add(name)
        }
        return map to order
    }

    private fun getArray(url: String): JSONArray {
        val text = Http.getText(url).trim()
        return if (text.startsWith("[")) {
            try {
                JSONArray(text)
            } catch (e: JSONException) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }

    private fun rating(o: JSONObject): Double? {
        val r = o.str("rating").replace(',', '.').toDoubleOrNull()
        if (r != null && r > 0) return r.coerceAtMost(10.0)
        val r5 = o.str("rating_5based").replace(',', '.').toDoubleOrNull()
        return if (r5 != null && r5 > 0) (r5 * 2).coerceAtMost(10.0) else null
    }

    private fun decode(s: String): String {
        if (s.isEmpty()) return s
        return try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            s
        }
    }

    companion object {
        private val YEAR = Regex("""\b(19|20)\d{2}\b""")

        fun normalize(raw: String): String {
            var s = raw.trim()
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").trimEnd('/')
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            return s
        }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        private fun yearIn(title: String): String? = YEAR.findAll(title).lastOrNull()?.value

        private fun firstUrl(o: JSONObject, key: String): String? {
            if (!o.has(key) || o.isNull(key)) return null
            return when (val v = o.opt(key)) {
                is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.startsWith("http") }
                is String -> v.takeIf { it.startsWith("http") }
                else -> null
            }
        }

        private fun formatDuration(raw: String, secs: Long?): String? {
            val total = secs?.takeIf { it > 0 } ?: run {
                val parts = raw.split(':').mapNotNull { it.trim().toLongOrNull() }
                if (parts.size == 3) parts[0] * 3600 + parts[1] * 60 + parts[2] else null
            }
            if (total == null || total <= 0) return raw.nullIfBlank()
            val h = total / 3600
            val m = (total % 3600) / 60
            return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
        }
    }
}

/** optString renvoie "null" pour les valeurs JSON nulles : on corrige ça. */
internal fun JSONObject.str(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")
