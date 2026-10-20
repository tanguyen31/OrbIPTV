package com.orbit.iptv.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.orbit.iptv.ui.isTv
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.OrbitTheme

class PlayerActivity : ComponentActivity() {

    private lateinit var controller: PlaybackController
    private var inPip by mutableStateOf(false)
    private var tv = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        tv = isTv()
        controller = PlaybackController(applicationContext)
        if (PlayerSession.queue.isEmpty()) {
            finish()
            return
        }
        setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalTv provides tv) {
                    PlayerScreen(controller, inPip = inPip, onExit = { finish() })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.reloadIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        if (::controller.isInitialized && PlayerSession.queue.isNotEmpty()) controller.attach()
    }

    override fun onStop() {
        super.onStop()
        if (::controller.isInitialized) controller.release()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!tv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && controller.isPlaying &&
            packageManager.hasSystemFeature("android.software.picture_in_picture")
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
