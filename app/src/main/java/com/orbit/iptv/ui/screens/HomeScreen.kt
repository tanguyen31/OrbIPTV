package com.orbit.iptv.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.orbit.iptv.data.Catalog
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.WatchProgress
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.openMedia
import com.orbit.iptv.ui.toPlayable
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.ChannelCard
import com.orbit.iptv.ui.components.ContinueCard
import com.orbit.iptv.ui.components.LoadingView
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.MetaLine
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.PosterCard
import com.orbit.iptv.ui.components.SectionRow
import com.orbit.iptv.ui.components.formatRating
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class HomeRow(val key: String, val title: String, val items: List<Media>)

private data class HomeData(
    val featured: List<Media> = emptyList(),
    val continueWatching: List<WatchProgress> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val progressById: Map<String, Float> = emptyMap(),
)

private fun buildHome(
    catalog: Catalog,
    favorites: Set<String>,
    progress: List<WatchProgress>,
    recent: List<String>,
): HomeData {
    val rows = ArrayList<HomeRow>()

    fun newest(list: List<Media>) =
        if (list.any { it.added > 0 }) list.sortedByDescending { it.added } else list

    val featured = newest(catalog.movies).filter { !it.poster.isNullOrBlank() }.take(6)
        .ifEmpty { newest(catalog.series).filter { !it.poster.isNullOrBlank() }.take(6) }
        .ifEmpty { catalog.live.filter { !it.poster.isNullOrBlank() }.take(6) }

    val cw = progress.filter { it.isUnfinished }
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.playable.seriesId ?: it.playable.id }
        .take(15)

    val myList = favorites.mapNotNull { catalog.byId(it) }
    if (myList.isNotEmpty()) rows.add(HomeRow("mylist", "Ma liste", myList.take(30)))

    val recentChannels = recent.mapNotNull { catalog.byId(it) }
    if (recentChannels.isNotEmpty()) rows.add(HomeRow("recent", "Regardées récemment", recentChannels))

    if (catalog.movies.isNotEmpty()) rows.add(HomeRow("newmovies", "Films ajoutés récemment", newest(catalog.movies).take(24)))
    if (catalog.series.isNotEmpty()) rows.add(HomeRow("newseries", "Nouveautés séries", newest(catalog.series).take(24)))

    val topRated = catalog.movies.filter { (it.rating ?: 0.0) >= 7.0 }.sortedByDescending { it.rating }.take(24)
    if (topRated.size >= 6) rows.add(HomeRow("toprated", "Les mieux notés", topRated))

    fun byCategory(list: List<Media>, cats: List<String>, prefix: String, max: Int, keyPrefix: String) {
        val groups = list.groupBy { it.category }
        cats.asSequence()
            .mapNotNull { c -> groups[c]?.takeIf { it.size >= 3 }?.let { c to it } }
            .take(max)
            .forEach { (c, items) -> rows.add(HomeRow("$keyPrefix:$c", "$prefix$c", items.take(24))) }
    }
    byCategory(catalog.live, catalog.liveCategories, "En direct · ", 3, "live")
    byCategory(catalog.movies, catalog.movieCategories, "", 8, "movies")
    byCategory(catalog.series, catalog.seriesCategories, "Séries · ", 5, "series")

    val progressById = progress.filter { it.isUnfinished }.associate { it.playable.id to it.fraction }
    return HomeData(featured, cw, rows.map { r -> r.copy(items = r.items.distinctBy { it.id }) }, progressById)
}

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val favorites by Repo.favorites.collectAsState()
    val progress by Repo.progress.collectAsState()
    val recent by Repo.recentLive.collectAsState()
    val loading by Repo.loading.collectAsState()
    val error by Repo.error.collectAsState()

    val data by produceState(HomeData(), catalog, favorites, progress, recent) {
        value = withContext(Dispatchers.Default) { buildHome(catalog, favorites, progress, recent) }
    }

    if (catalog.isEmpty) {
        if (loading || error == null) {
            LoadingView("Chargement de ton catalogue…")
        } else {
            MessageView(
                Icons.Rounded.CloudOff,
                "Impossible de charger le catalogue",
                error,
                actionLabel = "Réessayer",
                onAction = { Repo.loadCatalog(force = true) },
            )
        }
        return
    }

    val heroFocus = remember { FocusRequester() }
    LaunchedEffect(tv, data.featured.isNotEmpty()) {
        if (tv && data.featured.isNotEmpty()) {
            delay(250)
            runCatching { heroFocus.requestFocus() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        if (data.featured.isNotEmpty()) {
            item(key = "hero") { Hero(data.featured, catalog, heroFocus) }
        }
        if (data.continueWatching.isNotEmpty()) {
            item(key = "continue") {
                SectionRow("Reprendre la lecture", data.continueWatching, key = { it.playable.id }) { p ->
                    ContinueCard(
                        p,
                        onClick = { nav.play(listOf(p.playable), 0, p.positionMs) },
                        modifier = Modifier.width(Dims.wide(tv)),
                        onLongClick = {
                            p.playable.seriesId?.let { nav.open(com.orbit.iptv.ui.Route.SeriesDetail(it)) }
                        },
                    )
                }
            }
        }
        items(data.rows, key = { it.key }) { row ->
            SectionRow(row.title, row.items, key = { it.id }) { m ->
                MediaCard(m, data.progressById[m.id], onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
            }
        }
    }
}

/** Vignette adaptée au type : chaîne en 16:9, film ou série en affiche. */
@Composable
fun MediaCard(media: Media, progress: Float?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tv = LocalTv.current
    if (media.type == MediaType.LIVE) {
        ChannelCard(media, onClick, Modifier.width(Dims.channel(tv)), onLongClick)
    } else {
        PosterCard(media, onClick, Modifier.width(Dims.poster(tv)), onLongClick, progress)
    }
}

private data class HeroDetails(val backdrop: String?, val plot: String?, val meta: List<String>)

private suspend fun heroDetails(m: Media): HeroDetails = when (m.type) {
    MediaType.MOVIE -> Repo.movieInfo(m).let { i ->
        HeroDetails(
            i?.backdrop ?: m.backdrop,
            i?.plot,
            listOfNotNull(i?.year ?: m.year, i?.duration, (i?.rating ?: m.rating)?.let { "★ ${formatRating(it)}" }, i?.genre),
        )
    }
    MediaType.SERIES -> Repo.seriesInfo(m).let { i ->
        HeroDetails(
            i?.backdrop ?: m.backdrop,
            i?.plot,
            listOfNotNull(
                i?.year ?: m.year,
                i?.seasons?.size?.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
                (i?.rating ?: m.rating)?.let { "★ ${formatRating(it)}" },
                i?.genre,
            ),
        )
    }
    else -> HeroDetails(null, null, listOf(m.category))
}

/** Grande bannière animée en tête de l'accueil. */
@Composable
private fun Hero(items: List<Media>, catalog: Catalog, focusRequester: FocusRequester) {
    val tv = LocalTv.current
    val nav = LocalNav.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val current = items[index.coerceIn(0, items.lastIndex)]

    LaunchedEffect(items, hasFocus) {
        while (items.size > 1 && !hasFocus) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }
    val details by produceState<HeroDetails?>(null, current.id) { value = heroDetails(current) }
    val image = details?.backdrop ?: current.backdrop ?: current.poster
    val edge = Dims.edge(tv)

    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tv) 460.dp else 400.dp),
    ) {
        Crossfade(targetState = image, label = "heroImage") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Orbit.Surface),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Orbit.Bg.copy(alpha = 0.35f), 1f to Orbit.Bg)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to Orbit.Bg.copy(alpha = if (tv) 0.92f else 0.55f), 0.65f to Color.Transparent)),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (tv) 0.52f else 1f)
                .padding(start = edge, end = edge, bottom = 18.dp),
        ) {
            Text(
                when (current.type) {
                    MediaType.MOVIE -> "FILM"
                    MediaType.SERIES -> "SÉRIE"
                    else -> "EN DIRECT"
                },
                color = Orbit.Accent,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                current.title,
                fontSize = if (tv) 40.sp else 30.sp,
                lineHeight = if (tv) 46.sp else 36.sp,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            details?.meta?.let {
                Spacer(Modifier.height(8.dp))
                MetaLine(it)
            }
            details?.plot?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Orbit.Text.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OrbitButton(
                    text = if (current.type == MediaType.SERIES) "Voir la série" else "Lecture",
                    icon = Icons.Rounded.PlayArrow,
                    primary = true,
                    focusRequester = focusRequester,
                    onClick = {
                        if (current.type == MediaType.MOVIE) {
                            val p = current.toPlayable()
                            if (p != null) nav.play(listOf(p), 0, Repo.progressFor(current.id)?.takeIf { it.isUnfinished }?.positionMs ?: 0L)
                        } else {
                            nav.openMedia(current, catalog)
                        }
                    },
                )
                if (current.type != MediaType.LIVE) {
                    OrbitButton(
                        text = "Plus d'infos",
                        icon = Icons.Rounded.Info,
                        onClick = { nav.openMedia(current, catalog) },
                    )
                }
            }
        }

        if (items.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = edge, bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(width = if (i == index) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (i == index) Orbit.Text else Orbit.Text.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}
