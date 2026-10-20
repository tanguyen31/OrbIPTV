package com.orbit.iptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.orbit.iptv.data.Http
import com.orbit.iptv.data.MediaType
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Repo
import java.util.Locale

/** File de lecture transmise par l'écran qui lance le lecteur. */
object PlayerSession {
    var queue: List<Playable> = emptyList()
        private set
    var index: Int = 0
        private set
    var startMs: Long = 0L
        private set
    var version: Int = 0
        private set

    fun start(queue: List<Playable>, index: Int, startMs: Long) {
        this.queue = queue
        this.index = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        this.startMs = startMs
        version++
    }
}

data class TrackOption(val label: String, val selected: Boolean, val group: Tracks.Group?, val trackIndex: Int)

/** Pilote ExoPlayer et expose l'état de lecture à l'interface Compose. */
@OptIn(UnstableApi::class)
class PlaybackController(private val context: Context) {

    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var queue: List<Playable> by mutableStateOf(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var ended by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var resizeMode by mutableIntStateOf(Repo.settings.value.resizeMode)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var tracksVersion by mutableIntStateOf(0)
        private set

    private var attempt = 0
    private var lastSave = 0L
    private var loadedVersion = -1

    val current: Playable? get() = queue.getOrNull(index)
    val isLive: Boolean get() = current?.type == MediaType.LIVE
    val hasNext: Boolean get() = index < queue.size - 1
    val hasPrevious: Boolean get() = index > 0

    fun attach() {
        if (player != null) return
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.addListener(listener)
        player = exo

        if (loadedVersion != PlayerSession.version) {
            loadedVersion = PlayerSession.version
            queue = PlayerSession.queue
            play(PlayerSession.index, PlayerSession.startMs)
        } else {
            // Retour au premier plan : on reprend où on en était.
            play(index, positionMs)
        }
    }

    fun release() {
        saveProgress()
        player?.removeListener(listener)
        player?.release()
        player = null
        isPlaying = false
    }

    /** Recharge la session si une nouvelle lecture a été demandée pendant que le lecteur était ouvert. */
    fun reloadIfNeeded() {
        if (player != null && loadedVersion != PlayerSession.version) {
            saveProgress()
            loadedVersion = PlayerSession.version
            queue = PlayerSession.queue
            play(PlayerSession.index, PlayerSession.startMs)
        }
    }

    fun play(newIndex: Int, startMs: Long = 0L) {
        if (queue.isEmpty()) return
        if (newIndex != index) saveProgress()
        index = if (isLiveAt(newIndex)) {
            ((newIndex % queue.size) + queue.size) % queue.size
        } else {
            newIndex.coerceIn(0, queue.size - 1)
        }
        attempt = 0
        ended = false
        positionMs = startMs
        durationMs = 0L
        load(alternate = false, startMs = startMs)
        current?.let { if (it.type == MediaType.LIVE) Repo.addRecentLive(it.id) }
    }

    private fun isLiveAt(i: Int) = queue.getOrNull(i.coerceIn(0, queue.size - 1))?.type == MediaType.LIVE

    fun next() = play(index + 1)
    fun previous() = play(index - 1)

    private fun load(alternate: Boolean, startMs: Long) {
        val exo = player ?: return
        val item = current ?: return
        var url = item.url
        val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        var forceHls = isHls
        if (alternate) {
            if (isHls && url.contains("/live/")) {
                url = url.substringBeforeLast(".m3u8") + ".ts"
                forceHls = false
            } else if (!isHls && item.type == MediaType.LIVE) {
                forceHls = true
            }
        }
        val media = MediaItem.Builder()
            .setUri(url)
            .setMediaId(item.id)
            .apply { if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        error = null
        buffering = true
        if (startMs > 0) exo.setMediaItem(media, startMs) else exo.setMediaItem(media)
        exo.setPlaybackSpeed(if (item.type == MediaType.LIVE) 1f else speed)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun retry() {
        attempt = 0
        load(alternate = false, startMs = if (isLive) 0L else positionMs)
    }

    fun togglePlay() {
        val exo = player ?: return
        if (ended) {
            exo.seekTo(0)
            ended = false
        }
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        if (isLive) return
        val target = (exo.currentPosition + deltaMs).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
        exo.seekTo(target)
        positionMs = target
    }

    fun seekTo(ms: Long) {
        val exo = player ?: return
        exo.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms
    }

    fun cycleResize() {
        resizeMode = (resizeMode + 1) % 3
        Repo.updateSettings { it.copy(resizeMode = resizeMode) }
    }

    fun cycleSpeed() {
        val steps = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        val i = steps.indexOfFirst { it == speed }
        speed = steps[(i + 1) % steps.size]
        player?.playbackParameters = PlaybackParameters(speed)
    }

    /** Appelée régulièrement par l'interface pour rafraîchir la position. */
    fun tick() {
        val exo = player ?: return
        positionMs = exo.currentPosition.coerceAtLeast(0L)
        val d = exo.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        bufferedMs = exo.bufferedPosition.coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        if (now - lastSave > 10_000 && exo.isPlaying) {
            lastSave = now
            saveProgress()
        }
    }

    fun saveProgress() {
        val item = current ?: return
        val exo = player
        val pos = exo?.currentPosition ?: positionMs
        val dur = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: durationMs
        if (item.type == MediaType.LIVE || dur <= 0 || pos < 5_000) return
        Repo.saveProgress(item, pos, dur)
    }

    // ---- Pistes audio et sous-titres ---------------------------------------------------

    fun audioTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_AUDIO)

    fun textTracks(): List<TrackOption> {
        val exo = player ?: return emptyList()
        val list = tracksOf(C.TRACK_TYPE_TEXT)
        val disabled = exo.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || list.none { it.selected }
        return listOf(TrackOption("Désactivés", disabled, null, -1)) + list.map { it.copy(selected = it.selected && !disabled) }
    }

    private fun tracksOf(type: Int): List<TrackOption> {
        val exo = player ?: return emptyList()
        val out = ArrayList<TrackOption>()
        var n = 1
        for (group in exo.currentTracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                val lang = f.language?.takeIf { it != "und" }?.let { code ->
                    Locale(code).getDisplayLanguage(Locale.FRANCE).replaceFirstChar { it.uppercase() }
                }
                val extra = when {
                    type == C.TRACK_TYPE_AUDIO && f.channelCount >= 6 -> " · 5.1"
                    type == C.TRACK_TYPE_AUDIO && f.channelCount == 2 -> " · Stéréo"
                    else -> ""
                }
                val label = (f.label ?: lang ?: "Piste $n") + extra
                out.add(TrackOption(label, group.isTrackSelected(i), group, i))
                n++
            }
        }
        return out
    }

    fun selectTrack(type: Int, option: TrackOption) {
        val exo = player ?: return
        val builder = exo.trackSelectionParameters.buildUpon()
        val group = option.group
        if (group == null) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        exo.trackSelectionParameters = builder.build()
        tracksVersion++
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (!playing) saveProgress()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            when (state) {
                Player.STATE_READY -> {
                    attempt = 0
                    error = null
                    tick()
                }
                Player.STATE_ENDED -> {
                    if (!isLive) {
                        val item = current
                        val d = durationMs
                        if (item != null && d > 0) Repo.saveProgress(item, d, d)
                        ended = true
                    }
                }
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            tracksVersion++
        }

        override fun onPlayerError(e: PlaybackException) {
            val exo = player ?: return
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exo.seekToDefaultPosition()
                exo.prepare()
                return
            }
            if (attempt < 2) {
                attempt++
                load(alternate = attempt == 1, startMs = if (isLive) 0L else positionMs)
            } else {
                buffering = false
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Le serveur refuse ce flux (connexions simultanées dépassées ou flux hors ligne)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connexion au flux impossible. Vérifie ta connexion internet."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Format vidéo non pris en charge par cet appareil."
                    else -> "Impossible de lire ce contenu pour le moment."
                }
            }
        }
    }
}
