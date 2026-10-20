package com.orbit.iptv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.Catalog
import com.orbit.iptv.data.Media
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.LocalNav
import com.orbit.iptv.ui.openMedia
import com.orbit.iptv.ui.toggleFavorite
import com.orbit.iptv.ui.components.MessageView
import com.orbit.iptv.ui.components.SectionRow
import com.orbit.iptv.ui.theme.Dims
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

private data class SearchResults(
    val live: List<Media> = emptyList(),
    val movies: List<Media> = emptyList(),
    val series: List<Media> = emptyList(),
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private val marks = Regex("\\p{Mn}+")
private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(marks, "")

private fun search(catalog: Catalog, query: String): SearchResults {
    val q = query.trim().normalized()
    if (q.length < 2) return SearchResults()
    val words = q.split(' ').filter { it.isNotBlank() }
    fun match(m: Media): Boolean {
        val t = m.title.normalized()
        return words.all { t.contains(it) }
    }
    // Les titres qui commencent par la recherche passent en premier.
    fun rank(list: List<Media>) = list.asSequence().filter(::match)
        .sortedBy { if (it.title.normalized().startsWith(q)) 0 else 1 }
        .take(40).toList()
    return SearchResults(rank(catalog.live), rank(catalog.movies), rank(catalog.series))
}

@Composable
fun SearchScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val tv = LocalTv.current
    val focus = LocalFocusManager.current
    val catalog by Repo.catalog.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val results by produceState(SearchResults(), query, catalog) {
        delay(250)
        value = withContext(Dispatchers.Default) { search(catalog, query) }
    }
    val edge = Dims.edge(tv)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Chaînes, films, séries…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Effacer") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orbit.Accent,
                unfocusedBorderColor = Orbit.Stroke,
                focusedContainerColor = Orbit.Surface,
                unfocusedContainerColor = Orbit.Surface,
                cursorColor = Orbit.Accent,
            ),
            modifier = Modifier
                .padding(start = edge, end = edge, top = if (tv) 28.dp else 8.dp)
                .fillMaxWidth(if (tv) 0.6f else 1f),
        )

        when {
            query.trim().length < 2 -> MessageView(
                Icons.Rounded.Search,
                "Que veux-tu regarder ?",
                "Recherche parmi ${catalog.live.size} chaînes, ${catalog.movies.size} films et ${catalog.series.size} séries.",
            )
            results.isEmpty -> MessageView(Icons.Rounded.SearchOff, "Aucun résultat", "Rien ne correspond à « ${query.trim()} ».")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                if (results.live.isNotEmpty()) {
                    item(key = "live") {
                        SectionRow("Chaînes (${results.live.size})", results.live, key = { it.id }) { m ->
                            MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                        }
                    }
                }
                if (results.movies.isNotEmpty()) {
                    item(key = "movies") {
                        SectionRow("Films (${results.movies.size})", results.movies, key = { it.id }) { m ->
                            MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                        }
                    }
                }
                if (results.series.isNotEmpty()) {
                    item(key = "series") {
                        SectionRow("Séries (${results.series.size})", results.series, key = { it.id }) { m ->
                            MediaCard(m, null, onClick = { nav.openMedia(m, catalog) }, onLongClick = { context.toggleFavorite(m) })
                        }
                    }
                }
            }
        }
    }
}
