package com.clawdroid.app.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Manages the foreground service lifecycle.
 * Provides a clean API for the UI to start/stop background agent mode.
 */
object ServiceManager {

    /**
     * Start the foreground service with full background agent capabilities.
     */
    fun start(context: Context) {
        val intent = Intent(context, EnhancedForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Stop the foreground service and background agent.
     */
    fun stop(context: Context) {
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
}
