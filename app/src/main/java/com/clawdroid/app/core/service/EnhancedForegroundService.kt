package com.clawdroid.app.core.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.clawdroid.app.core.agent.BackgroundAgent
import com.clawdroid.app.core.notifications.NotificationHelper

/**
 * Enhanced foreground service that powers the background agent.
 * Runs 24/7 with dataSync foreground type to support channels,
 * heartbeats, and background message processing.
 *
 * Compared to the existing AgentForegroundService (microphone-based),
 * this provides the full background agent stack.
 */
class EnhancedForegroundService : Service() {

    private var backgroundAgent: BackgroundAgent? = null

    companion object {
        private const val TAG = "EnhancedForegroundService"
        private const val NOTIFICATION_ID = 43
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating EnhancedForegroundService")

        // Start foreground with dataSync type
        val notification = NotificationHelper.foregroundNotification(
            this,
            "Background agent active"
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Initialize background agent
        backgroundAgent = BackgroundAgent(applicationContext)
        backgroundAgent?.start()

        // Observe agent status and update notification
        observeStatus()
    }

    private fun observeStatus() {
        android.os.Handler(mainLooper).postDelayed(object : Runnable {
            override fun run() {
                val agent = backgroundAgent
                if (agent != null && agent.isRunning.value) {
                    val status = agent.status.value
                    val notification = NotificationHelper.foregroundNotification(
                        this@EnhancedForegroundService,
                        status
                    )
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
                if (backgroundAgent?.isRunning?.value == true) {
                    android.os.Handler(mainLooper).postDelayed(this, 30_000)
                }
            }
        }, 30_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed from recents — keeping service alive")
        val notification = NotificationHelper.foregroundNotification(
            this,
            "Agent running in background — tap to reopen"
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundAgent?.stop()
        backgroundAgent = null
        Log.i(TAG, "EnhancedForegroundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
