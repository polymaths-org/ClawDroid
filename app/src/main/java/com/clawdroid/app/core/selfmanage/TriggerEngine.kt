package com.clawdroid.app.core.selfmanage

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TriggerEngine(context: Context) {
    private val appContext = context.applicationContext
    private val askManager = AgentAskManager(appContext)
    private val selfManageRepo = SelfManageRepository(appContext)

    suspend fun evaluateTriggers(now: Long = System.currentTimeMillis()) {
        selfManageRepo.getDueReminders(now).take(5).forEach { reminder ->
            askManager.ask(
                AgentQuestion(
                    id = "reminder_${reminder.id}_${reminder.dueAt}",
                    question = "Reminder: ${reminder.title}. Review it now?",
                    context = "reminder_due",
                    suggestedActions = listOf("show_reminder", "dismiss", "snooze"),
                    priority = reminder.priority,
                    expiresAt = now + 60 * 60 * 1000L,
                )
            )
        }

        selfManageRepo.getOverdueTodos(now).take(1).forEach { todo ->
            askManager.ask(
                AgentQuestion(
                    id = "todo_${todo.id}_${todo.dueAt ?: 0}",
                    question = "Task overdue: ${todo.title}. Review your todos?",
                    context = "overdue_todos",
                    suggestedActions = listOf("show_list", "reschedule", "dismiss"),
                    priority = todo.priority,
                    expiresAt = now + 2 * 60 * 60 * 1000L,
                )
            )
        }

        selfManageRepo.getNextAlarm()?.let { alarm ->
            val label = "${alarm.hour.toString().padStart(2, '0')}:${alarm.minute.toString().padStart(2, '0')}"
            askManager.ask(
                AgentQuestion(
                    id = "alarm_${alarm.id}_${Date(now).dateKey()}",
                    question = "Next alarm is $label for ${alarm.label}. All set?",
                    context = "alarm_upcoming",
                    suggestedActions = listOf("confirm", "reschedule", "cancel"),
                    priority = 6,
                    expiresAt = now + 30 * 60 * 1000L,
                )
            )
        }
    }
}

private fun Date.dateKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(this)
