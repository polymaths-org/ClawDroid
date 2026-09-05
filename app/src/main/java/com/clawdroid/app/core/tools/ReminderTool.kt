package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.reminders.ReminderManager
import org.json.JSONArray
import org.json.JSONObject

object ReminderTool {
    fun setReminder(context: Context, args: JSONObject): JSONObject {
        val record = ReminderManager.create(
            context = context,
            kind = args.optString("kind", "reminder"),
            title = args.getString("title"),
            note = args.optString("note", ""),
            triggerAt = args.optString("trigger_at").takeIf { it.isNotBlank() },
            deliveryMode = args.optString("delivery_mode").takeIf { it.isNotBlank() },
            runAgent = args.optBoolean("run_agent", false),
            agentPrompt = args.optString("agent_prompt").takeIf { it.isNotBlank() },
        )
        return JSONObject()
            .put("success", true)
            .put("reminder", ReminderManager.toJson(record))
    }

    fun listReminders(context: Context, includeCompleted: Boolean): JSONObject {
        val arr = JSONArray()
        ReminderManager.list(context, includeCompleted).forEach { arr.put(ReminderManager.toJson(it)) }
        return JSONObject()
            .put("success", true)
            .put("reminders", arr)
    }

    fun cancelReminder(context: Context, reminderId: String): JSONObject {
        val canceled = ReminderManager.cancel(context, reminderId)
        return JSONObject()
            .put("success", canceled)
            .put("reminder_id", reminderId)
    }
}
