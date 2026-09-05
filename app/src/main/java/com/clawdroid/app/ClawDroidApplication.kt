package com.clawdroid.app

import android.app.Application
import com.clawdroid.app.core.config.AppConfigManager

class ClawDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfigManager.init(this)
    }
}
