package com.orbit.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.openMedia
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.OrbitChip
import com.orbit.iptv.ui.components.PosterCard
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ALL_CATS = "__all__"

enum class SortMode(val label: String) { RECENT("Récents"), ALPHA("A à Z"), RATING("Mieux notés") }

/** Grille d'affiches avec filtres par catégorie et tri (films ou séries). */
@Composable
fun CatalogScreen(type: MediaType) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val progress by Repo.progress.collectAsState()
    val all = if (type == MediaType.MOVIE) catalog.movies else catalog.series
    val categories = if (type == MediaType.MOVIE) catalog.movieCategories else catalog.seriesCategories
    var selected by rememberSaveable(type) { mutableStateOf(ALL_CATS) }
    var sort by rememberSaveable(type) { mutableStateOf(SortMode.RECENT) }

    val items by produceState(emptyList<Media>(), all, selected, sort) {
        value = withContext(Dispatchers.Default) {
            val base = if (selected == ALL_CATS) all else all.filter { it.category == selected }
            when (sort) {
                SortMode.RECENT -> if (base.any { it.added > 0 }) base.sortedByDescending { it.added } else base
                SortMode.ALPHA -> base.sortedBy { it.title.lowercase() }
                SortMode.RATING -> base.sortedByDescending { it.rating ?: 0.0 }
            }
        }
    }
    val progressById = remember(progress) {
        progress.filter { it.isUnfinished }.associate { it.playable.id to it.fraction }
    }

    val title = if (type == MediaType.MOVIE) "Films" else "Séries"
    if (all.isEmpty()) {
        MessageView(
            if (type == MediaType.MOVIE) Icons.Rounded.Movie else Icons.Rounded.Tv,
            "Aucun contenu",
            if (type == MediaType.MOVIE) "Ta source ne propose pas de films." else "Ta source ne propose pas de séries.",
        )
        return
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(selected, sort) { gridState.scrollToItem(0) }
    val edge = Dims.edge(tv)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = edge, end = edge, top = if (tv) 28.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.padding(start = 12.dp))
            Text("${items.size}", color = Orbit.Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (tv) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortMode.entries.forEach { m -> OrbitChip(m.label, sort == m, onClick = { sort = m }) }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!tv) {
                items(SortMode.entries.toList(), key = { "sort:${it.name}" }) { m ->
                    OrbitChip(m.label, sort == m, onClick = { sort = m })
                }
                item(key = "sep") { Spacer(Modifier.padding(horizontal = 4.dp)) }
            }
            item(key = "cat:all") { OrbitChip("Tout", selected == ALL_CATS, onClick = { selected = ALL_CATS }) }
            items(categories, key = { "cat:$it" }) { c ->
                OrbitChip(c, selected == c, onClick = { selected = c })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (tv) 150.dp else 104.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = edge, end = edge, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 20.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (tv) 22.dp else 14.dp),
        ) {
            items(items, key = { it.id }) { m ->
                PosterCard(
                    m,
                    onClick = { nav.openMedia(m, catalog) },
                    modifier = Modifier.fillMaxWidth(),
                    onLongClick = { context.toggleFavorite(m) },
                    progress = progressById[m.id],
                )
            }
        }
    }
}
