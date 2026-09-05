package com.clawdroid.app.core.engine

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.ui.chat.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class ConversationRunState(
    val conversationId: String,
    val engine: AgentEngine,
    val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val agentResponseText: MutableStateFlow<String> = MutableStateFlow(""),
    val runningAgentMessageId: MutableStateFlow<String?> = MutableStateFlow(null),
    val runningActivityId: MutableStateFlow<String?> = MutableStateFlow(null),
    val activeChatItems: MutableStateFlow<List<ChatItem>> = MutableStateFlow(emptyList())
) {
    lateinit var job: Job
}

object AgentRunManager {
    private const val TAG = "AgentRunManager"
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    val activeRuns = MutableStateFlow<Map<String, ConversationRunState>>(emptyMap())
    private var appContext: Context? = null

    val events = MutableSharedFlow<Pair<String, AgentRunEvent>>(extraBufferCapacity = 64)

    fun getActiveEngine(conversationId: String?): AgentEngine? {
        if (conversationId == null) return null
        return synchronized(activeRuns) {
            activeRuns.value[conversationId]?.engine
        }
    }

    fun setAgentResponseText(conversationId: String?, text: String) {
        if (conversationId == null) return
        synchronized(activeRuns) {
            activeRuns.value[conversationId]?.agentResponseText?.value = text
        }
    }
    
