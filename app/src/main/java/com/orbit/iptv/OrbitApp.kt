package com.orbit.iptv

import android.app.Application
import com.orbit.iptv.data.Repo

class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
