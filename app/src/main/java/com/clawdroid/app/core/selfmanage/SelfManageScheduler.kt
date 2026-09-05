package com.clawdroid.app.core.selfmanage

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object SelfManageScheduler {
    const val ACTION_TRIGGER = "com.clawdroid.action.SELF_MANAGE_TRIGGER"
    const val EXTRA_ID = "self_manage_id"
    const val EXTRA_KIND = "self_manage_kind"
    const val EXTRA_TITLE = "self_manage_title"

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        val triggerAt = nextAlarmMillis(alarm)
        val pendingIntent = pendingIntent(
            context = context,
            id = alarm.id,
            kind = "alarm",
            title = alarm.label.ifBlank { "Alarm" },
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, pendingIntent),
            pendingIntent,
        )
    }

    fun scheduleReminder(context: Context, reminder: Reminder) {
        if (reminder.completed) {
            cancel(context, reminder.id)
            return
        }
        val now = System.currentTimeMillis()
        if (reminder.dueAt <= now) return
        val pendingIntent = pendingIntent(
            context = context,
            id = reminder.id,
            kind = "reminder",
            title = reminder.title.ifBlank { "Reminder" },
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.dueAt, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.dueAt, pendingIntent)
        }
    }

    fun cancel(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, id, "alarm", ""))
        alarmManager.cancel(pendingIntent(context, id, "reminder", ""))
    }

    private fun pendingIntent(
        context: Context,
        id: String,
        kind: String,
        title: String,
    ): PendingIntent {
        val intent = Intent(context, SelfManageAlarmReceiver::class.java)
            .setAction(ACTION_TRIGGER)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_KIND, kind)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context,
            requestCode(id, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(id: String, kind: String): Int = 31 * id.hashCode() + kind.hashCode()

    private fun nextAlarmMillis(alarm: Alarm, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        var best: Long? = null
        val activeDays = alarm.daysOfWeek.map { it.toCalendarDay() }.toSet()

        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour.coerceIn(0, 23))
                set(Calendar.MINUTE, alarm.minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayMatches = activeDays.isEmpty() || candidate.get(Calendar.DAY_OF_WEEK) in activeDays
            if (dayMatches && candidate.timeInMillis > now.timeInMillis) {
                val millis = candidate.timeInMillis
                if (best == null || millis < best!!) best = millis
            }
        }

        return best ?: Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, alarm.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, alarm.minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun Int.toCalendarDay(): Int = when (this) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> this.coerceIn(Calendar.SUNDAY, Calendar.SATURDAY)
    }
}
