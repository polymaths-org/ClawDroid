package com.clawdroid.app

import android.app.Application
import com.clawdroid.app.core.AppContainer
import com.clawdroid.app.core.config.AppConfigManager

class ClawDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfigManager.init(this)
        AppContainer.init(this)
        // Fresh process (app was closed/killed): do not reopen the last thread.
        // ChatScreen creates a new session; history stays in the sidebar.
        runCatching { AppConfigManager.activeConversationId = null }
    }
}
