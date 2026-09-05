package com.clawdroid.app.core.heartbeat

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.clawdroid.app.core.agent.AgentConfigLoader
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import java.util.concurrent.TimeUnit

object HeartbeatManager {
    private const val TAG = "HeartbeatManager"
    private const val WORK_NAME_PREFIX = "heartbeat_"

    /**
     * Schedule all enabled heartbeat tasks from the agent config.
     * Each task gets its own WorkManager periodic work.
     */
    fun scheduleAll(context: Context) {
        val config = AgentConfigLoader.load(context)
        val enabled = config.heartbeats.filter { it.enabled }
        if (enabled.isEmpty()) {
            Log.i(TAG, "No heartbeat tasks to schedule")
            return
        }
        val workManager = WorkManager.getInstance(context)
        // Cancel existing heartbeats first
        workManager.cancelAllWorkByTag("heartbeat")

        enabled.forEach { hb ->
            // Parse cron to approximate interval (simple: treat as hours/days)
            val intervalHours = parseCronIntervalHours(hb.cron)
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(intervalHours, TimeUnit.HOURS)
                .addTag("heartbeat")
                .addTag("heartbeat_${hb.id}")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    androidx.work.Data.Builder()
                        .putString("task_id", hb.id)
                        .putString("prompt", hb.prompt)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "$WORK_NAME_PREFIX${hb.id}",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.i(TAG, "Scheduled heartbeat '${hb.id}' every ${intervalHours}h (cron: ${hb.cron})")
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("heartbeat")
    }

    /**
     * Rough cron-to-hours conversion.
     * Supports: "0 * * * *" (hourly), "0 0 * * *" (daily),
     * "0 0 * * 0" (weekly), "star-slash-N * * * *" (every N min -> ceil to hours)
     */
    private fun parseCronIntervalHours(cron: String): Long {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size < 5) return 24L
        val minute = parts[0]
        val hour = parts[1]
        val dayOfMonth = parts[2]
        val month = parts[3]
        val dayOfWeek = parts[4]

        return when {
            // Every N minutes
            minute.startsWith("*/") -> {
                val n = minute.removePrefix("*/").toLongOrNull() ?: 60
                maxOf(1, n / 60) // convert minutes to hours, min 1
            }
            // Specific minute, every hour
            hour == "*" && dayOfMonth == "*" && dayOfWeek == "*" -> 1L
            // Specific hour, specific day of week -> weekly
            dayOfWeek != "*" -> 168L
            // Specific hour, every day -> daily
            hour != "*" && dayOfMonth == "*" -> 24L
            // Specific day of month -> monthly
            dayOfMonth != "*" -> 720L
            else -> 24L
        }
    }
}

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        val prompt = inputData.getString("prompt") ?: return Result.failure()
        Log.i("HeartbeatWorker", "Running heartbeat task: $taskId")

        return try {
            val engine = AgentEngine(applicationContext)
            var resultText = ""
            // Run synchronously in the worker thread
            val job = kotlinx.coroutines.runBlocking {
                engine.run(prompt).collect { event ->
                    if (event is AgentRunEvent.Completed) {
                        resultText = event.finalText
                    }
                }
            }

            // Log result — in future, send to channel
            Log.i("HeartbeatWorker", "Task '$taskId' result: ${resultText.take(200)}")
            Result.success()
        } catch (e: Exception) {
            Log.w("HeartbeatWorker", "Heartbeat task '$taskId' failed", e)
            Result.retry()
        }
    }
}
