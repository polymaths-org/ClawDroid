package com.clawdroid.app.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clawdroid.app.MainActivity
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.selfmanage.SelfManageAlarmActivity
import com.clawdroid.app.R

object NotificationHelper {
    const val AGENT_CHANNEL_ID = "agent_activity"
    const val ACTION_TASK_MARK_DONE = "com.clawdroid.app.ACTION_TASK_MARK_DONE"
    const val ACTION_TASK_NEEDS_WORK = "com.clawdroid.app.ACTION_TASK_NEEDS_WORK"
    const val ACTION_TASK_VOICE_REPLY = "com.clawdroid.app.ACTION_TASK_VOICE_REPLY"
    const val EXTRA_TASK_SUMMARY = "task_summary"
    const val EXTRA_TASK_PROMPT = "task_prompt"
    const val TASK_ASK_NOTIFICATION_ID = 1110
    const val ACTION_AGENT_TRIGGER = "com.clawdroid.action.AGENT_TRIGGER"
    const val ACTION_SNOOZE = "com.clawdroid.action.SNOOZE"
    const val ACTION_SHOW_SNOOZED_NOTIFICATION = "com.clawdroid.action.SHOW_SNOOZED_NOTIFICATION"
    const val EXTRA_TRIGGER_ACTION = "trigger_action"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    const val EXTRA_NOTIFICATION_TITLE = "notification_title"
    const val EXTRA_NOTIFICATION_BODY = "notification_body"
    private const val FOREGROUND_CHANNEL_ID = "agent_foreground"
    private const val REMINDER_CHANNEL_ID = "agent_reminders"
    private const val ALARM_CHANNEL_ID = "self_manage_alarms"
    private const val AGENT_NOTIFICATION_ID = 1001
    private const val TASK_NOTIFICATION_ID = 1100
    private const val REMINDER_NOTIFICATION_ID = 2100
    private const val ALARM_NOTIFICATION_ID = 2400
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
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Progress, completion, and input-needed updates from ClawDroid agents."
            enableVibration(true)
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
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Self-management alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Full-screen alarms scheduled inside ClawDroid."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1200)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(activityChannel)
        manager.createNotificationChannel(foregroundChannel)
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(alarmChannel)
    }

    fun foregroundNotification(context: Context, text: String = "Agent ready"): Notification {
        ensureChannels(context)
        return baseBuilder(
            context = context,
            channelId = FOREGROUND_CHANNEL_ID,
            notificationId = AGENT_NOTIFICATION_ID,
            useBroadcastIntent = false,
        )
            .setContentTitle("ClawDroid")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun sendAgentNotification(
        context: Context,
        title: String,
        body: String,
        triggerAction: String = "open_app",
        notificationId: Int = AGENT_NOTIFICATION_ID,
    ) {
        ensureChannels(context)
        if (!AppConfigManager.notificationsEnabled) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationManagerCompat.from(context).notify(
            notificationId,
            baseBuilder(
                context = context,
                triggerAction = triggerAction,
                notificationId = notificationId,
            )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun sendAgentQuestion(context: Context, questionId: String, question: String, triggerAction: String) {
        val notificationId = 20_000 + questionId.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) } % 10_000
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val snoozeIntent = Intent(context, AgentNotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_TRIGGER_ACTION, triggerAction)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_SNOOZE_MINUTES, 15)
            putExtra(EXTRA_NOTIFICATION_TITLE, "ClawDroid has a question")
            putExtra(EXTRA_NOTIFICATION_BODY, question)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            notificationId,
            baseBuilder(
                context = context,
                triggerAction = triggerAction,
                notificationId = notificationId,
            )
                .setContentTitle("ClawDroid has a question")
                .setContentText(question)
                .setStyle(NotificationCompat.BigTextStyle().bigText(question))
                .addAction(R.mipmap.ic_launcher, "Remind in 15m", snoozePendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun scheduleSnoozedNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        triggerAction: String,
        delayMinutes: Int,
    ) {
        val delayMs = delayMinutes.coerceAtLeast(1) * 60_000L
        val alarmIntent = Intent(context, AgentNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_SNOOZED_NOTIFICATION
            putExtra(EXTRA_TRIGGER_ACTION, triggerAction)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_NOTIFICATION_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_BODY, body)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMs,
            pendingIntent,
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
            baseBuilder(
                context = context,
                channelId = REMINDER_CHANNEL_ID,
                notificationId = REMINDER_NOTIFICATION_ID + (reminderId.hashCode() and 0x0FFF),
                useBroadcastIntent = false,
            )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    fun sendSelfManageAlarmNotification(
        context: Context,
        alarmId: String,
        title: String,
        body: String,
    ) {
        ensureChannels(context)
        if (!AppConfigManager.notificationsEnabled) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notificationId = ALARM_NOTIFICATION_ID + (alarmId.hashCode() and 0x0FFF)
        val alarmIntent = Intent(context, SelfManageAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SelfManageAlarmActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(SelfManageAlarmActivity.EXTRA_ALARM_TITLE, title)
            putExtra(SelfManageAlarmActivity.EXTRA_ALARM_BODY, body)
            putExtra(SelfManageAlarmActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 800, 400, 800, 400, 1200))
                .build(),
        )
        runCatching { context.startActivity(alarmIntent) }
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
            baseBuilder(
                context = context,
                triggerAction = "task_notification",
                notificationId = TASK_NOTIFICATION_ID + idOffset,
            )
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

    private fun baseBuilder(
        context: Context,
        channelId: String = AGENT_CHANNEL_ID,
        triggerAction: String = "open_app",
        notificationId: Int = AGENT_NOTIFICATION_ID,
        useBroadcastIntent: Boolean = true,
    ): NotificationCompat.Builder {
        val pendingIntent = if (useBroadcastIntent) {
            val intent = Intent(context, AgentNotificationReceiver::class.java).apply {
                action = ACTION_AGENT_TRIGGER
                putExtra(EXTRA_TRIGGER_ACTION, triggerAction)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
    }
}