    fun startRun(
        context: Context,
        conversationId: String,
        prompt: String,
        mediaPath: String? = null,
        mediaMimeType: String? = null,
    ) {
        synchronized(activeRuns) {
            if (activeRuns.value.containsKey(conversationId)) {
                Log.w(TAG, "An agent run is already active for conversation $conversationId")
                return
            }
        }
        
        val appCtx = context.applicationContext
        appContext = appCtx
        
        if (!AppConfigManager.ultraAgentEnabled) {
            ServiceManager.start(appCtx)
        }
        
        val engine = AgentEngine(appCtx, projectId = AppConfigManager.activeProjectId)
        val initialMessage = AgentChatItem(text = "", streaming = true)
        
        val runState = ConversationRunState(
            conversationId = conversationId,
            engine = engine
        )
        runState.runningAgentMessageId.value = initialMessage.id
        runState.activeChatItems.value = listOf(initialMessage)
        
        synchronized(activeRuns) {
            activeRuns.value = activeRuns.value + (conversationId to runState)
        }
        
        val job = appScope.launch {
            try {
                engine.run(
                    prompt = prompt,
                    targetConversationId = conversationId,
                    maxTurns = AppConfigManager.maxAgentTurns,
                    mediaPath = mediaPath,
                    mediaMimeType = mediaMimeType
                )
                    .collect { event ->
                        handleEvent(conversationId, event)
                        events.emit(Pair(conversationId, event))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Background run failed for $conversationId", e)
                handleFailure(conversationId, e.message ?: "Run failed")
                events.emit(Pair(conversationId, AgentRunEvent.RunError(e.message ?: "Run failed")))
            } finally {
                saveUnsavedStateToDb(appCtx, runState)
                runState.isRunning.value = false
                synchronized(activeRuns) {
                    activeRuns.value = activeRuns.value - conversationId
                }
                if (!AppConfigManager.ultraAgentEnabled && synchronized(activeRuns) { activeRuns.value.isEmpty() }) {
                    ServiceManager.stop(appCtx)
                }
            }
        }
        runState.job = job
    }
    
    fun stopRun(conversationId: String?) {
        if (conversationId == null) return
        val runState = synchronized(activeRuns) {
            val state = activeRuns.value[conversationId]
            if (state != null) {
                activeRuns.value = activeRuns.value - conversationId
            }
            state
        }
        runState?.let { state ->
            state.engine.stop()
            state.job.cancel()
            state.isRunning.value = false
            appContext?.let { ctx ->
                if (!AppConfigManager.ultraAgentEnabled && synchronized(activeRuns) { activeRuns.value.isEmpty() }) {
                    ServiceManager.stop(ctx)
                }
            }
        }
    }

    private suspend fun saveUnsavedStateToDb(context: Context, runState: ConversationRunState) {
        val msgId = runState.runningAgentMessageId.value ?: return
        val text = runState.agentResponseText.value
        val conversationId = runState.conversationId
        if (text.isBlank()) return
        
        try {
            val db = ClawDroidDatabase.get(context)
            val existing = db.messages().getById(msgId)
            if (existing == null) {
                db.messages().insert(
                    MessageEntity(
                        id = msgId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = text,
                        createdAt = System.currentTimeMillis(),
                        tokenCount = 0
                    )
                )
            } else if (existing.content != text) {
                db.messages().update(existing.copy(content = text))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save unsaved state to DB for $conversationId", e)
        }
    }

    private fun handleEvent(conversationId: String, event: AgentRunEvent) {
        val runState = synchronized(activeRuns) { activeRuns.value[conversationId] } ?: return
        val currentItems = runState.activeChatItems.value.toMutableList()
        when (event) {
            is AgentRunEvent.TextDelta -> {
                finishCurrentActivity(runState, currentItems)
                val messageId = ensureAgentMessage(runState, currentItems)
                currentItems.replaceAgentMessage(messageId) { current ->
                    current.copy(text = current.text + event.text, streaming = true)
                }
                runState.agentResponseText.value += event.text
            }

            is AgentRunEvent.ToolCallStreaming -> {
                finishCurrentAgentText(runState, currentItems)
                val args = event.arguments
                val summary = getToolSummary(event.name, args)
                val step = ActivityStepItem(
                    id = event.callId,
                    callId = event.callId,
                    type = event.name.toActivityStepType(),
                    summary = summary,
                    detail = event.arguments,
                    result = "",
                    arguments = event.arguments,
                    running = true,
                )
                val activityId = runState.runningActivityId.value
                if (activityId == null) {
                    val activity = ActivityChatItem(steps = listOf(step), running = true)
                    currentItems.add(activity)
                    runState.runningActivityId.value = activity.id
                } else {
                    currentItems.replaceActivityItem(activityId) { current ->
                        val idx = current.steps.indexOfFirst { it.callId == event.callId }
                        val updatedSteps = if (idx >= 0) {
                            current.steps.mapIndexed { i, s -> if (i == idx) step else s }
                        } else {
                            current.steps + step
                        }
                        current.copy(running = true, steps = updatedSteps)
                    }
                }
            }

            is AgentRunEvent.ToolCallRequested -> {
                finishCurrentAgentText(runState, currentItems)
                val args = event.call.arguments
                val summary = getToolSummary(event.call.name, args)
                val step = ActivityStepItem(
                    id = event.call.id,
                    callId = event.call.id,
                    type = event.call.name.toActivityStepType(),
                    summary = summary,
                    detail = event.call.arguments,
                    result = "",
                    arguments = event.call.arguments,
                    running = true,
                )
                val activityId = runState.runningActivityId.value
                if (activityId == null) {
                    val activity = ActivityChatItem(steps = listOf(step), running = true)
                    currentItems.add(activity)
                    runState.runningActivityId.value = activity.id
                } else {
                    currentItems.replaceActivityItem(activityId) { current ->
                        val idx = current.steps.indexOfFirst { it.callId == event.call.id }
                        val updatedSteps = if (idx >= 0) {
                            current.steps.mapIndexed { i, s -> if (i == idx) step else s }
                        } else {
                            current.steps + step
                        }
                        current.copy(running = true, steps = updatedSteps)
                    }
                }
            }

            is AgentRunEvent.ToolOutputUpdated -> {
                runState.runningActivityId.value?.let { id ->
                    currentItems.replaceActivityItem(id) { current ->
                        current.copy(
                            steps = current.steps.map { step ->
                                if (step.callId == event.callId) {
                                    val mockResult = JSONObject().put("output", event.output).toString()
                                    step.copy(result = mockResult)
                                } else {
                                    step
                                }
                            }
                        )
                    }
                }
            }

            is AgentRunEvent.ToolResultReceived -> {
                val isError = event.result.isError || runCatching {
                    val obj = JSONObject(event.result.content)
                    obj.optInt("exit_code", 0) != 0 || obj.has("error")
                }.getOrDefault(false)

                runState.runningActivityId.value?.let { id ->
                    currentItems.replaceActivityItem(id) { current ->
                        current.copy(running = true, steps = current.steps.markLastComplete(event.result.content, isError = isError))
                    }
                }
            }

            is AgentRunEvent.SteeringApplied -> {
                val activity = ActivityChatItem(
                    steps = listOf(
                        ActivityStepItem(
                            type = ActivityStepType.Service,
                            summary = "Applied steering",
                            detail = event.message,
                        )
                    ),
                    running = false,
                )
                currentItems.add(activity)
            }

            is AgentRunEvent.LoopWarning -> {
                val activity = ActivityChatItem(
                    steps = listOf(
                        ActivityStepItem(
                            type = ActivityStepType.Service,
                            summary = "Loop warning",
                            detail = event.message,
                        )
                    ),
                    running = false,
                )
                currentItems.add(activity)
            }

            is AgentRunEvent.Completed -> {
                runState.runningAgentMessageId.value?.let { id ->
                    currentItems.replaceAgentMessage(id) { current ->
                        current.copy(text = current.text.ifBlank { event.finalText }, streaming = false)
                    }
                }
                finishCurrentActivity(runState, currentItems)
                runState.runningAgentMessageId.value = null
                runState.runningActivityId.value = null
            }

            is AgentRunEvent.Stopped -> {
                runState.runningAgentMessageId.value?.let { id ->
                    currentItems.replaceAgentMessage(id) { current ->
                        current.copy(text = current.text.ifBlank { "Stopped: ${event.reason}" }, streaming = false)
                    }
                }
                runState.runningActivityId.value?.let { id ->
                    currentItems.replaceActivityItem(id) { current ->
                        current.copy(running = false, steps = current.steps.markLastComplete(event.reason))
                    }
                }
                runState.runningAgentMessageId.value = null
                runState.runningActivityId.value = null
            }

            is AgentRunEvent.RunError -> {
                runState.runningAgentMessageId.value?.let { id ->
                    currentItems.replaceAgentMessage(id) { current ->
                        current.copy(text = current.text.ifBlank { "Error: ${event.message}" }, streaming = false)
                    }
                }
                runState.runningActivityId.value?.let { id ->
                    currentItems.replaceActivityItem(id) { current ->
                        current.copy(running = false, steps = current.steps.markLastComplete(event.message, isError = true))
                    }
                }
                runState.runningAgentMessageId.value = null
                runState.runningActivityId.value = null
            }
        }
        runState.activeChatItems.value = currentItems
    }

    private fun handleFailure(conversationId: String, errorMsg: String) {
        val runState = synchronized(activeRuns) { activeRuns.value[conversationId] } ?: return
        val currentItems = runState.activeChatItems.value.toMutableList()
        val messageId = runState.runningAgentMessageId.value
        if (messageId != null) {
            currentItems.replaceAgentMessage(messageId) { current ->
                current.copy(
                    text = current.text.ifBlank { "Error: $errorMsg" },
                    streaming = false,
                )
            }
        }
        runState.runningActivityId.value?.let { id ->
            currentItems.replaceActivityItem(id) { current ->
                current.copy(running = false, steps = current.steps.markLastComplete(errorMsg, isError = true))
            }
        }
        runState.runningAgentMessageId.value = null
        runState.runningActivityId.value = null
        runState.activeChatItems.value = currentItems
    }

    private fun ensureAgentMessage(runState: ConversationRunState, items: MutableList<ChatItem>): String {
        val existingId = runState.runningAgentMessageId.value
        if (existingId != null) return existingId
        val message = AgentChatItem(text = "", streaming = true)
        items.add(message)
        runState.runningAgentMessageId.value = message.id
        return message.id
    }

    private fun finishCurrentAgentText(runState: ConversationRunState, items: MutableList<ChatItem>) {
        runState.runningAgentMessageId.value?.let { id ->
            items.replaceAgentMessage(id) { it.copy(streaming = false) }
        }
        runState.runningAgentMessageId.value = null
    }

    private fun finishCurrentActivity(runState: ConversationRunState, items: MutableList<ChatItem>) {
        runState.runningActivityId.value?.let { id ->
            items.replaceActivityItem(id) { it.copy(running = false, steps = it.steps.markAllComplete()) }
        }
        runState.runningActivityId.value = null
    }

    private fun getToolSummary(name: String, args: String): String {
        return when (name) {
            "write_file" -> {
                val content = runCatching { JSONObject(args).optString("content") }.getOrNull()
                    ?: extractJsonField(args, "content").orEmpty()
                val lineCount = content.lines().size
                "Write File (+$lineCount lines)"
            }
            "edit_file" -> {
                val search = runCatching { JSONObject(args).optString("search") }.getOrNull()
                    ?: extractJsonField(args, "search").orEmpty()
                val replace = runCatching { JSONObject(args).optString("replace") }.getOrNull()
                    ?: extractJsonField(args, "replace").orEmpty()
                val searchLines = search.lines().size
                val replaceLines = replace.lines().size
                "Edit File (-$searchLines lines, +$replaceLines lines)"
            }
            else -> name.readableToolName()
        }
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"".toRegex()
        val match = pattern.find(json) ?: return null
        val start = match.range.last + 1
        
        val sb = StringBuilder()
        var escaped = false
        var i = start
        while (i < json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    else -> {
                        sb.append('\\')
                        sb.append(c)
                    }
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun MutableList<ChatItem>.replaceAgentMessage(id: String, transform: (AgentChatItem) -> AgentChatItem) {
        val index = indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = transform(this[index] as AgentChatItem)
            if (updated.text.isBlank() && !updated.streaming) {
                removeAt(index)
            } else {
                this[index] = updated
            }
        }
    }

    private fun MutableList<ChatItem>.replaceActivityItem(id: String, transform: (ActivityChatItem) -> ActivityChatItem) {
        val index = indexOfFirst { it.id == id }
        if (index >= 0) this[index] = transform(this[index] as ActivityChatItem)
    }

    private fun List<ActivityStepItem>.markLastComplete(detail: String, isError: Boolean = false): List<ActivityStepItem> {
        if (isEmpty()) return this
        val last = last()
        return dropLast(1) + last.copy(
            detail = detail,
            result = detail,
            running = false,
            isError = isError
        )
    }

    private fun List<ActivityStepItem>.markAllComplete(): List<ActivityStepItem> = map { it.copy(running = false) }

    private fun String.toActivityStepType(): ActivityStepType = when (this) {
        "read_file", "list_directory" -> ActivityStepType.File
        "write_file", "edit_file" -> ActivityStepType.Edit
        "browse_web", "web_search" -> ActivityStepType.Web
        "send_notification" -> ActivityStepType.Service
        "start_process", "check_process", "send_input", "kill_process", "list_processes", "execute_command" -> ActivityStepType.Command
        else -> ActivityStepType.Service
    }

    private fun String.readableToolName(): String = split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}
