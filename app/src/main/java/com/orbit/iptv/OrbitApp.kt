package com.orbit.iptv

import android.app.Application
import com.orbit.iptv.data.Repository

class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
    }
}
