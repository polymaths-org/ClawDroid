package com.clawdroid.app.core.selfmanage

import android.content.Context
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object SelfManageTools {
    suspend fun addAlarm(context: Context, args: JSONObject): JSONObject {
        val alarm = Alarm(
            label = args.getString("label"),
            hour = args.getInt("hour"),
            minute = args.getInt("minute"),
            daysOfWeek = args.optJSONArray("days_of_week").toIntSet(),
            enabled = args.optBoolean("enabled", true),
            createdAt = System.currentTimeMillis(),
        )
        SelfManageRepository(context).addAlarm(alarm)
        return JSONObject().put("ok", true).put("type", "alarm").put("id", alarm.id)
    }

    suspend fun addReminder(context: Context, args: JSONObject): JSONObject {
        val reminder = Reminder(
            title = args.getString("title"),
            description = args.optString("description"),
            dueAt = args.getLong("due_at"),
            priority = args.optInt("priority", 5),
            recurring = args.optBoolean("recurring", false),
            intervalMinutes = args.optIntOrNull("interval_minutes"),
            category = args.optString("category", "general"),
            createdBy = args.optString("created_by", "agent"),
        )
        SelfManageRepository(context).addReminder(reminder)
        return JSONObject().put("ok", true).put("type", "reminder").put("id", reminder.id)
    }

    suspend fun addTodo(context: Context, args: JSONObject): JSONObject {
        val todo = Todo(
            title = args.getString("title"),
            description = args.optString("description"),
            dueAt = args.optLongOrNull("due_at"),
            priority = args.optInt("priority", 5),
            tags = args.optJSONArray("tags").toStringList(),
            createdBy = args.optString("created_by", "agent"),
        )
        SelfManageRepository(context).addTodo(todo)
        return JSONObject().put("ok", true).put("type", "todo").put("id", todo.id)
    }

    suspend fun list(context: Context): JSONObject {
        val repo = SelfManageRepository(context)
        return JSONObject()
            .put("alarms", JSONArray(repo.getAllAlarms().first().map { it.toJson() }))
            .put("reminders", JSONArray(repo.getAllReminders().first().map { it.toJson() }))
            .put("todos", JSONArray(repo.getAllTodos().first().map { it.toJson() }))
    }

    suspend fun completeReminder(context: Context, args: JSONObject): JSONObject {
        val id = args.getString("id")
        SelfManageRepository(context).completeReminder(id)
        return JSONObject().put("ok", true).put("id", id)
    }

    suspend fun completeTodo(context: Context, args: JSONObject): JSONObject {
        val id = args.getString("id")
        SelfManageRepository(context).completeTodo(id)
        return JSONObject().put("ok", true).put("id", id)
    }
}

object AgentAskTools {
    suspend fun ask(context: Context, args: JSONObject): JSONObject {
        val question = AgentQuestion(
            question = args.getString("question"),
            context = args.optString("context", "agent_ask"),
            suggestedActions = args.optJSONArray("suggested_actions").toStringList(),
            priority = args.optInt("priority", 5),
            expiresAt = args.optLongOrNull("expires_at"),
        )
        AgentAskManager(context).ask(question)
        return JSONObject().put("ok", true).put("id", question.id)
    }

    suspend fun answer(context: Context, args: JSONObject): JSONObject {
        val questionId = args.getString("question_id")
        val answer = args.getString("answer")
        AgentAskManager(context).answer(questionId, answer)
        return JSONObject().put("ok", true).put("question_id", questionId).put("answer", answer)
    }
}

private fun Alarm.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("hour", hour)
    .put("minute", minute)
    .put("days_of_week", JSONArray(daysOfWeek.sorted()))
    .put("enabled", enabled)

private fun Reminder.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("description", description)
    .put("due_at", dueAt)
    .put("priority", priority)
    .put("completed", completed)
    .put("category", category)
    .put("created_by", createdBy)

private fun Todo.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("description", description)
    .put("due_at", dueAt)
    .put("priority", priority)
    .put("completed", completed)
    .put("tags", JSONArray(tags))
    .put("created_by", createdBy)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { value -> value.isNotBlank() } }
}

private fun JSONArray?.toIntSet(): Set<Int> {
    if (this == null) return emptySet()
    return (0 until length()).mapNotNull { optInt(it).takeIf { value -> value > 0 } }.toSet()
}

private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
