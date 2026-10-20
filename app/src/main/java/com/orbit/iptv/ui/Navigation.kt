package com.orbit.iptv.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.orbit.iptv.data.Catalog
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.components.toast

sealed interface Route {
    data object Home : Route
    data object Live : Route
    data object Movies : Route
    data object Series : Route
    data object MyList : Route
    data object Search : Route
    data object Settings : Route
    data object AddSource : Route
    data class MovieDetail(val id: String) : Route
    data class SeriesDetail(val id: String) : Route
}

class Nav(
    val open: (Route) -> Unit,
    val back: () -> Unit,
    val play: (queue: List<Playable>, index: Int, startMs: Long) -> Unit,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav non fourni") }

fun Media.toPlayable(): Playable? {
    val u = url ?: return null
    return Playable(
        id = id,
        type = type,
        title = title,
        subtitle = category,
        url = u,
        poster = if (type == MediaType.MOVIE) backdrop ?: poster else poster,
        streamId = streamId,
        category = category,
    )
}

/** Ouvre un élément : lecture directe pour une chaîne, fiche pour un film ou une série. */
fun Nav.openMedia(media: Media, catalog: Catalog) {
    when (media.type) {
        MediaType.LIVE -> {
            val list = catalog.live.filter { it.category == media.category }.mapNotNull { it.toPlayable() }
            val queue = list.ifEmpty { listOfNotNull(media.toPlayable()) }
            play(queue, queue.indexOfFirst { it.id == media.id }.coerceAtLeast(0), 0L)
        }
        MediaType.MOVIE -> open(Route.MovieDetail(media.id))
        MediaType.SERIES -> open(Route.SeriesDetail(media.id))
        MediaType.EPISODE -> Unit
    }
}

fun Context.toggleFavorite(media: Media) {
    val added = Repo.toggleFavorite(media.id)
    toast(if (added) "« ${media.title} » ajouté à Ma liste" else "« ${media.title} » retiré de Ma liste")
}
