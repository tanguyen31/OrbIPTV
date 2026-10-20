package com.orbit.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.MovieInfo
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.openMedia
import com.orbit.iptv.ui.toPlayable
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.MetaLine
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.PosterCard
import com.orbit.iptv.ui.components.RoundIconButton
import com.orbit.iptv.ui.components.SectionRow
import com.orbit.iptv.ui.components.formatRating
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.delay

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatRemaining(ms: Long): String {
    val min = (ms / 60000).coerceAtLeast(1)
    return if (min >= 60) "${min / 60} h ${(min % 60).toString().padStart(2, '0')} restantes" else "$min min restantes"
}

@Composable
fun MovieDetailScreen(id: String) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val favorites by Repo.favorites.collectAsState()
    val progressList by Repo.progress.collectAsState()
    val media = catalog.byId(id)
    if (media == null) {
        MessageView(Icons.Rounded.ErrorOutline, "Contenu introuvable", "Il a peut-être été retiré de ta source.", actionLabel = "Retour", onAction = nav.back)
        return
    }
    val info by produceState<MovieInfo?>(null, id) { value = Repo.movieInfo(media) }
    val prog = progressList.firstOrNull { it.playable.id == id }?.takeIf { it.isUnfinished }
    val isFav = id in favorites
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(id) {
        if (tv) {
            delay(250)
            runCatching { playFocus.requestFocus() }
        }
    }
    val similar = remember(catalog, id) { catalog.movies.filter { it.category == media.category && it.id != id }.take(24) }
    val backdrop = info?.backdrop ?: media.backdrop ?: info?.poster ?: media.poster
    val edge = Dims.edge(tv)
    val heroHeight = if (tv) 520.dp else 320.dp

    fun play(fromStart: Boolean) {
        val p = media.toPlayable() ?: return
        val withImage = if (backdrop != null) p.copy(poster = backdrop) else p
        nav.play(listOf(withImage), 0, if (fromStart) 0L else prog?.positionMs ?: 0L)
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop, heroHeight, tv)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "header") {
                Column(Modifier.padding(start = edge, end = edge, top = if (tv) 150.dp else 190.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (tv) {
                            PosterImage(info?.poster ?: media.poster, 190.dp)
                            Spacer(Modifier.width(28.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                media.title,
                                style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            MetaLine(
                                listOfNotNull(
                                    info?.year ?: media.year,
                                    info?.duration,
                                    (info?.rating ?: media.rating)?.let { "★ ${formatRating(it)}" },
                                    info?.genre,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrbitButton(
                            text = if (prog != null) "Reprendre" else "Lecture",
                            icon = Icons.Rounded.PlayArrow,
                            primary = true,
                            focusRequester = playFocus,
                            onClick = { play(fromStart = false) },
                        )
                        if (prog != null) {
                            OrbitButton("Depuis le début", { play(fromStart = true) }, icon = Icons.Rounded.Replay)
                        }
                        OrbitButton(
                            text = "Ma liste",
                            icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add,
                            onClick = { context.toggleFavorite(media) },
                        )
                    }
                    if (prog != null) {
                        Text(
                            formatRemaining(prog.durationMs - prog.positionMs),
                            color = Orbit.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    info?.plot?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Orbit.Text.copy(alpha = 0.88f),
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .fillMaxWidth(if (tv) 0.7f else 1f),
                        )
                    }
                    info?.director?.let { Credit("Réalisation", it, tv) }
                    info?.cast?.let { Credit("Avec", it, tv) }
                }
            }
            if (similar.isNotEmpty()) {
                item(key = "similar") {
                    SectionRow("Dans la même catégorie", similar, key = { it.id }, modifier = Modifier.padding(top = 18.dp)) { m ->
                        PosterCard(m, onClick = { nav.openMedia(m, catalog) }, modifier = Modifier.width(Dims.poster(tv)), onLongClick = { context.toggleFavorite(m) })
                    }
                }
            }
        }
        if (!tv) {
            RoundIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                "Retour",
                onClick = nav.back,
                modifier = Modifier.padding(12.dp),
                size = 42.dp,
            )
        }
    }
}

@Composable
fun Credit(label: String, value: String, tv: Boolean) {
    Text(
        "$label : $value",
        color = Orbit.Muted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth(if (tv) 0.7f else 1f),
    )
}

/** Image de fond plein cadre avec dégradés vers le fond de l'écran. */
@Composable
fun Backdrop(url: String?, height: Dp, tv: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Orbit.Bg.copy(alpha = 0.15f), 0.6f to Orbit.Bg.copy(alpha = 0.55f), 1f to Orbit.Bg)),
        )
        if (tv) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(0f to Orbit.Bg.copy(alpha = 0.9f), 0.7f to Color.Transparent)),
            )
        }
    }
}

@Composable
fun PosterImage(url: String?, width: Dp) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(Orbit.SurfaceHigh),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Suppress("unused")
private fun Media.hasPoster() = !poster.isNullOrBlank()
