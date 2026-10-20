package com.orbit.iptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Repo
import com.orbit.iptv.ui.screens.CatalogScreen
import com.orbit.iptv.ui.screens.HomeScreen
import com.orbit.iptv.ui.screens.LiveScreen
import com.orbit.iptv.ui.screens.MovieDetailScreen
import com.orbit.iptv.ui.screens.MyListScreen
import com.orbit.iptv.ui.screens.SearchScreen
import com.orbit.iptv.ui.screens.SeriesDetailScreen
import com.orbit.iptv.ui.screens.SettingsScreen
import com.orbit.iptv.ui.screens.SetupScreen
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit

private val RailCollapsed = 84.dp

private enum class Tab(val route: Route, val label: String, val icon: ImageVector, val onPhone: Boolean) {
    SEARCH(Route.Search, "Recherche", Icons.Rounded.Search, false),
    HOME(Route.Home, "Accueil", Icons.Rounded.Home, true),
    LIVE(Route.Live, "TV en direct", Icons.Rounded.LiveTv, true),
    MOVIES(Route.Movies, "Films", Icons.Rounded.Movie, true),
    SERIES(Route.Series, "Séries", Icons.Rounded.Tv, true),
    MYLIST(Route.MyList, "Ma liste", Icons.Rounded.Bookmarks, true),
    SETTINGS(Route.Settings, "Réglages", Icons.Rounded.Settings, false),
}

@Composable
fun OrbitApp(onPlay: (List<Playable>, Int, Long) -> Unit) {
    val active by Repo.activeSource.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Orbit.Bg),
    ) {
        if (active == null) {
            SetupScreen(firstRun = true, onDone = {}, onCancel = null)
        } else {
            Shell(onPlay)
        }
    }
}

@Composable
private fun Shell(onPlay: (List<Playable>, Int, Long) -> Unit) {
    val tv = LocalTv.current
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = stack.last()
    val root = stack.first()

    fun selectTab(r: Route) {
        if (stack.size == 1 && stack[0] == r) return
        stack.clear()
        stack.add(r)
    }

    val nav = remember {
        Nav(
            open = { stack.add(it) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            play = onPlay,
        )
    }

    BackHandler(enabled = stack.size > 1 || root != Route.Home) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else selectTab(Route.Home)
    }

    val holder = rememberSaveableStateHolder()
    val screen: @Composable () -> Unit = {
        holder.SaveableStateProvider(key = "${stack.size}:$current") { Screen(current) }
    }

    CompositionLocalProvider(LocalNav provides nav) {
        if (tv) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = RailCollapsed),
                ) { screen() }
                SideRail(selected = root, onSelect = ::selectTab)
            }
        } else {
            val atRoot = stack.size == 1
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                if (atRoot && root != Route.Search && root != Route.Settings) {
                    PhoneTopBar(
                        onSearch = { nav.open(Route.Search) },
                        onSettings = { nav.open(Route.Settings) },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (atRoot) Modifier else Modifier.navigationBarsPadding()),
                ) { screen() }
                if (atRoot) {
                    PhoneBottomBar(selected = root, onSelect = ::selectTab)
                }
            }
        }
    }
}

@Composable
private fun Screen(route: Route) {
    val nav = LocalNav.current
    when (route) {
        Route.Home -> HomeScreen()
        Route.Live -> LiveScreen()
        Route.Movies -> CatalogScreen(MediaType.MOVIE)
        Route.Series -> CatalogScreen(MediaType.SERIES)
        Route.MyList -> MyListScreen()
        Route.Search -> SearchScreen()
        Route.Settings -> SettingsScreen()
        Route.AddSource -> SetupScreen(firstRun = false, onDone = { nav.back() }, onCancel = { nav.back() })
        is Route.MovieDetail -> MovieDetailScreen(route.id)
        is Route.SeriesDetail -> SeriesDetailScreen(route.id)
    }
}

@Composable
private fun PhoneTopBar(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitLogo(showName = true)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Recherche", tint = Orbit.Text) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Réglages", tint = Orbit.Text) }
    }
}

@Composable
private fun PhoneBottomBar(selected: Route, onSelect: (Route) -> Unit) {
    NavigationBar(containerColor = Orbit.Surface, tonalElevation = 0.dp) {
        Tab.entries.filter { it.onPhone }.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = {
                    Text(
                        if (tab == Tab.LIVE) "Direct" else tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Orbit.OnAccent,
                    selectedTextColor = Orbit.Text,
                    indicatorColor = Orbit.Accent,
                    unselectedIconColor = Orbit.Muted,
                    unselectedTextColor = Orbit.Muted,
                ),
            )
        }
    }
}

@Composable
fun OrbitLogo(showName: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Orbit.Accent, Color(0xFFFF7A45)))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Orbit.Bg),
            )
        }
        if (showName) {
            Spacer(Modifier.width(10.dp))
            Text("Orbit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Orbit.Text)
        }
    }
}

/** Menu latéral Android TV : replié en icônes, il s'ouvre quand il reçoit le focus. */
@Composable
private fun SideRail(selected: Route, onSelect: (Route) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 250.dp else RailCollapsed, label = "railWidth")
    Box(Modifier.fillMaxHeight()) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (expanded) 520.dp else RailCollapsed)
                .background(
                    if (expanded) {
                        Brush.horizontalGradient(listOf(Orbit.Bg, Orbit.Bg.copy(alpha = 0.92f), Color.Transparent))
                    } else {
                        Brush.horizontalGradient(listOf(Orbit.Bg, Orbit.Bg))
                    },
                ),
        )
        Column(
            Modifier
                .fillMaxHeight()
                .width(width)
                .onFocusChanged { expanded = it.hasFocus }
                .focusGroup()
                .padding(vertical = 28.dp, horizontal = 14.dp),
        ) {
            OrbitLogo(showName = expanded, modifier = Modifier.padding(start = 13.dp))
            Spacer(Modifier.weight(1f))
            Tab.entries.forEach { tab ->
                RailItem(tab.icon, tab.label, selected == tab.route, expanded) { onSelect(tab.route) }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val fg = when {
        focused -> Color.Black
        selected -> Orbit.Text
        else -> Orbit.Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(label, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}
