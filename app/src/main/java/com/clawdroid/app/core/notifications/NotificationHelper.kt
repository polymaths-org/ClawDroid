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
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.MainActivity
import com.clawdroid.app.R

object NotificationHelper {
    const val AGENT_CHANNEL_ID = "agent_activity"
    const val ACTION_TASK_MARK_DONE = "com.clawdroid.app.ACTION_TASK_MARK_DONE"
    const val ACTION_TASK_NEEDS_WORK = "com.clawdroid.app.ACTION_TASK_NEEDS_WORK"
    const val ACTION_TASK_VOICE_REPLY = "com.clawdroid.app.ACTION_TASK_VOICE_REPLY"
    const val EXTRA_TASK_SUMMARY = "task_summary"
    const val EXTRA_TASK_PROMPT = "task_prompt"
    const val TASK_ASK_NOTIFICATION_ID = 1110
    private const val FOREGROUND_CHANNEL_ID = "agent_foreground"
    private const val REMINDER_CHANNEL_ID = "agent_reminders"
    private const val AGENT_NOTIFICATION_ID = 1001
    private const val TASK_NOTIFICATION_ID = 1100
    private const val REMINDER_NOTIFICATION_ID = 2100
    @Volatile private var appVisible: Boolean = false

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
    }

    fun isAppVisible(): Boolean = appVisible

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val activityChannel = NotificationChannel(
            AGENT_CHANNEL_ID,
            "Agent activity",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Progress, completion, and input-needed updates from ClawDroid agents."
        }
        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Background agent status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent status for background agent services."
            setShowBadge(false)
        }
        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Reminders and alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Scheduled reminders, todos, alarms, and agent task prompts."
        }
        manager.createNotificationChannel(activityChannel)
        manager.createNotificationChannel(foregroundChannel)
        manager.createNotificationChannel(reminderChannel)
    }

    fun foregroundNotification(context: Context, text: String = "Agent ready"): Notification {
        ensureChannels(context)
        return baseBuilder(context, FOREGROUND_CHANNEL_ID)
            .setContentTitle("ClawDroid")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun sendAgentNotification(context: Context, title: String, body: String) {
        ensureChannels(context)
        if (!AppConfigManager.notificationsEnabled) return
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

    fun sendReminderNotification(
        context: Context,
        reminderId: String,
        title: String,
        body: String,
        voiceMode: Boolean,
    ) {
        ensureChannels(context)
        if (!AppConfigManager.notificationsEnabled) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (voiceMode) putExtra("START_VOICE_SESSION", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            REMINDER_NOTIFICATION_ID + (reminderId.hashCode() and 0x0FFF),
            baseBuilder(context, REMINDER_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    fun sendTaskStarted(context: Context, prompt: String) {
        if (appVisible) return
        if (!AppConfigManager.notificationsEnabled || !AppConfigManager.taskStartedNotificationsEnabled) return
        sendTaskNotification(
            context = context,
            title = "ClawDroid is working",
            body = summarize(prompt).ifBlank { "Your task is running in the background." },
            idOffset = 1,
        )
    }

    fun sendTaskComplete(context: Context, result: String) {
        if (appVisible) return
        if (!AppConfigManager.notificationsEnabled) return
        when (AppConfigManager.taskCompletionNotificationMode) {
            "silent" -> return
            "ask" -> {
                sendTaskAskNotification(context, result)
                return
            }
        }
        sendTaskNotification(
            context = context,
            title = "Task complete",
            body = summarize(result).ifBlank { "ClawDroid finished the task." },
            idOffset = 2,
        )
    }

    fun sendTaskFailed(context: Context, reason: String) {
        if (appVisible) return
        if (!AppConfigManager.notificationsEnabled || !AppConfigManager.taskFailedNotificationsEnabled) return
        sendTaskNotification(
            context = context,
            title = "Task needs attention",
            body = summarize(reason).ifBlank { "ClawDroid could not finish the task." },
            idOffset = 3,
        )
    }

    private fun sendTaskAskNotification(context: Context, result: String) {
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val summary = summarize(result).ifBlank { "ClawDroid finished the task." }
        val owner = AppConfigManager.ownerName.takeIf { it.isNotBlank() } ?: "there"
        val prompt = "Hey $owner, this task looks done. Should I mark it complete?"
        val details = "$prompt\n\n$summary"

        fun actionIntent(action: String): PendingIntent {
            val intent = Intent(context, TaskAskReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_SUMMARY, summary)
                putExtra(EXTRA_TASK_PROMPT, prompt)
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = baseBuilder(context)
            .setContentTitle("Task complete?")
            .setContentText(prompt)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setAutoCancel(false)
            .addAction(android.R.drawable.checkbox_on_background, "Mark done", actionIntent(ACTION_TASK_MARK_DONE))
            .addAction(android.R.drawable.ic_menu_edit, "Needs work", actionIntent(ACTION_TASK_NEEDS_WORK))
            .addAction(android.R.drawable.ic_btn_speak_now, "Voice", actionIntent(ACTION_TASK_VOICE_REPLY))
            .build()

        manager.notify(TASK_ASK_NOTIFICATION_ID, notification)
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

    private fun baseBuilder(context: Context, channelId: String = AGENT_CHANNEL_ID): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
    }
}
