package com.clawdroid.app.core.assistant

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.assistant.overlay.AssistantOverlayCoordinator
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.engine.AgentRunManager
import com.clawdroid.app.data.api.ToolSchemaRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

object AssistantInvocationRouter {
    private const val TAG = "AssistantInvocationRouter"
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Assistant router coroutine crashed", throwable)
        AssistantOverlayCoordinator.showError("Assistant run crashed: ${throwable.message ?: throwable::class.java.simpleName}")
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    private var activeJob: Job? = null
    val activeConversationId = MutableStateFlow<String?>(null)
    val activeInvocationId = MutableStateFlow<String?>(null)

    fun present(context: Context, invocation: AssistantInvocation) {
        Log.i(TAG, "present id=${invocation.id} mode=${invocation.mode} source=${invocation.source} package=${invocation.contextSnapshot?.sourcePackage} screenshot=${invocation.contextSnapshot?.screenshotPath}")
        AssistantOverlayCoordinator.showOverlay(context, invocation)
    }

    fun submit(
        context: Context,
        invocation: AssistantInvocation,
        userText: String,
        mode: AssistantMode = invocation.mode,
    ) {
        Log.i(
            TAG,
            "submit id=${invocation.id} mode=$mode source=${invocation.source} " +
                "textLen=${userText.length} textPreview=${userText.take(80).replace('\n', ' ')}"
        )
        val submittedInvocation = invocation.copy(
            mode = mode,
            userText = userText.ifBlank { invocation.userText },
        )
        invoke(context, submittedInvocation)
    }

    fun invoke(context: Context, invocation: AssistantInvocation) {
        Log.i(
            TAG,
            "invoke id=${invocation.id} mode=${invocation.mode} source=${invocation.source} " +
                "conversation=${invocation.conversationId ?: "assistant_${invocation.id}"} package=${invocation.contextSnapshot?.sourcePackage} " +
                "media=${invocation.contextSnapshot?.screenshotPath ?: invocation.mediaPath}"
        )

        // Launch overlay UI
        AssistantOverlayCoordinator.showRunning(context, invocation)

        // Terminate active session if one exists
        activeConversationId.value?.let { previousConversationId ->
            Log.i(TAG, "stopping previous active run before id=${invocation.id} conversation=$previousConversationId")
            AgentRunManager.stopRun(previousConversationId)
            activeJob?.cancel()
        }

        val conversationId = invocation.conversationId ?: "assistant_${invocation.id}"
        activeConversationId.value = conversationId
        activeInvocationId.value = invocation.id

        val job = scope.launch {
            try {
                AgentRunManager.events
                    .filter { (eventConversationId, _) -> eventConversationId == conversationId }
                    .collect { (_, event) ->
                        handleRunEvent(invocation.id, event)
                        if (event is AgentRunEvent.Completed || event is AgentRunEvent.Stopped || event is AgentRunEvent.RunError) {
                            clearActive(invocation.id)
                        }
                    }
            } finally {
                Log.i(TAG, "AgentRunManager event bridge ended id=${invocation.id}")
            }
        }
        activeJob = job

        val visiblePrompt = invocation.userText?.takeIf { it.isNotBlank() }
            ?: "Wait for the user's instruction about this screen."
        val executionPrompt = if (AppConfigManager.promptEnhancementEnabled) {
            invocation.toSharedAgentPrompt()
        } else {
            visiblePrompt
        }

        AgentRunManager.startRun(
            context = context.applicationContext,
            conversationId = conversationId,
            prompt = executionPrompt,
            mediaPath = null,
            mediaMimeType = null,
            toolsOverride = ToolSchemaRegistry.overlayTools(),
            displayPrompt = visiblePrompt,
            hidePromptInChat = executionPrompt != visiblePrompt,
        )
    }

    fun stopActive(reason: String = "Stopped by user") {
        Log.i(TAG, "stopActive reason=$reason invocationId=${activeInvocationId.value} conversationId=${activeConversationId.value}")
        AgentRunManager.stopRun(activeConversationId.value)
        activeJob?.cancel()
        activeJob = null
        activeConversationId.value = null
        activeInvocationId.value = null
        AssistantOverlayCoordinator.updateStatus("Stopped")
        AssistantOverlayCoordinator.showAnswer(reason)
    }

    fun stopIfActiveConversation(conversationId: String?): Boolean {
        if (conversationId == null || activeConversationId.value != conversationId) return false
        stopActive()
        return true
    }

