package com.clawdroid.app.core.selfmanage

import android.content.Context
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.SelfManageAlarmEntity
import com.clawdroid.app.data.db.SelfManageReminderEntity
import com.clawdroid.app.data.db.SelfManageTodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long? = null,
)

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val dueAt: Long,
    val priority: Int = 5,
    val completed: Boolean = false,
    val recurring: Boolean = false,
    val intervalMinutes: Int? = null,
    val category: String = "general",
    val createdBy: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
)

data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val dueAt: Long? = null,
    val completed: Boolean = false,
    val priority: Int = 5,
    val tags: List<String> = emptyList(),
    val createdBy: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

class SelfManageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = ClawDroidDatabase.get(appContext).selfManage()

    fun getAllAlarms(): Flow<List<Alarm>> = dao.observeAlarms().map { list -> list.map { it.toModel() } }
    fun getAllReminders(): Flow<List<Reminder>> = dao.observeReminders().map { list -> list.map { it.toModel() } }
    fun getAllTodos(): Flow<List<Todo>> = dao.observeTodos().map { list -> list.map { it.toModel() } }

    suspend fun getDueReminders(now: Long): List<Reminder> = dao.getDueReminders(now).map { it.toModel() }
    suspend fun getOverdueTodos(now: Long): List<Todo> = dao.getOverdueTodos(now).map { it.toModel() }
    suspend fun getNextAlarm(): Alarm? = dao.getNextEnabledAlarm()?.toModel()

    suspend fun rescheduleActive() {
        val now = System.currentTimeMillis()
        dao.getAllEnabledAlarms().map { it.toModel() }.forEach { SelfManageScheduler.scheduleAlarm(appContext, it) }
        dao.getFutureReminders(now).map { it.toModel() }.forEach { SelfManageScheduler.scheduleReminder(appContext, it) }
        dao.getFutureTodos(now).map { it.toModel() }.forEach { SelfManageScheduler.scheduleTodo(appContext, it) }
    }

    suspend fun addAlarm(alarm: Alarm) {
        dao.upsertAlarm(alarm.toEntity())
        SelfManageScheduler.scheduleAlarm(appContext, alarm)
    }

    suspend fun updateAlarm(alarm: Alarm) {
        dao.upsertAlarm(alarm.toEntity())
        SelfManageScheduler.scheduleAlarm(appContext, alarm)
    }

    suspend fun deleteAlarm(alarmId: String) {
        SelfManageScheduler.cancel(appContext, alarmId)
        dao.deleteAlarm(alarmId)
    }

    suspend fun addReminder(reminder: Reminder) {
        dao.upsertReminder(reminder.toEntity())
        SelfManageScheduler.scheduleReminder(appContext, reminder)
    }

    suspend fun updateReminder(reminder: Reminder) {
        dao.upsertReminder(reminder.toEntity())
        SelfManageScheduler.scheduleReminder(appContext, reminder)
    }

    suspend fun completeReminder(reminderId: String) {
        SelfManageScheduler.cancel(appContext, reminderId)
        dao.completeReminder(reminderId)
    }

    suspend fun deleteReminder(reminderId: String) {
        SelfManageScheduler.cancel(appContext, reminderId)
        dao.deleteReminder(reminderId)
    }

    suspend fun addTodo(todo: Todo) {
        dao.upsertTodo(todo.toEntity())
        SelfManageScheduler.scheduleTodo(appContext, todo)
    }

    suspend fun updateTodo(todo: Todo) {
        dao.upsertTodo(todo.toEntity())
        SelfManageScheduler.scheduleTodo(appContext, todo)
    }

    suspend fun completeTodo(todoId: String) {
        SelfManageScheduler.cancel(appContext, todoId)
        dao.completeTodo(todoId, System.currentTimeMillis())
    }

    suspend fun deleteTodo(todoId: String) {
        SelfManageScheduler.cancel(appContext, todoId)
        dao.deleteTodo(todoId)
    }
}

private fun SelfManageAlarmEntity.toModel(): Alarm = Alarm(
    id = id,
    label = label,
    hour = hour,
    minute = minute,
    daysOfWeek = daysOfWeekCsv.csvInts(),
    enabled = enabled,
    createdAt = createdAt,
    lastTriggered = lastTriggered,
)

private fun SelfManageReminderEntity.toModel(): Reminder = Reminder(
    id = id,
    title = title,
    description = description,
    dueAt = dueAt,
    priority = priority,
    completed = completed,
    recurring = recurring,
    intervalMinutes = intervalMinutes,
    category = category,
    createdBy = createdBy,
    createdAt = createdAt,
)

private fun SelfManageTodoEntity.toModel(): Todo = Todo(
    id = id,
    title = title,
    description = description,
    dueAt = dueAt,
    completed = completed,
    priority = priority,
    tags = tagsCsv.csvStrings(),
    createdBy = createdBy,
    createdAt = createdAt,
    completedAt = completedAt,
)

private fun Alarm.toEntity(): SelfManageAlarmEntity = SelfManageAlarmEntity(
    id = id,
    label = label,
    hour = hour.coerceIn(0, 23),
    minute = minute.coerceIn(0, 59),
    daysOfWeekCsv = daysOfWeek.sorted().joinToString(","),
    enabled = enabled,
    createdAt = createdAt,
    lastTriggered = lastTriggered,
)

private fun Reminder.toEntity(): SelfManageReminderEntity = SelfManageReminderEntity(
    id = id,
    title = title,
    description = description,
    dueAt = dueAt,
    priority = priority.coerceIn(1, 10),
    completed = completed,
    recurring = recurring,
    intervalMinutes = intervalMinutes,
    category = category,
    createdBy = createdBy,
    createdAt = createdAt,
)

private fun Todo.toEntity(): SelfManageTodoEntity = SelfManageTodoEntity(
    id = id,
    title = title,
    description = description,
    dueAt = dueAt,
    completed = completed,
    priority = priority.coerceIn(1, 10),
    tagsCsv = tags.joinToString(","),
    createdBy = createdBy,
    createdAt = createdAt,
    completedAt = completedAt,
)

private fun String.csvInts(): Set<Int> = split(',')
    .mapNotNull { it.trim().toIntOrNull() }
    .toSet()

private fun String.csvStrings(): List<String> = split(',')
    .map { it.trim() }
    .filter { it.isNotBlank() }
