package com.clawdroid.app.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clawdroid.app.MainActivity
import com.clawdroid.app.R

object NotificationHelper {
    const val AGENT_CHANNEL_ID = "agent_activity"
    private const val AGENT_NOTIFICATION_ID = 1001
    private const val TASK_NOTIFICATION_ID = 1100
    @Volatile private var appVisible: Boolean = false

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
    }

    fun isAppVisible(): Boolean = appVisible

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            AGENT_CHANNEL_ID,
            "Agent activity",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Progress, completion, and input-needed updates from ClawDroid agents."
        }
        manager.createNotificationChannel(channel)
    }

    fun foregroundNotification(context: Context, text: String = "Agent ready"): Notification {
        ensureChannels(context)
        return baseBuilder(context)
            .setContentTitle("ClawDroid")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    fun sendAgentNotification(context: Context, title: String, body: String) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationManagerCompat.from(context).notify(
            AGENT_NOTIFICATION_ID,
            baseBuilder(context)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showNotification(context: Context, title: String, body: String) {
        sendAgentNotification(context, title, body)
    }

    fun sendTaskStarted(context: Context, prompt: String) {
        if (appVisible) return
        sendTaskNotification(
            context = context,
            title = "ClawDroid is working",
            body = summarize(prompt).ifBlank { "Your task is running in the background." },
            idOffset = 1,
        )
    }

    fun sendTaskComplete(context: Context, result: String) {
        if (appVisible) return
        sendTaskNotification(
            context = context,
            title = "Task complete",
            body = summarize(result).ifBlank { "ClawDroid finished the task." },
            idOffset = 2,
        )
    }

    fun sendTaskFailed(context: Context, reason: String) {
        if (appVisible) return
        sendTaskNotification(
            context = context,
            title = "Task needs attention",
            body = summarize(reason).ifBlank { "ClawDroid could not finish the task." },
            idOffset = 3,
        )
    }

    private fun sendTaskNotification(
        context: Context,
        title: String,
        body: String,
        idOffset: Int,
    ) {
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            TASK_NOTIFICATION_ID + idOffset,
            baseBuilder(context)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun summarize(text: String): String {
        return text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("\\s+"), " ")
            ?.take(140)
            ?: ""
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, AGENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }
}
