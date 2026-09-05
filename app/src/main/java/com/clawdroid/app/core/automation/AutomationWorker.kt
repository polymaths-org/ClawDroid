package com.clawdroid.app.core.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clawdroid.app.data.db.ClawDroidDatabase

class AutomationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val enabledAutomations = ClawDroidDatabase.get(applicationContext)
            .automations()
            .getEnabled()

        // Execution routing will be connected after the foreground service/notification layer is in place.
        // For now this worker proves scheduling, persistence lookup, and reboot recovery plumbing.
        return if (enabledAutomations.isNotEmpty()) Result.success() else Result.success()
    }
}
