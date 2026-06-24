package com.clawdroid.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.clawdroid.app.MainActivity

class AgentNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_AGENT_TRIGGER -> {
                val triggerAction = intent.getStringExtra(NotificationHelper.EXTRA_TRIGGER_ACTION) ?: "open_app"
                val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
                if (notificationId != 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(NotificationHelper.EXTRA_TRIGGER_ACTION, triggerAction)
                }
                context.startActivity(launchIntent)
            }
            NotificationHelper.ACTION_SNOOZE -> {
                val triggerAction = intent.getStringExtra(NotificationHelper.EXTRA_TRIGGER_ACTION) ?: return
                val minutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 15)
                val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
                val title = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TITLE) ?: "ClawDroid"
                val body = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_BODY) ?: "Tap to continue."
                if (notificationId != 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                NotificationHelper.scheduleSnoozedNotification(
                    context = context,
                    notificationId = notificationId.takeIf { it != 0 } ?: 1001,
                    title = title,
                    body = body,
                    triggerAction = triggerAction,
                    delayMinutes = minutes,
                )
            }
            NotificationHelper.ACTION_SHOW_SNOOZED_NOTIFICATION -> {
                val triggerAction = intent.getStringExtra(NotificationHelper.EXTRA_TRIGGER_ACTION) ?: "open_app"
                val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 1001)
                val title = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_TITLE) ?: "ClawDroid"
                val body = intent.getStringExtra(NotificationHelper.EXTRA_NOTIFICATION_BODY) ?: "Tap to continue."
                NotificationHelper.sendAgentNotification(
                    context = context,
                    title = title,
                    body = body,
                    triggerAction = triggerAction,
                    notificationId = notificationId,
                )
            }
        }
    }
}
