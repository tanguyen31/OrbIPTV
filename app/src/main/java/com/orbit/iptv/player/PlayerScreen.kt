package com.orbit.iptv.player

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Program
import com.orbit.iptv.data.Repo
import com.orbit.iptv.data.nowPlaying
import com.orbit.iptv.ui.components.FocusSurface
import com.orbit.iptv.ui.components.LiveBadge
import com.orbit.iptv.ui.components.OrbitButton
import com.orbit.iptv.ui.components.ProgressLine
import com.orbit.iptv.ui.components.RoundIconButton
import com.orbit.iptv.ui.components.toast
import com.orbit.iptv.ui.screens.ResizeLabels
import com.orbit.iptv.ui.screens.formatClock
import com.orbit.iptv.ui.screens.formatTime
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.Orbit
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Panel { NONE, TRACKS, QUEUE }

@Composable
fun PlayerScreen(c: PlaybackController, inPip: Boolean, onExit: () -> Unit) {
    val tv = LocalTv.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by Repo.settings.collectAsState()
    val favorites by Repo.favorites.collectAsState()

    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var zapBanner by remember { mutableLongStateOf(0L) }
    var zapVisible by remember { mutableStateOf(false) }
    var nextDismissed by remember(c.index) { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    val current = c.current
    val live = c.isLive
    val showNextCard = c.ended && c.hasNext && current?.type == MediaType.EPISODE && settings.autoplayNext && !nextDismissed

    fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    fun zap(delta: Int) {
        if (delta > 0) c.next() else c.previous()
        zapBanner = System.currentTimeMillis()
        touch()
    }

    // Rafraîchit la position de lecture.
    LaunchedEffect(c) {
        while (true) {
            c.tick()
            delay(500)
        }
    }

    // Masque automatiquement les commandes.
    LaunchedEffect(overlay, lastInteraction, c.isPlaying, panel) {
        if (overlay && c.isPlaying && panel == Panel.NONE) {
            delay(5000)
            overlay = false
        }
    }

    // Fin de lecture : épisode suivant ou retour.
    LaunchedEffect(c.ended, c.index) {
        if (c.ended && !live) {
            if (!(c.hasNext && current?.type == MediaType.EPISODE)) {
                delay(1500)
                onExit()
            } else if (!settings.autoplayNext) {
                overlay = true
            }
        }
    }

    // Place le focus là où il faut pour la télécommande.
    LaunchedEffect(overlay, panel, c.error, showNextCard) {
        delay(80)
        runCatching {
            when {
                panel != Panel.NONE -> Unit
                c.error != null -> errorFocus.requestFocus()
                showNextCard -> nextFocus.requestFocus()
                overlay && tv -> playFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler(enabled = panel != Panel.NONE || (tv && overlay && c.error == null)) {
        if (panel != Panel.NONE) panel = Panel.NONE else overlay = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch()
                val code = e.nativeKeyEvent.keyCode
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        c.togglePlay()
                        overlay = true
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_CHANNEL_UP -> if (live) {
                        zap(1)
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> if (live) {
                        zap(-1)
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        c.seekBy(30_000)
                        return@onPreviewKeyEvent true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        c.seekBy(-30_000)
                        return@onPreviewKeyEvent true
                    }
                }
                if (overlay || panel != Panel.NONE || c.error != null || showNextCard) return@onPreviewKeyEvent false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        overlay = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (live) zap(-1) else overlay = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (live) zap(1) else overlay = true
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (live) {
                            panel = Panel.QUEUE
                        } else {
                            c.seekBy(-10_000)
                            overlay = true
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!live) c.seekBy(10_000)
                        overlay = true
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(live) {
                detectTapGestures(
                    onTap = {
                        if (panel != Panel.NONE) panel = Panel.NONE else overlay = !overlay
                        touch()
                    },
                    onDoubleTap = { offset ->
                        if (!live) {
                            c.seekBy(if (offset.x < size.width / 2) -10_000 else 10_000)
                            overlay = true
                            touch()
                        }
                    },
                )
            },
    ) {
        VideoSurface(c)

        if (c.buffering && c.error == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp),
            )
        }

        if (!inPip) {
            AnimatedVisibility(
                visible = overlay && panel == Panel.NONE && c.error == null && !showNextCard,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                ControlsOverlay(
                    c = c,
                    favorite = current?.let { it.id in favorites || (it.seriesId != null && it.seriesId in favorites) } ?: false,
                    playFocus = playFocus,
                    onExit = onExit,
                    onTouch = ::touch,
                    onTracks = { panel = Panel.TRACKS },
                    onQueue = { panel = Panel.QUEUE },
                    onFavorite = {
                        current?.let { p ->
                            val id = p.seriesId ?: p.id
                            val added = Repo.toggleFavorite(id)
                            context.toast(if (added) "Ajouté à Ma liste" else "Retiré de Ma liste")
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = live && zapVisible && !overlay && panel == Panel.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                current?.let { ZapBanner(it, c.index, c.queue.size) }
            }
            LaunchedEffect(zapBanner) {
                if (zapBanner > 0) {
                    zapVisible = true
                    delay(3500)
                    zapVisible = false
                }
            }

            AnimatedVisibility(
                visible = panel == Panel.TRACKS,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                TracksPanel(c, onClose = { panel = Panel.NONE })
            }

            AnimatedVisibility(
                visible = panel == Panel.QUEUE,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                QueuePanel(c, onClose = { panel = Panel.NONE })
            }

            if (showNextCard) {
                NextEpisodeCard(
                    c,
                    focusRequester = nextFocus,
                    onCancel = { nextDismissed = true; overlay = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(32.dp),
                )
            }

            c.error?.let { message ->
                ErrorCard(
                    message = message,
                    live = live && c.queue.size > 1,
                    focusRequester = errorFocus,
                    onRetry = { c.retry() },
                    onNext = { zap(1) },
                    onExit = onExit,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(c: PlaybackController) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(true)
                setBackgroundColor(android.graphics.Color.BLACK)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { view ->
            view.player = c.player
            view.resizeMode = when (c.resizeMode) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ControlsOverlay(
    c: PlaybackController,
    favorite: Boolean,
    playFocus: FocusRequester,
    onExit: () -> Unit,
    onTouch: () -> Unit,
    onTracks: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
) {
    val tv = LocalTv.current
    val item = c.current ?: return
    val live = item.type == MediaType.LIVE
    val clock by produceState(formatTime(System.currentTimeMillis())) {
        while (true) {
            value = formatTime(System.currentTimeMillis())
            delay(15_000)
        }
    }
    val programs by produceState(Repo.cachedEpg(item.id) ?: emptyList(), item.id) {
        value = Repo.epg(item.streamId, item.id)
    }
    val now = programs.nowPlaying()
    val nextProgram = now?.let { n -> programs.firstOrNull { it.start >= n.end } }
    val pad = if (tv) 48.dp else 20.dp

    fun act(block: () -> Unit): () -> Unit = {
        onTouch()
        block()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (tv) 300.dp else 240.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
        )

        // ---- En-tête -------------------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = pad, vertical = if (tv) 32.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!tv) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onClick = onExit, size = 44.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                if (live) {
                    LiveBadge()
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    item.title,
                    style = if (tv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = if (live) item.category else item.subtitle
                if (!sub.isNullOrBlank() && item.type != MediaType.MOVIE) {
                    Text(sub, color = Orbit.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(clock, style = MaterialTheme.typography.titleMedium, color = Orbit.Text)
        }

        // ---- Commandes centrales (tactile) ------------------------------------------
        if (!tv) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live) {
                    RoundIconButton(Icons.Rounded.SkipPrevious, "Chaîne précédente", onClick = act { c.previous() }, size = 56.dp)
                } else {
                    RoundIconButton(Icons.Rounded.Replay10, "Reculer de 10 secondes", onClick = act { c.seekBy(-10_000) }, size = 56.dp)
                }
                RoundIconButton(
                    if (c.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (c.isPlaying) "Pause" else "Lecture",
                    onClick = act { c.togglePlay() },
                    size = 76.dp,
                )
                if (live) {
                    RoundIconButton(Icons.Rounded.SkipNext, "Chaîne suivante", onClick = act { c.next() }, size = 56.dp)
                } else {
                    RoundIconButton(Icons.Rounded.Forward10, "Avancer de 10 secondes", onClick = act { c.seekBy(10_000) }, size = 56.dp)
                }
            }
        }

        // ---- Bas de l'écran ------------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(start = pad, end = pad, bottom = if (tv) 36.dp else 18.dp),
        ) {
            if (live) {
                if (now != null) {
                    Text(
                        "${formatTime(now.start)} – ${formatTime(now.end)}",
                        color = Orbit.Accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(now.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressLine(
                        now.progress,
                        Modifier
                            .padding(top = 8.dp)
                            .widthIn(max = 520.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        height = 4.dp,
                    )
                    nextProgram?.let {
                        Text(
                            "Ensuite · ${formatTime(it.start)}  ${it.title}",
                            color = Orbit.Muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                if (item.type == MediaType.MOVIE && !item.subtitle.isNullOrBlank()) {
                    Text(item.subtitle, color = Orbit.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatClock(c.positionMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                    SeekBar(
                        position = c.positionMs,
                        duration = c.durationMs,
                        buffered = c.bufferedMs,
                        onSeek = { c.seekTo(it); onTouch() },
                        onSeekBy = { c.seekBy(it); onTouch() },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (c.durationMs > 0) "-" + formatClock(c.durationMs - c.positionMs) else "--:--",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .width(72.dp)
                            .padding(start = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tv) {
                    OrbitButton(
                        text = if (c.isPlaying) "Pause" else "Lecture",
                        onClick = act { c.togglePlay() },
                        icon = if (c.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        focusRequester = playFocus,
                        compact = true,
                    )
                }
                if (live && c.queue.size > 1) {
                    OrbitButton("Chaînes", act(onQueue), icon = Icons.Rounded.LiveTv, compact = true)
                }
                if (item.type == MediaType.EPISODE && c.queue.size > 1) {
                    OrbitButton("Épisodes", act(onQueue), icon = Icons.Rounded.VideoLibrary, compact = true)
                }
                if (item.type == MediaType.EPISODE && c.hasNext) {
                    OrbitButton("Épisode suivant", act { c.next() }, icon = Icons.Rounded.SkipNext, compact = true)
                }
                OrbitButton("Audio et sous-titres", act(onTracks), icon = Icons.Rounded.Subtitles, compact = true)
                OrbitButton(ResizeLabels[c.resizeMode.coerceIn(0, 2)], act { c.cycleResize() }, icon = Icons.Rounded.AspectRatio, compact = true)
                if (!live) {
                    OrbitButton("Vitesse ${formatSpeed(c.speed)}", act { c.cycleSpeed() }, icon = Icons.Rounded.Speed, compact = true)
                }
                OrbitButton(
                    if (favorite) "Dans Ma liste" else "Ma liste",
                    act(onFavorite),
                    icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    compact = true,
                )
            }
        }
    }
}

private fun formatSpeed(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x".replace('.', ',')

/** Barre de progression utilisable au doigt et à la télécommande (gauche / droite). */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val trackHeight = if (focused) 8.dp else 5.dp
    val thumb = if (focused) 20.dp else 14.dp

    Box(
        modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (e.key) {
                        Key.DirectionLeft -> {
                            onSeekBy(-10_000)
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekBy(10_000)
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
            .pointerInput(duration) {
                detectTapGestures { off ->
                    if (duration > 0) onSeek((off.x / widthPx * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> drag = (off.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / widthPx).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bufferedFraction)
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Orbit.Accent),
            )
        }
        val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumb.toPx() }
        Box(
            Modifier
                .offset { IntOffset((fraction * widthPx - thumbPx / 2).roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun ZapBanner(p: Playable, index: Int, total: Int) {
    val programs by produceState(Repo.cachedEpg(p.id) ?: emptyList(), p.id) {
        value = Repo.epg(p.streamId, p.id)
    }
    val now = programs.nowPlaying()
    Row(
        Modifier
            .padding(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(16.dp)
            .widthIn(max = 560.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 96.dp, height = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Orbit.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!p.poster.isNullOrBlank()) {
                AsyncImage(p.poster, null, contentScale = ContentScale.Fit, modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp))
            }
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("${index + 1} / $total", color = Orbit.Accent, style = MaterialTheme.typography.labelSmall)
            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(
                    "${formatTime(now.start)}  ${now.title}",
                    color = Orbit.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SidePanel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Orbit.Surface.copy(alpha = 0.97f))
            .padding(vertical = 28.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun TracksPanel(c: PlaybackController, onClose: () -> Unit) {
    val audio = remember(c.tracksVersion, c.player) { c.audioTracks() }
    val text = remember(c.tracksVersion, c.player) { c.textTracks() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    SidePanel("Audio et sous-titres") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("Audio")
            if (audio.isEmpty()) {
                Text("Piste par défaut", color = Orbit.Muted, modifier = Modifier.padding(8.dp))
            }
            audio.forEachIndexed { i, opt ->
                OptionRow(opt.label, opt.selected, if (i == 0) firstFocus else null) { c.selectTrack(C.TRACK_TYPE_AUDIO, opt) }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Sous-titres")
            text.forEachIndexed { i, opt ->
                OptionRow(opt.label, opt.selected, if (audio.isEmpty() && i == 0) firstFocus else null) {
                    c.selectTrack(C.TRACK_TYPE_TEXT, opt)
                }
            }
            Spacer(Modifier.height(20.dp))
            OrbitButton("Fermer", onClose, compact = true, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Orbit.Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        showBorder = false,
        color = Color.Transparent,
        focusedColor = Color.White,
        focusRequester = focusRequester,
    ) { focused ->
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (focused) Color.Black else Orbit.Accent)
            }
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (focused) Color.Black else Orbit.Text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QueuePanel(c: PlaybackController, onClose: () -> Unit) {
    val live = c.isLive
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (c.index - 2).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { currentFocus.requestFocus() }
    }
    SidePanel(if (live) "Chaînes" else "Épisodes") {
        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            itemsIndexed(c.queue, key = { _, p -> p.id }) { i, p ->
                val isCurrent = i == c.index
                FocusSurface(
                    onClick = {
                        if (!isCurrent) c.play(i)
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    focusedScale = 1.02f,
                    color = if (isCurrent) Orbit.SurfaceHigh else Color.Transparent,
                    focusedColor = Orbit.SurfaceHigh,
                    focusRequester = if (isCurrent) currentFocus else null,
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 88.dp, height = 50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Orbit.Bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!p.poster.isNullOrBlank()) {
                                AsyncImage(
                                    p.poster,
                                    null,
                                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(if (live) 5.dp else 0.dp),
                                )
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                if (live) "${i + 1}. ${p.title}" else p.subtitle ?: p.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) Orbit.Accent else Orbit.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isCurrent) {
                                Text("En cours", color = Orbit.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextEpisodeCard(c: PlaybackController, focusRequester: FocusRequester, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val next = c.queue.getOrNull(c.index + 1) ?: return
    var seconds by remember(c.index) { mutableIntStateOf(8) }
    LaunchedEffect(c.index) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
        c.next()
    }
    Column(
        modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Orbit.Surface.copy(alpha = 0.96f))
            .padding(20.dp),
    ) {
        Text("ÉPISODE SUIVANT", color = Orbit.Accent, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text(next.subtitle ?: next.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Lecture dans $seconds s", color = Orbit.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitButton("Lire maintenant", { c.next() }, icon = Icons.Rounded.PlayArrow, primary = true, focusRequester = focusRequester, compact = true)
            OrbitButton("Annuler", onCancel, compact = true)
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    live: Boolean,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(24.dp)
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Orbit.Surface.copy(alpha = 0.96f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Orbit.Danger, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("Lecture impossible", style = MaterialTheme.typography.titleLarge)
        Text(message, color = Orbit.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitButton("Réessayer", onRetry, icon = Icons.Rounded.Refresh, primary = true, focusRequester = focusRequester, compact = true)
            if (live) OrbitButton("Chaîne suivante", onNext, icon = Icons.Rounded.SkipNext, compact = true)
            OrbitButton("Quitter", onExit, compact = true)
        }
    }
}

@Suppress("unused")
private fun List<Program>.hasGuide() = isNotEmpty()
