package com.orbit.iptv.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.openMedia
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.ContinueCard
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.SectionRow
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv

@Composable
fun MyListScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val catalog by Repo.catalog.collectAsState()
    val favorites by Repo.favorites.collectAsState()
    val progress by Repo.progress.collectAsState()

    val items = remember(catalog, favorites) { favorites.mapNotNull { catalog.byId(it) } }
    val live = items.filter { it.type == MediaType.LIVE }
    val movies = items.filter { it.type == MediaType.MOVIE }
    val series = items.filter { it.type == MediaType.SERIES }
    val history = remember(progress) {
        progress.filter { it.isUnfinished }.sortedByDescending { it.updatedAt }.distinctBy { it.playable.seriesId ?: it.playable.id }
    }

    if (items.isEmpty() && history.isEmpty()) {
        MessageView(
            Icons.Rounded.Bookmarks,
            "Ta liste est vide",
            "Ajoute des films, des séries ou des chaînes avec le bouton « Ma liste » ou par un appui long.",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (tv) 16.dp else 0.dp, bottom = 40.dp)) {
        item(key = "title") {
            Text(
                "Ma liste",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = Dims.edge(tv), top = if (tv) 12.dp else 8.dp),
            )
        }
        if (history.isNotEmpty()) {
            item(key = "history") {
                SectionRow("En cours", history, key = { it.playable.id }) { p ->
                    ContinueCard(p, onClick = { nav.play(listOf(p.playable), 0, p.positionMs) }, modifier = Modifier.width(Dims.wide(tv)))
                }
            }
        }
        if (live.isNotEmpty()) {
            item(key = "live") {
                SectionRow("Chaînes", live, key = { it.id }) { m ->
                    MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                }
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "movies") {
                SectionRow("Films", movies, key = { it.id }) { m ->
                    MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                }
            }
        }
        if (series.isNotEmpty()) {
            item(key = "series") {
                SectionRow("Séries", series, key = { it.id }) { m ->
                    MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                }
            }
        }
    }
}
