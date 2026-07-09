package com.clawdroid.app.core.selfmanage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clawdroid.app.core.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SelfManageAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SelfManageScheduler.ACTION_TRIGGER) return
        val id = intent.getStringExtra(SelfManageScheduler.EXTRA_ID) ?: return
        val kind = intent.getStringExtra(SelfManageScheduler.EXTRA_KIND).orEmpty()
        val fallbackTitle = intent.getStringExtra(SelfManageScheduler.EXTRA_TITLE).orEmpty()
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SelfManageRepository(context.applicationContext)
                when (kind) {
                    "alarm" -> handleAlarm(context, repo, id, fallbackTitle)
                    "reminder" -> handleReminder(context, repo, id, fallbackTitle)
                    "todo" -> handleTodo(context, repo, id, fallbackTitle)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(
        context: Context,
        repo: SelfManageRepository,
        id: String,
        fallbackTitle: String,
    ) {
        val alarm = repo.getAllAlarms().first().firstOrNull { it.id == id }
        val title = alarm?.label?.ifBlank { null } ?: fallbackTitle.ifBlank { "Alarm" }
        NotificationHelper.sendSelfManageAlarmNotification(
            context = context,
            alarmId = id,
            title = title,
            body = "Alarm due now",
        )
        if (alarm != null) {
            val triggered = alarm.copy(
                enabled = alarm.daysOfWeek.isNotEmpty(),
                lastTriggered = System.currentTimeMillis(),
            )
            repo.updateAlarm(triggered)
        }
    }

    private suspend fun handleReminder(
        context: Context,
        repo: SelfManageRepository,
        id: String,
        fallbackTitle: String,
    ) {
        val reminder = repo.getAllReminders().first().firstOrNull { it.id == id }
        val title = reminder?.title?.ifBlank { null } ?: fallbackTitle.ifBlank { "Reminder" }
        val body = reminder?.description?.ifBlank { "Reminder due now" } ?: "Reminder due now"
        NotificationHelper.sendReminderNotification(
            context = context,
            reminderId = id,
            title = title,
            body = body,
            voiceMode = false,
        )
        if (reminder != null && reminder.recurring && reminder.intervalMinutes != null && reminder.intervalMinutes > 0) {
            repo.updateReminder(
                reminder.copy(
                    dueAt = System.currentTimeMillis() + reminder.intervalMinutes * 60_000L,
                    completed = false,
                ),
            )
        } else {
            repo.completeReminder(id)
        }
    }

    private suspend fun handleTodo(
        context: Context,
        repo: SelfManageRepository,
        id: String,
        fallbackTitle: String,
    ) {
        val todo = repo.getAllTodos().first().firstOrNull { it.id == id }
        if (todo == null || todo.completed) return
        val title = todo.title.ifBlank { fallbackTitle.ifBlank { "Todo due" } }
        val body = todo.description.ifBlank { "Todo is due now" }
        NotificationHelper.sendReminderNotification(
            context = context,
            reminderId = id,
            title = title,
            body = body,
            voiceMode = false,
        )
        AgentAskManager(context).ask(
            AgentQuestion(
                id = "todo_due_${todo.id}_${todo.dueAt ?: 0}",
                question = "Todo due: $title. Should I mark it done, reschedule it, or help with it now?",
                context = "todo_due",
                suggestedActions = listOf("mark_done", "reschedule", "help_now"),
                priority = todo.priority,
                expiresAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L,
            ),
        )
    }
}
