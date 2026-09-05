package com.clawdroid.app.core.notifications

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.*

/**
 * Lightweight background foreground service for agent automations (heartbeat, channels).
 * Does NOT use the microphone — voice recognition only happens in-app when the user
 * opens the voice call interface.
 */
class AgentForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "AgentForegroundService"
        private const val NOTIFICATION_ID = 42
    }

    override fun onCreate() {
        super.onCreate()

        if (!AppConfigManager.ultraAgentEnabled) {
            Log.i(TAG, "Ultra Agent not enabled, stopping service")
            stopSelf()
            return
        }

        // Start foreground with dataSync type — no microphone access
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                NotificationHelper.foregroundNotification(this, "Agent background service active"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                NotificationHelper.foregroundNotification(this, "Agent background service active")
            )
        }

        Log.i(TAG, "Background agent service started (no mic)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppConfigManager.ultraAgentEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "Background agent service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
