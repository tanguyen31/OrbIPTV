package com.orbit.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orbit.iptv.data.Episode
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.SeriesInfo
import com.orbit.iptv.data.WatchProgress
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.FocusSurface
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.MetaLine
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.OrbitChip
import com.orbit.iptv.ui.components.ProgressLine
import com.orbit.iptv.ui.components.RoundIconButton
import com.orbit.iptv.ui.components.formatRating
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.delay

fun Episode.toPlayable(series: Media): Playable = Playable(
    id = id,
    type = MediaType.EPISODE,
    title = series.title,
    subtitle = "S$season · E$number — $title",
    url = url,
    poster = image ?: series.backdrop ?: series.poster,
    seriesId = series.id,
    category = series.category,
)

@Composable
fun SeriesDetailScreen(id: String) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val favorites by Repo.favorites.collectAsState()
    val progressList by Repo.progress.collectAsState()
    val media = catalog.byId(id)
    if (media == null) {
        MessageView(Icons.Rounded.ErrorOutline, "Série introuvable", "Elle a peut-être été retirée de ta source.", actionLabel = "Retour", onAction = nav.back)
        return
    }

    var loaded by remember(id) { mutableStateOf(false) }
    val info by produceState<SeriesInfo?>(null, id) {
        value = Repo.seriesInfo(media)
        loaded = true
    }
    val seasons = info?.seasons ?: emptyMap()
    val seasonKeys = seasons.keys.sorted()
    val progressById = remember(progressList) { progressList.associateBy { it.playable.id } }
    val last: WatchProgress? = remember(progressList, id) {
        progressList.filter { it.playable.seriesId == id }.maxByOrNull { it.updatedAt }
    }
    val lastEpisode = last?.let { l -> seasons.values.asSequence().flatten().firstOrNull { it.id == l.playable.id } }

    var chosenSeason by rememberSaveable(id) { mutableIntStateOf(-1) }
    val season = when {
        chosenSeason in seasons -> chosenSeason
        lastEpisode != null -> lastEpisode.season
        else -> seasonKeys.firstOrNull() ?: -1
    }
    val episodes = seasons[season].orEmpty()

    // Épisode à lancer avec le bouton principal (reprise ou suivant).
    val resume: Pair<Episode, Long>? = remember(info, last) {
        val l = last ?: return@remember null
        val ep = lastEpisode ?: return@remember null
        if (l.isUnfinished) {
            ep to l.positionMs
        } else {
            val list = seasons[ep.season].orEmpty()
            val idx = list.indexOfFirst { it.id == ep.id }
            val next = list.getOrNull(idx + 1)
                ?: seasonKeys.firstOrNull { it > ep.season }?.let { seasons[it]?.firstOrNull() }
            next?.let { it to 0L }
        }
    }
    val first = seasonKeys.firstOrNull()?.let { seasons[it]?.firstOrNull() }

    fun playEpisode(ep: Episode, startMs: Long) {
        val list = seasons[ep.season].orEmpty()
        val queue = list.map { it.toPlayable(media) }
        nav.play(queue, list.indexOfFirst { it.id == ep.id }.coerceAtLeast(0), startMs)
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(id, loaded) {
        if (tv && loaded) {
            delay(200)
            runCatching { playFocus.requestFocus() }
        }
    }

    val backdrop = info?.backdrop ?: media.backdrop ?: info?.poster ?: media.poster
    val edge = Dims.edge(tv)
    val isFav = id in favorites

    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop, if (tv) 520.dp else 320.dp, tv)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "header") {
                Column(Modifier.padding(start = edge, end = edge, top = if (tv) 150.dp else 190.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (tv) {
                            PosterImage(info?.poster ?: media.poster, 190.dp)
                            Spacer(Modifier.width(28.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("SÉRIE", color = Orbit.Accent, style = MaterialTheme.typography.labelMedium)
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
                                    seasonKeys.size.takeIf { it > 0 }?.let { if (it > 1) "$it saisons" else "1 saison" },
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
                        val target = resume ?: first?.let { it to 0L }
                        OrbitButton(
                            text = when {
                                !loaded -> "Chargement…"
                                target == null -> "Aucun épisode"
                                resume != null && resume.second > 0 -> "Reprendre S${target.first.season} E${target.first.number}"
                                else -> "Lecture S${target.first.season} E${target.first.number}"
                            },
                            icon = Icons.Rounded.PlayArrow,
                            primary = true,
                            enabled = target != null,
                            focusRequester = playFocus,
                            onClick = { target?.let { playEpisode(it.first, it.second) } },
                        )
                        OrbitButton(
                            text = "Ma liste",
                            icon = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add,
                            onClick = { context.toggleFavorite(media) },
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
                    info?.cast?.let { Credit("Avec", it, tv) }
                }
            }

            if (!loaded) {
                item(key = "loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Orbit.Accent) }
                }
            } else if (seasonKeys.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Aucun épisode disponible pour cette série.",
                        color = Orbit.Muted,
                        modifier = Modifier.padding(horizontal = edge, vertical = 28.dp),
                    )
                }
            } else {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = edge, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(seasonKeys, key = { it }) { s ->
                            OrbitChip("Saison $s", s == season, onClick = { chosenSeason = s })
                        }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(
                        ep,
                        series = media,
                        progress = progressById[ep.id],
                        onClick = {
                            val p = progressById[ep.id]
                            playEpisode(ep, if (p != null && p.isUnfinished) p.positionMs else 0L)
                        },
                        modifier = Modifier.padding(horizontal = edge - 8.dp, vertical = 4.dp),
                    )
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
private fun EpisodeRow(
    ep: Episode,
    series: Media,
    progress: WatchProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tv = LocalTv.current
    val watched = progress != null && !progress.isUnfinished && progress.fraction > 0.9f
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.02f,
        color = Color.Transparent,
        focusedColor = Orbit.SurfaceHigh,
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = if (tv) 200.dp else 136.dp, height = if (tv) 112.dp else 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Orbit.SurfaceHigh),
            ) {
                val img = ep.image ?: series.backdrop ?: series.poster
                if (img != null) {
                    AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                if (progress != null && progress.isUnfinished) {
                    ProgressLine(progress.fraction, Modifier.align(Alignment.BottomCenter))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${ep.number}. ${ep.title}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (watched) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Check, contentDescription = "Vu", tint = Orbit.Accent, modifier = Modifier.size(16.dp))
                    }
                }
                ep.duration?.let {
                    Text(it, color = Orbit.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                }
                ep.plot?.let {
                    Text(
                        it,
                        color = Orbit.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (tv) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
