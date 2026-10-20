package com.orbit.iptv.data

enum class MediaType { LIVE, MOVIE, SERIES, EPISODE }

/** Un élément du catalogue : chaîne, film ou série. */
data class Media(
    val id: String,
    val type: MediaType,
    val title: String,
    val poster: String?,
    val category: String,
    val url: String? = null,
    val streamId: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val added: Long = 0L,
    val backdrop: String? = null,
    val num: Int = 0,
)

data class Catalog(
    val live: List<Media> = emptyList(),
    val movies: List<Media> = emptyList(),
    val series: List<Media> = emptyList(),
    val liveCategories: List<String> = emptyList(),
    val movieCategories: List<String> = emptyList(),
    val seriesCategories: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()

    private val index: Map<String, Media> by lazy {
        val m = HashMap<String, Media>(live.size + movies.size + series.size)
        for (x in live) m[x.id] = x
        for (x in movies) m[x.id] = x
        for (x in series) m[x.id] = x
        m
    }

    fun byId(id: String): Media? = index[id]

    fun withoutAdult(): Catalog {
        fun ok(c: String) = !ADULT.containsMatchIn(c)
        return Catalog(
            live = live.filter { ok(it.category) },
            movies = movies.filter { ok(it.category) },
            series = series.filter { ok(it.category) },
            liveCategories = liveCategories.filter(::ok),
            movieCategories = movieCategories.filter(::ok),
            seriesCategories = seriesCategories.filter(::ok),
        )
    }

    companion object {
        private val ADULT = Regex("""(?i)(adult|adulte|xxx|porn|\+ ?18|18 ?\+|for adults|hot ?club)""")
    }
}

enum class SourceKind { XTREAM, M3U }

data class Source(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val user: String = "",
    val pass: String = "",
    val expiry: Long? = null,
    val maxConnections: Int? = null,
)

data class AccountInfo(val expiry: Long?, val maxConnections: Int?, val status: String)

data class MovieInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val duration: String?,
    val rating: Double?,
    val year: String?,
    val backdrop: String?,
    val poster: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
)

data class SeriesInfo(
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val rating: Double?,
    val year: String?,
    val backdrop: String?,
    val poster: String?,
    val seasons: Map<Int, List<Episode>>,
)

/** Programme du guide TV, heures en millisecondes. */
data class Program(val title: String, val description: String?, val start: Long, val end: Long) {
    val isLive: Boolean get() = System.currentTimeMillis().let { it in start until end }
    val progress: Float
        get() {
            val now = System.currentTimeMillis()
            if (end <= start) return 0f
            return ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        }
}

fun List<Program>.nowPlaying(): Program? = firstOrNull { it.isLive }

/** Ce que le lecteur sait lire. */
data class Playable(
    val id: String,
    val type: MediaType,
    val title: String,
    val subtitle: String?,
    val url: String,
    val poster: String?,
    val streamId: String? = null,
    val seriesId: String? = null,
    val category: String? = null,
)

data class WatchProgress(
    val playable: Playable,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isUnfinished: Boolean get() = fraction in 0.01f..0.95f
}

data class Settings(
    val autoplayNext: Boolean = true,
    /** 0 = ajusté, 1 = remplir, 2 = zoom */
    val resizeMode: Int = 0,
    val hideAdult: Boolean = true,
    val pin: String? = null,
)

const val DEFAULT_GROUP = "Sans catégorie"

internal fun String.nullIfBlank(): String? = if (isBlank()) null else this

/** Ordre des catégories : celui du serveur, puis celles rencontrées en plus. */
internal fun orderCategories(serverOrder: List<String>, items: List<Media>): List<String> {
    val used = LinkedHashSet<String>()
    for (m in items) used.add(m.category)
    val out = ArrayList<String>(used.size)
    val seen = HashSet<String>()
    for (c in serverOrder) if (c in used && seen.add(c)) out.add(c)
    for (c in used) if (seen.add(c)) out.add(c)
    return out
}
