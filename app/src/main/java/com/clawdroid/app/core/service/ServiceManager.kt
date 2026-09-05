package com.clawdroid.app.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.agent.BackgroundAgent
import com.clawdroid.app.core.config.AppConfigManager

/**
 * Manages the foreground service lifecycle.
 * Provides a clean API for the UI to start/stop background agent mode.
 */
object ServiceManager {

    private var backgroundAgent: BackgroundAgent? = null

    /**
     * Start the foreground service with full background agent capabilities.
     */
    fun start(context: Context) {
        val intent = Intent(context, EnhancedForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)

        // Start background agent
        if (backgroundAgent == null) {
            backgroundAgent = BackgroundAgent(context.applicationContext)
        }
        backgroundAgent?.start()
    }

    /**
     * Stop the foreground service and background agent.
     */
    fun stop(context: Context) {
        backgroundAgent?.stop()
        backgroundAgent = null
        val intent = Intent(context, EnhancedForegroundService::class.java)
        context.stopService(intent)
    }

    /**
     * Restart when config changes (e.g., channels toggled).
     */
    fun restart(context: Context) {
        stop(context)
        start(context)
    }

    /**
     * Returns the current background agent instance, if running.
     */
    fun getBackgroundAgent(): BackgroundAgent? = backgroundAgent
}
