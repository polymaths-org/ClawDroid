package com.clawdroid.app.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.clawdroid.app.core.config.AppConfigManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

data class ReminderRecord(
    val id: String,
    val kind: String,
    val title: String,
    val note: String,
    val triggerAtMillis: Long?,
    val deliveryMode: String,
    val runAgent: Boolean,
    val agentPrompt: String,
    val completed: Boolean,
    val createdAtMillis: Long,
)

object ReminderManager {
    private const val PREFS = "clawdroid_reminders"
    private const val KEY_RECORDS = "records"
    const val EXTRA_REMINDER_ID = "reminder_id"

    fun create(
        context: Context,
        kind: String,
        title: String,
        note: String,
        triggerAt: String?,
        deliveryMode: String?,
        runAgent: Boolean,
        agentPrompt: String?,
    ): ReminderRecord {
        require(AppConfigManager.remindersEnabled) { "Reminders are disabled in settings." }
        val cleanKind = kind.ifBlank { "reminder" }.lowercase()
        val triggerAtMillis = triggerAt?.takeIf { it.isNotBlank() }?.let { parseTriggerMillis(it) }
        val record = ReminderRecord(
            id = UUID.randomUUID().toString(),
            kind = cleanKind,
            title = title.ifBlank { "Reminder" },
            note = note,
            triggerAtMillis = triggerAtMillis,
            deliveryMode = normalizeDeliveryMode(deliveryMode),
            runAgent = runAgent,
            agentPrompt = agentPrompt.orEmpty(),
            completed = false,
            createdAtMillis = System.currentTimeMillis(),
        )
        saveAll(context, list(context).filterNot { it.id == record.id } + record)
        if (triggerAtMillis != null) schedule(context, record)
        return record
    }

    fun list(context: Context, includeCompleted: Boolean = true): List<ReminderRecord> {
        val records = readAll(context)
        return if (includeCompleted) records else records.filterNot { it.completed }
    }

    fun get(context: Context, id: String): ReminderRecord? = readAll(context).firstOrNull { it.id == id }

    fun cancel(context: Context, id: String): Boolean {
        val records = readAll(context)
        val record = records.firstOrNull { it.id == id } ?: return false
        cancelAlarm(context, record)
        saveAll(context, records.filterNot { it.id == id })
        return true
    }

    fun markCompleted(context: Context, id: String) {
        saveAll(
            context,
            readAll(context).map { if (it.id == id) it.copy(completed = true) else it },
        )
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        readAll(context)
            .filter { !it.completed && it.triggerAtMillis != null && it.triggerAtMillis > now }
            .forEach { schedule(context, it) }
    }

    private fun schedule(context: Context, record: ReminderRecord) {
        val triggerAt = record.triggerAtMillis ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_REMINDER_ID, record.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            record.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, record: ReminderRecord) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            record.id.hashCode(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_REMINDER_ID, record.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun normalizeDeliveryMode(mode: String?): String {
        return when (mode?.lowercase()?.trim()) {
            "voice", "both", "silent", "notification" -> mode.lowercase().trim()
            else -> AppConfigManager.reminderDefaultDeliveryMode
        }
    }

    private fun parseTriggerMillis(raw: String): Long {
        raw.toLongOrNull()?.let { return it }
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                val zone = ZoneId.systemDefault()
                LocalDateTime.parse(raw).atZone(zone).toInstant().toEpochMilli()
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): List<ReminderRecord> {
        val json = prefs(context).getString(KEY_RECORDS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(json) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { index ->
            runCatching {
                val obj = arr.getJSONObject(index)
                ReminderRecord(
                    id = obj.getString("id"),
                    kind = obj.optString("kind", "reminder"),
                    title = obj.optString("title", "Reminder"),
                    note = obj.optString("note", ""),
                    triggerAtMillis = obj.optLong("triggerAtMillis").takeIf { obj.has("triggerAtMillis") && it > 0L },
                    deliveryMode = obj.optString("deliveryMode", "notification"),
                    runAgent = obj.optBoolean("runAgent", false),
                    agentPrompt = obj.optString("agentPrompt", ""),
                    completed = obj.optBoolean("completed", false),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L),
                )
            }.getOrNull()
        }
    }

    private fun saveAll(context: Context, records: List<ReminderRecord>) {
        val arr = JSONArray()
        records
            .sortedWith(compareBy<ReminderRecord> { it.completed }.thenBy { it.triggerAtMillis ?: Long.MAX_VALUE })
            .forEach { record ->
                arr.put(
                    JSONObject()
                        .put("id", record.id)
                        .put("kind", record.kind)
                        .put("title", record.title)
                        .put("note", record.note)
                        .apply { record.triggerAtMillis?.let { put("triggerAtMillis", it) } }
                        .put("deliveryMode", record.deliveryMode)
                        .put("runAgent", record.runAgent)
                        .put("agentPrompt", record.agentPrompt)
                        .put("completed", record.completed)
                        .put("createdAtMillis", record.createdAtMillis),
                )
            }
        prefs(context).edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    fun toJson(record: ReminderRecord): JSONObject {
        return JSONObject()
            .put("id", record.id)
            .put("kind", record.kind)
            .put("title", record.title)
            .put("note", record.note)
            .put("trigger_at", record.triggerAtMillis?.let {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toString()
            } ?: JSONObject.NULL)
            .put("delivery_mode", record.deliveryMode)
            .put("run_agent", record.runAgent)
            .put("completed", record.completed)
    }
}
