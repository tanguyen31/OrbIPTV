package com.orbit.iptv.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class XtreamClient(server: String, private val user: String, private val pass: String) {

    val base: String = normalize(server)
    private var liveFormat = "m3u8"

    private fun api(action: String? = null, extra: String = ""): String {
        val sb = StringBuilder(base)
            .append("/player_api.php?username=").append(enc(user))
            .append("&password=").append(enc(pass))
        if (action != null) sb.append("&action=").append(action)
        return sb.append(extra).toString()
    }

    fun authenticate() {
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
            liveFormat = when {
                "m3u8" in list -> "m3u8"
                "ts" in list -> "ts"
                else -> "m3u8"
            }
        }
    }

    fun liveStreams(): List<Channel> {
        val cats = categories("get_live_categories")
        val arr = getArray(api("get_live_streams"))
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id")
            if (id.isEmpty()) continue
            out.add(
                Channel(
                    id = "live:$id",
                    name = o.str("name").trim().ifEmpty { "Chaîne $id" },
                    logo = o.str("stream_icon").ifBlank { null },
                    group = cats[o.str("category_id")] ?: DEFAULT_GROUP,
                    url = "$base/live/$user/$pass/$id.$liveFormat",
                    kind = Kind.LIVE,
                    streamId = id,
                )
            )
        }
        return out
    }

    fun movies(): List<Channel> {
        val cats = categories("get_vod_categories")
        val arr = getArray(api("get_vod_streams"))
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("stream_id")
            if (id.isEmpty()) continue
            val ext = o.str("container_extension").ifBlank { "mp4" }
            out.add(
                Channel(
                    id = "movie:$id",
                    name = o.str("name").trim().ifEmpty { "Film $id" },
                    logo = o.str("stream_icon").ifBlank { null },
                    group = cats[o.str("category_id")] ?: DEFAULT_GROUP,
                    url = "$base/movie/$user/$pass/$id.$ext",
                    kind = Kind.MOVIE,
                    streamId = id,
                )
            )
        }
        return out
    }

    /** Programme en cours et suivant, sous la forme "20:00  Titre". */
    fun shortEpg(streamId: String): List<String> {
        val text = Http.getText(api("get_short_epg", "&stream_id=$streamId&limit=2"))
        val arr = try {
            JSONObject(text).optJSONArray("epg_listings")
        } catch (e: JSONException) {
            null
        } ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = decode(o.str("title")).trim()
            if (title.isEmpty()) continue
            val start = o.str("start")
            val hhmm = if (start.length >= 16) start.substring(11, 16) else ""
            out.add("$hhmm  $title".trim())
        }
        return out
    }

    private fun categories(action: String): Map<String, String> {
        val arr = try {
            getArray(api(action))
        } catch (e: IOException) {
            JSONArray()
        }
        val map = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            map[o.str("category_id")] = o.str("category_name").trim().ifBlank { DEFAULT_GROUP }
        }
        return map
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

    private fun decode(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        s
    }

    companion object {
        fun normalize(raw: String): String {
            var s = raw.trim()
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").trimEnd('/')
            if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
                s = "http://$s"
            }
            return s
        }

        private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}

/** optString renvoie "null" pour les valeurs JSON nulles : on corrige ça. */
internal fun JSONObject.str(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key, "")
