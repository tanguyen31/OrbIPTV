package com.orbit.iptv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.orbit.iptv.R
import com.orbit.iptv.data.Channel
import com.orbit.iptv.data.Http
import com.orbit.iptv.data.Kind
import com.orbit.iptv.data.Repository
import com.orbit.iptv.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "index"
        private const val INFO_DURATION_MS = 5_000L
    }

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var index = 0
    private var attempt = 0
    private var epgJob: Job? = null
    private val queue: List<Channel> get() = Repository.playQueue
    private val hideInfo = Runnable { b.infoPanel.animate().alpha(0f).setDuration(300).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        if (queue.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0))
            .coerceIn(0, queue.size - 1)

        b.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            b.zapBar.visibility = if (visibility == View.VISIBLE && isLive()) View.VISIBLE else View.GONE
            if (visibility == View.VISIBLE) showInfoPanel() else hideSystemBars()
        })
        b.btnPrev.setOnClickListener { play(index - 1) }
        b.btnNext.setOnClickListener { play(index + 1) }
        b.btnFav.setOnClickListener {
            val ch = queue.getOrNull(index) ?: return@setOnClickListener
            Repository.toggleFavorite(ch.id)
            updateFavButton()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
    }

    override fun onStart() {
        super.onStart()
        if (queue.isEmpty()) return
        initPlayer()
        play(index)
    }

    override fun onStop() {
        super.onStop()
        epgJob?.cancel()
        b.infoPanel.removeCallbacks(hideInfo)
        player?.release()
        player = null
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    attempt = 0
                    b.errorText.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                if (attempt < 2) {
                    // 2e essai : autre format (HLS forcé ou .ts) ; 3e essai : on recommence.
                    attempt++
                    load(index, alternate = attempt == 1)
                } else {
                    b.errorText.visibility = View.VISIBLE
                }
            }
        })
        b.playerView.player = exo
        b.playerView.requestFocus()
        player = exo
    }

    private fun play(newIndex: Int) {
        if (queue.isEmpty()) return
        index = ((newIndex % queue.size) + queue.size) % queue.size
        attempt = 0
        load(index, alternate = false)
        showInfo(queue[index])
        updateFavButton()
    }

    private fun load(i: Int, alternate: Boolean) {
        val exo = player ?: return
        val ch = queue.getOrNull(i) ?: return
        var url = ch.url
        val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        var forceHls = isHls
        if (alternate) {
            if (isHls && url.contains("/live/")) {
                url = url.substringBeforeLast(".m3u8") + ".ts"
                forceHls = false
            } else if (!isHls) {
                forceHls = true
            }
        }
        val item = MediaItem.Builder().setUri(url).apply {
            if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        b.errorText.visibility = View.GONE
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun isLive(): Boolean = queue.getOrNull(index)?.kind == Kind.LIVE

    private fun updateFavButton() {
        val ch = queue.getOrNull(index) ?: return
        b.btnFav.setImageResource(if (Repository.isFavorite(ch.id)) R.drawable.ic_star else R.drawable.ic_star_outline)
    }

    private fun showInfo(ch: Channel) {
        b.infoIndex.text = if (ch.kind == Kind.LIVE) "${index + 1} / ${queue.size}" else getString(R.string.tab_movies).uppercase()
        b.infoName.text = ch.name
        b.infoGroup.text = ch.group
        b.infoEpg.visibility = View.GONE
        showInfoPanel()

        epgJob?.cancel()
        val streamId = ch.streamId
        if (ch.kind == Kind.LIVE && streamId != null) {
            val client = Repository.xtreamClient() ?: return
            epgJob = lifecycleScope.launch {
                val lines = withContext(Dispatchers.IO) {
                    try { client.shortEpg(streamId) } catch (e: Exception) { emptyList() }
                }
                if (lines.isNotEmpty() && queue.getOrNull(index)?.id == ch.id) {
                    b.infoEpg.text = lines.joinToString("\n")
                    b.infoEpg.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showInfoPanel() {
        b.infoPanel.removeCallbacks(hideInfo)
        b.infoPanel.animate().cancel()
        b.infoPanel.alpha = 1f
        b.infoPanel.postDelayed(hideInfo, INFO_DURATION_MS)
    }

    /** Zapping à la télécommande quand les commandes sont masquées. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && isLive() && !b.playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    play(index - 1); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    play(index + 1); return true
                }
                KeyEvent.KEYCODE_INFO -> {
                    queue.getOrNull(index)?.let { showInfo(it) }; return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
