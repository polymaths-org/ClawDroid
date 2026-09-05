package com.clawdroid.app.core.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AgentForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(42, NotificationHelper.foregroundNotification(this, "Agent service active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
