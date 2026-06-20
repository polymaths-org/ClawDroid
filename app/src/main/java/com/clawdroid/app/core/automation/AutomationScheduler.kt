package com.clawdroid.app.core.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutomationScheduler {
    private const val WORK_NAME = "clawdroid_automation_tick"
    private const val IMMEDIATE_WORK_NAME = "clawdroid_automation_now"

    fun schedule(context: Context) {
        val interval = com.clawdroid.app.core.config.AppConfigManager.heartbeatIntervalMin.toLong().coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<AutomationWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scheduleOrCancel(context: Context) {
        if (com.clawdroid.app.core.config.AppConfigManager.heartbeatEnabled ||
            com.clawdroid.app.core.config.AppConfigManager.ultraAgentEnabled ||
            com.clawdroid.app.core.config.AppConfigManager.proactiveAssistantEnabled
        ) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }
}
