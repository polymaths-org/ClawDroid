package com.clawdroid.app.core.terminal

import android.content.Context

object ProcessManagerProvider {
    @Volatile private var instance: ProcessManager? = null

    fun get(context: Context): ProcessManager = instance ?: synchronized(this) {
        instance ?: ProcessManager(context.applicationContext).also { instance = it }
    }
}
