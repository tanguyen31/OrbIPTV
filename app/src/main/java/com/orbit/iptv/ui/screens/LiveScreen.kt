package com.orbit.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.Program
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.nowPlaying
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.toPlayable
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.FocusSurface
import com.orbit.iptv.ui.components.LiveBadge
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.OrbitChip
import com.orbit.iptv.ui.components.ProgressLine
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALL = "__all__"
private const val FAVS = "__favs__"

private val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
fun formatTime(ms: Long): String = timeFormat.format(Date(ms))

@Composable
fun LiveScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val favorites by Repo.favorites.collectAsState()
    var selected by rememberSaveable { mutableStateOf(ALL) }

    val categories = remember(catalog) { listOf(ALL, FAVS) + catalog.liveCategories }
    val channels = remember(catalog, selected, favorites) {
        when (selected) {
            ALL -> catalog.live
            FAVS -> catalog.live.filter { it.id in favorites }
            else -> catalog.live.filter { it.category == selected }
        }
    }
    val counts = remember(catalog) { catalog.live.groupingBy { it.category }.eachCount() }

    fun label(c: String) = when (c) {
        ALL -> "Toutes les chaînes"
        FAVS -> "Favoris"
        else -> c
    }

    fun play(ch: Media) {
        val queue = channels.mapNotNull { it.toPlayable() }
        nav.play(queue, queue.indexOfFirst { it.id == ch.id }.coerceAtLeast(0), 0L)
    }

    if (catalog.live.isEmpty()) {
        MessageView(Icons.Rounded.LiveTv, "Aucune chaîne en direct", "Ta source ne propose pas de chaînes en direct.")
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (tv || maxWidth >= 840.dp) {
            var focusedChannel by remember { mutableStateOf<Media?>(null) }
            val shown = focusedChannel?.takeIf { f -> channels.any { it.id == f.id } } ?: channels.firstOrNull()
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(top = if (tv) 28.dp else 12.dp),
            ) {
                LazyColumn(
                    Modifier
                        .width(if (tv) 270.dp else 230.dp)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = if (tv) 24.dp else 12.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    item {
                        Text(
                            "TV en direct",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 12.dp, bottom = 14.dp),
                        )
                    }
                    items(categories, key = { it }) { c ->
                        CategoryItem(
                            label = label(c),
                            count = when (c) {
                                ALL -> catalog.live.size
                                FAVS -> catalog.live.count { it.id in favorites }
                                else -> counts[c] ?: 0
                            },
                            selected = selected == c,
                            onClick = { selected = c },
                        )
                    }
                }
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (channels.isEmpty()) {
                        item { EmptyChannels(selected == FAVS) }
                    }
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                        ChannelRow(
                            ch,
                            number = if (ch.num > 0) ch.num else i + 1,
                            favorite = ch.id in favorites,
                            onClick = { play(ch) },
                            onLongClick = { context.toggleFavorite(ch) },
                            onFocus = { focusedChannel = ch },
                        )
                    }
                }
                if (shown != null) {
                    ChannelPreview(
                        shown,
                        favorite = shown.id in favorites,
                        onPlay = { play(shown) },
                        modifier = Modifier
                            .width(if (tv) 360.dp else 300.dp)
                            .fillMaxHeight()
                            .padding(start = 12.dp, end = if (tv) 40.dp else 12.dp, bottom = 24.dp),
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Dims.edge(false), vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it }) { c ->
                        OrbitChip(label(c), selected == c, onClick = { selected = c })
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (channels.isEmpty()) {
                        item { EmptyChannels(selected == FAVS) }
                    }
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                        ChannelRow(
                            ch,
                            number = if (ch.num > 0) ch.num else i + 1,
                            favorite = ch.id in favorites,
                            onClick = { play(ch) },
                            onLongClick = { context.toggleFavorite(ch) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChannels(favorites: Boolean) {
    Text(
        if (favorites) "Aucune chaîne favorite.\nFais un appui long sur une chaîne pour l'ajouter." else "Aucune chaîne dans cette catégorie.",
        color = Orbit.Muted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun CategoryItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.03f,
        showBorder = false,
        color = if (selected) Orbit.SurfaceHigh else Color.Transparent,
        focusedColor = Color.White,
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = when {
                    focused -> Color.Black
                    selected -> Orbit.Accent
                    else -> Orbit.Text
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
                color = if (focused) Color.Black.copy(alpha = 0.6f) else Orbit.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun ChannelRow(
    ch: Media,
    number: Int,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: (() -> Unit)? = null,
) {
    val now by produceState(Repo.cachedEpg(ch.id)?.nowPlaying(), ch.id) {
        delay(450)
        value = Repo.epg(ch).nowPlaying()
    }
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onLongClick = onLongClick,
        onFocus = onFocus,
        shape = RoundedCornerShape(12.dp),
        focusedScale = 1.02f,
        color = Color.Transparent,
        focusedColor = Orbit.SurfaceHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                color = Orbit.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.width(34.dp),
            )
            Box(
                Modifier
                    .size(width = 74.dp, height = 46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Orbit.SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (!ch.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = ch.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp),
                    )
                } else {
                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Orbit.Muted, modifier = Modifier.size(22.dp))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(ch.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val p = now
                if (p != null) {
                    Text(
                        "${formatTime(p.start)}  ${p.title}",
                        color = Orbit.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    ProgressLine(
                        p.progress,
                        Modifier
                            .padding(top = 6.dp)
                            .width(140.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        height = 3.dp,
                    )
                }
            }
            if (favorite) {
                Icon(Icons.Rounded.Favorite, contentDescription = "Favori", tint = Orbit.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Panneau de droite (TV) : logo, programme en cours et à venir. */
@Composable
private fun ChannelPreview(ch: Media, favorite: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val programs by produceState<List<Program>?>(Repo.cachedEpg(ch.id), ch.id) {
        delay(250)
        value = Repo.epg(ch)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Orbit.Surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Orbit.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!ch.poster.isNullOrBlank()) {
                AsyncImage(
                    model = ch.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                )
            } else {
                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Orbit.Muted, modifier = Modifier.size(40.dp))
            }
            LiveBadge(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(ch.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(ch.category, color = Orbit.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitButton("Regarder", onPlay, icon = Icons.Rounded.PlayArrow, primary = true, compact = true)
            OrbitButton(
                if (favorite) "Retirer" else "Favori",
                onClick = { context.toggleFavorite(ch) },
                icon = Icons.Rounded.Favorite,
                compact = true,
            )
        }
        Spacer(Modifier.height(20.dp))
        val list = programs
        when {
            list == null -> CircularProgressIndicator(color = Orbit.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            list.isEmpty() -> Text("Pas de guide des programmes pour cette chaîne.", color = Orbit.Muted, style = MaterialTheme.typography.bodySmall)
            else -> list.take(6).forEach { ProgramItem(it) }
        }
    }
}

@Composable
private fun ProgramItem(p: Program) {
    val live = p.isLive
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatTime(p.start)} – ${formatTime(p.end)}",
                color = if (live) Orbit.Accent else Orbit.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
            if (live) {
                Spacer(Modifier.width(8.dp))
                Text("MAINTENANT", color = Orbit.Live, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            p.title,
            style = if (live) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (live) {
            if (!p.description.isNullOrBlank()) {
                Text(
                    p.description,
                    color = Orbit.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ProgressLine(
                p.progress,
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(2.dp)),
                height = 3.dp,
            )
        }
    }
}