    private fun handleRunEvent(invocationId: String, event: AgentRunEvent) {
        when (event) {
            is AgentRunEvent.TextDelta -> {
                Log.d(TAG, "event TextDelta id=$invocationId len=${event.text.length} preview=${event.text.take(80).replace('\n', ' ')}")
                AssistantOverlayCoordinator.updateText(event.text)
            }
            is AgentRunEvent.ToolCallRequested -> {
                Log.i(TAG, "event ToolCallRequested id=$invocationId tool=${event.call.name} callId=${event.call.id}")
                AssistantOverlayCoordinator.updateStatus(overlayStatusForTool(event.call.name))
                AssistantOverlayCoordinator.recordAction(overlayActionForTool(event.call.name, event.call.arguments))
            }
            is AgentRunEvent.ToolCallStreaming -> {
                Log.d(TAG, "event ToolCallStreaming id=$invocationId tool=${event.name} callId=${event.callId} argsLen=${event.arguments.length}")
                AssistantOverlayCoordinator.updateStatus(overlayStatusForTool(event.name))
            }
            is AgentRunEvent.ToolOutputUpdated -> {
                Log.d(TAG, "event ToolOutputUpdated id=$invocationId callId=${event.callId} len=${event.output.length}")
                AssistantOverlayCoordinator.updateProgress(event.output)
            }
            is AgentRunEvent.ToolResultReceived -> {
                Log.i(TAG, "event ToolResultReceived id=$invocationId callId=${event.result.callId} isError=${event.result.isError} len=${event.result.content.length}")
                AssistantOverlayCoordinator.updateStatus("Thinking...")
                AssistantOverlayCoordinator.recordAction(if (event.result.isError) "Tool returned an error" else "Tool finished")
            }
            is AgentRunEvent.SteeringApplied -> {
                Log.i(TAG, "event SteeringApplied id=$invocationId len=${event.message.length}")
            }
            is AgentRunEvent.LoopWarning -> {
                Log.w(TAG, "event LoopWarning id=$invocationId ${event.message}")
            }
            is AgentRunEvent.Completed -> {
                Log.i(TAG, "event Completed id=$invocationId len=${event.finalText.length}")
                AssistantOverlayCoordinator.showAnswer(event.finalText)
            }
            is AgentRunEvent.Stopped -> {
                Log.w(TAG, "event Stopped id=$invocationId reason=${event.reason}")
                AssistantOverlayCoordinator.updateStatus("Stopped: ${event.reason}")
            }
            is AgentRunEvent.RunError -> {
                Log.e(TAG, "event RunError id=$invocationId message=${event.message}")
                AssistantOverlayCoordinator.showError(event.message)
            }
        }
    }

    private fun clearActive(invocationId: String) {
        if (activeInvocationId.value == invocationId) {
            activeJob?.cancel()
            activeJob = null
            activeConversationId.value = null
            activeInvocationId.value = null
        }
    }

    private fun overlayStatusForTool(name: String): String {
        return when (name) {
            "get_screen", "screenshot" -> "Capturing screen"
            "open_app", "launch_app" -> "Opening app"
            "tap", "tap_text", "tap_resource_id" -> "Clicking"
            "swipe", "scroll" -> "Scrolling"
            "type_text", "clear_text" -> "Typing"
            "press_key", "press_back", "press_home", "press_recents" -> "Pressing key"
            "perform_android_actions" -> "Acting on screen"
            "send_message_in_current_chat" -> "Sending message"
            "wait" -> "Waiting for UI"
            "execute_command", "start_process" -> "Running command"
            "read_file", "write_file", "edit_file" -> "Updating files"
            else -> "Working"
        }
    }

    private fun overlayActionForTool(name: String, arguments: String): String {
        return when (name) {
            "get_screen" -> "Capture screen tree"
            "screenshot" -> "Capture screenshot"
            "open_app", "launch_app" -> {
                val pkg = runCatching { org.json.JSONObject(arguments).optString("package_name") }.getOrNull().orEmpty()
                if (pkg.isBlank()) "Open app" else "Open $pkg"
            }
            "tap" -> "Click screen coordinates"
            "tap_text" -> {
                val label = runCatching { org.json.JSONObject(arguments).optString("label") }.getOrNull().orEmpty()
                if (label.isBlank()) "Click text" else "Click \"$label\""
            }
            "tap_resource_id" -> "Click UI element"
            "swipe" -> "Swipe screen"
            "scroll" -> "Scroll screen"
            "type_text" -> "Type into field"
            "clear_text" -> "Clear field"
            "press_back" -> "Press Back"
            "press_home" -> "Press Home"
            "press_recents" -> "Open Recents"
            "perform_android_actions" -> {
                val count = runCatching {
                    org.json.JSONObject(arguments).optJSONArray("actions")?.length() ?: 0
                }.getOrDefault(0)
                if (count > 0) "Run $count screen actions" else "Run screen actions"
            }
            "send_message_in_current_chat" -> "Send chat message"
            "wait" -> "Wait for UI"
            "execute_command" -> "Run Linux command"
            "start_process" -> "Start process"
            "read_file" -> "Read file"
            "write_file" -> "Write file"
            "edit_file" -> "Edit file"
            else -> name.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        }
    }

    private fun AssistantInvocation.toSharedAgentPrompt(): String {
        val userInstruction = userText?.takeIf { it.isNotBlank() } ?: "Wait for the user's instruction about this screen."
        val packageHint = contextSnapshot?.sourcePackage ?: "unknown"
        val activityHint = contextSnapshot?.sourceActivity ?: "unknown"
        return buildString {
            appendLine("You were opened as an Android assistant overlay on top of the user's current app.")
            appendLine("Use the same Android control tools and workflow you normally use in the ClawDroid app.")
            appendLine("The floating ClawDroid widget stays visible while you work. It is your status UI, not the target app UI.")
            appendLine("If the widget appears in screen captures, ignore it and do not tap it unless the user asks.")
            appendLine("Current app package: $packageHint")
            appendLine("Current app activity: $activityHint")
            appendLine("If the user asks to send a specific message in the current chat, use send_message_in_current_chat first.")
            appendLine()
            append(userInstruction)
        }
    }
}
