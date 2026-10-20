package com.orbit.iptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.orbit.iptv.data.Playable
import com.orbit.iptv.data.Repo
import com.orbit.iptv.player.PlayerActivity
import com.orbit.iptv.player.PlayerSession
import com.orbit.iptv.ui.theme.LocalTv
import com.orbit.iptv.ui.theme.OrbitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val tv = isTv()
        if (Repo.activeSource.value != null) Repo.loadCatalog(force = false)
        setContent {
            OrbitTheme {
                CompositionLocalProvider(LocalTv provides tv) {
                    OrbitApp(onPlay = ::startPlayer)
                }
            }
        }
    }

    private fun startPlayer(queue: List<Playable>, index: Int, startMs: Long) {
        if (queue.isEmpty()) return
        PlayerSession.start(queue, index, startMs)
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}

fun Context.isTv(): Boolean {
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
