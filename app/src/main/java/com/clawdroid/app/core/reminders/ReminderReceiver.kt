package com.clawdroid.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.BackgroundAgentRunner
import com.clawdroid.app.core.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_ID) ?: return
        val record = ReminderManager.get(context, reminderId) ?: return
        ReminderManager.markCompleted(context, reminderId)

        if (record.deliveryMode != "silent") {
            NotificationHelper.sendReminderNotification(
                context = context,
                reminderId = record.id,
                title = record.title,
                body = record.note.ifBlank { record.kind.replaceFirstChar { it.uppercaseChar() } },
                voiceMode = record.deliveryMode == "voice" || record.deliveryMode == "both",
            )
        }

        if (record.runAgent && AppConfigManager.scheduledAgentRunsEnabled) {
            val pendingResult = goAsync()
            val prompt = record.agentPrompt.ifBlank {
                "Scheduled ${record.kind}: ${record.title}\n${record.note}".trim()
            }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val conversationId = AppConfigManager.activeConversationId
                        ?: "scheduled_reminders"
                    BackgroundAgentRunner.runAgentInBackground(
                        context = context.applicationContext,
                        projectId = AppConfigManager.activeProjectId,
                        conversationId = conversationId,
                        prompt = prompt,
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
