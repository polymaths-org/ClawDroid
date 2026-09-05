package com.clawdroid.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clawdroid.app.core.AppContainer
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.data.db.MessageWithToolCalls
import com.clawdroid.app.data.db.ToolCallEntity
import com.clawdroid.app.ui.voice.OrbState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID

data class ChatUiState(
    val currentConversationId: String? = null,
    val displayItems: List<ChatItem> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingSteps: List<ActivityStepItem> = emptyList(),
    val streamingMessageId: String? = null,
    val runtimeState: AgentRuntimeState = AgentRuntimeState.Idle,
    val input: String = "",
    val isCallModeActive: Boolean = false,
    val isCallMuted: Boolean = false,
    val orbState: OrbState = OrbState.Idle,
    val userPartialText: String = "",
    val agentResponseText: String = "",
    val voiceSpeaking: Boolean = false,
    val currentAmplitude: Float = 0f,
    val piperDownloadProgress: Float = 0f,
    val showPermissionsDialog: Boolean = false,
    val isInitialized: Boolean = false,
)

class ChatViewModel(
    private val appContainer: AppContainer = AppContainer,
) : ViewModel() {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private val db get() = appContainer.db
    private val context get() = appContainer.context

    private var engine: AgentEngine? = null
    private var runJob: Job? = null
    private var voiceLoopJob: Job? = null
    private var wasInterrupted = false
    private var isListening = false
    private var listenTrigger = 0

    private val voiceManager: VoiceManager by lazy { appContainer.getVoiceManager() }
    private val voiceRecognizer: SpeechRecognizerClient by lazy { appContainer.getSpeechRecognizer() }

    fun initialize() {
        if (uiState.isInitialized) return
        viewModelScope.launch {
            val allConversations = db.conversations().observeConversations().first()
            val latest = allConversations.firstOrNull()
            if (latest != null) {
                setConversation(latest.id)
            } else {
                createNewConversation()
            }
            uiState = uiState.copy(isInitialized = true)
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        if (AppConfigManager.isOnboardingComplete
            && !AppConfigManager.permissionsAsked
            && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            uiState = uiState.copy(showPermissionsDialog = true)
            AppConfigManager.permissionsAsked = true
        }
    }

    fun dismissPermissionsDialog() {
        uiState = uiState.copy(showPermissionsDialog = false)
    }

    fun grantPermissions(
        requestPermissions: (Array<String>) -> Unit,
        requestOverlay: (Intent) -> Unit,
    ) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && !Settings.canDrawOverlays(context)
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            requestOverlay(intent)
        }
        uiState = uiState.copy(showPermissionsDialog = false)
    }

    fun setConversation(id: String) {
        uiState = uiState.copy(currentConversationId = id)
        viewModelScope.launch {
            val conv = db.conversations().getById(id)
            AppConfigManager.activeProjectId = conv?.projectId
        }
    }

    fun observeDbMessages(): Flow<List<MessageWithToolCalls>> {
        val id = uiState.currentConversationId ?: return flowOf(emptyList())
        return db.messages().observeMessagesWithToolCalls(id)
    }

    fun updateDisplayItems(dbMessages: List<MessageWithToolCalls>) {
        uiState = uiState.copy(displayItems = buildItems(dbMessages))
    }

    private fun buildItems(dbMessages: List<MessageWithToolCalls>): List<ChatItem> {
        val historical = dbMessages.flatMap { m ->
            val role = m.message.role
            val content = m.message.content
            val msgId = m.message.id
            if (role == "user") {
                listOf(UserChatItem(id = msgId, text = content))
            } else {
                val steps = m.toolCalls.map { t ->
                    ActivityStepItem(
                        id = t.id,
                        type = t.toolName.toViewModelActivityStepType(),
                        summary = t.toolName.toReadableToolName(),
                        detail = t.result ?: t.arguments,
                        arguments = t.arguments,
                        result = t.result,
                        running = t.status == "running"
                    )
                }
                listOfNotNull(
                    ActivityChatItem(
                        id = "${msgId}_activity",
                        steps = steps,
                        running = steps.any { it.running },
                    ).takeIf { steps.isNotEmpty() },
                    AgentChatItem(
                        id = msgId,
                        text = content,
                        streaming = false,
                    ),
                )
            }
        }

        val s = uiState
        return if (s.isStreaming) {
            historical +
                ActivityChatItem(
                    id = "${s.streamingMessageId ?: "streaming"}_activity",
                    steps = s.streamingSteps,
                    running = s.streamingSteps.any { it.running },
                ).takeIf { s.streamingSteps.isNotEmpty() }.let { listOfNotNull(it) } +
                AgentChatItem(
                    id = s.streamingMessageId ?: "streaming",
                    text = s.streamingText,
                    streaming = true,
                )
        } else {
            historical
        }
    }

    fun onInputChange(value: String) {
        uiState = uiState.copy(input = value)
    }

    fun submit() {
        val text = uiState.input.trim()
        if (text.isEmpty()) return
        uiState = uiState.copy(input = "")

        if (uiState.runtimeState == AgentRuntimeState.Running) {
            engine?.steer(text)
            viewModelScope.launch {
                val convId = uiState.currentConversationId ?: return@launch
                db.messages().insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = convId,
                        role = "user",
                        content = text,
                        createdAt = System.currentTimeMillis(),
                        tokenCount = 0,
                    )
                )
            }
            return
        }

        submitQuery(text)
    }

    private fun submitQuery(text: String) {
        val convId = uiState.currentConversationId ?: return

        viewModelScope.launch {
            db.messages().insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "user",
                    content = text,
                    createdAt = System.currentTimeMillis(),
                    tokenCount = 0,
                )
            )

            uiState = uiState.copy(
                streamingText = "",
                streamingSteps = emptyList(),
                streamingMessageId = UUID.randomUUID().toString(),
                isStreaming = true,
                runtimeState = AgentRuntimeState.Running,
                orbState = OrbState.Thinking,
            )

            val conv = db.conversations().getById(convId)
            if (conv?.title == "New Agent Chat") {
                db.conversations().update(
                    conv.copy(
                        title = text.take(30) + if (text.length > 30) "..." else "",
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            } else if (conv != null) {
                db.conversations().update(conv.copy(updatedAt = System.currentTimeMillis()))
            }

            val newEngine = appContainer.createAgentEngine(projectId = conv?.projectId)
            engine = newEngine

            if (uiState.isCallModeActive) {
                voiceManager.speakThinkingPhrase()
            }

            runJob = viewModelScope.launch {
                runCatching {
                    newEngine.run(text, maxTurns = AppConfigManager.maxAgentTurns).collect { event ->
                        when (event) {
                            is AgentRunEvent.TextDelta -> {
                                uiState = uiState.copy(
                                    streamingText = uiState.streamingText + event.text,
                                    agentResponseText = uiState.agentResponseText + event.text,
                                )
                            }

                            is AgentRunEvent.ToolCallRequested -> {
                                val newSteps = uiState.streamingSteps + ActivityStepItem(
                                    id = event.call.id,
                                    type = event.call.name.toViewModelActivityStepType(),
                                    summary = event.call.name.toReadableToolName(),
                                    detail = event.call.arguments,
                                    arguments = event.call.arguments,
                                    running = true,
                                )
                                uiState = uiState.copy(streamingSteps = newSteps)
                            }

                            is AgentRunEvent.ToolResultReceived -> {
                                val newSteps = uiState.streamingSteps.map { step ->
                                    if (step.id == event.result.callId) {
                                        step.copy(
                                            detail = event.result.content,
                                            result = event.result.content,
                                            running = false,
                                        )
                                    } else step
                                }
                                uiState = uiState.copy(streamingSteps = newSteps)
                            }

                            is AgentRunEvent.Completed -> {
                                finalizeStreaming(event.finalText)
                                processCommand(event.finalText)
                                if (uiState.isCallModeActive) {
                                    voiceManager.speakWithNaturalBreaks(event.finalText) {
                                        viewModelScope.launch {
                                            uiState = uiState.copy(
                                                userPartialText = "",
                                                agentResponseText = "",
                                            )
                                            listenTrigger++
                                        }
                                    }
                                } else {
                                    uiState = uiState.copy(userPartialText = "", agentResponseText = "")
                                }
                            }

                            is AgentRunEvent.Stopped -> {
                                val finalText = uiState.streamingText.ifBlank { "Stopped: ${event.reason}" }
                                finalizeStreaming(finalText)
                                listenTrigger++
                            }

                            else -> {}
                        }
                    }
                }.onFailure { error ->
                    val finalText = uiState.streamingText.ifBlank {
                        "Error: ${error.message ?: error::class.java.simpleName}"
                    }
                    finalizeStreaming(finalText)
                    listenTrigger++
                }
            }
        }
    }

    fun stopCurrentRun(reason: String = "Stopped") {
        engine?.stop()
        runJob?.cancel()
        val convId = uiState.currentConversationId
        if (convId != null) {
            viewModelScope.launch {
                finalizeStreaming(uiState.streamingText.ifBlank { reason })
            }
        }
        listenTrigger++
    }

    private suspend fun finalizeStreaming(finalText: String) {
        val msgId = uiState.streamingMessageId ?: UUID.randomUUID().toString()
        val convId = uiState.currentConversationId ?: return

        db.messages().insert(
            MessageEntity(
                id = msgId,
                conversationId = convId,
                role = "assistant",
                content = finalText,
                createdAt = System.currentTimeMillis(),
                tokenCount = 0,
            )
        )
        uiState.streamingSteps.forEach { step ->
            db.toolCalls().upsert(
                ToolCallEntity(
                    id = step.id,
                    messageId = msgId,
                    toolName = step.summary.lowercase().replace(" ", "_"),
                    arguments = step.detail,
                    result = step.detail,
                    status = "completed",
                    durationMs = 0L,
                )
            )
        }

        uiState = uiState.copy(
            isStreaming = false,
            streamingText = "",
            streamingSteps = emptyList(),
            streamingMessageId = null,
            runtimeState = AgentRuntimeState.Idle,
        )
    }

    private fun processCommand(text: String) {
        val lower = text.lowercase()
        try {
            when {
                lower.contains("call ") || lower.contains("dial ") -> {
                    val q = text.substringAfter("call", "")
                        .substringAfter("dial", "").trim().removeSuffix(".")
                    if (q.isNotEmpty()) {
                        Toast.makeText(context, "Calling $q...", Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${Uri.encode(q)}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                }
                lower.contains("alarm") || lower.contains("set alarm") -> {
                    Toast.makeText(context, "Opening Alarm...", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_MESSAGE, "ClawDroid Alarm")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
                lower.contains("remind") -> {
                    NotificationHelper.showNotification(context, "Reminder", text)
                }
                lower.contains("save note") || lower.contains("write down") -> {
                    NotificationHelper.showNotification(context, "Note Saved", text)
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Command: $text", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceSession(requestPermission: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            uiState = uiState.copy(isCallModeActive = true, isCallMuted = false)
            ServiceManager.start(context)
            startVoiceLoop()
        } else {
            requestPermission(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onVoicePermissionResult(granted: Boolean) {
        if (granted) {
            uiState = uiState.copy(isCallModeActive = true, isCallMuted = false)
            startVoiceLoop()
        }
    }

    private fun startVoiceLoop() {
        voiceLoopJob?.cancel()
        voiceLoopJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive && uiState.isCallModeActive) {
                uiState = uiState.copy(orbState = OrbState.Listening, userPartialText = "")
                val result = suspendCoroutine<String?> { cont ->
                    voiceRecognizer.startListening(
                        onResult = { text -> cont.resume(text) },
                        onError = { _ -> cont.resume(null) },
                    )
                }
                if (!currentCoroutineContext().isActive || !uiState.isCallModeActive) break
                if (result.isNullOrBlank()) continue
                uiState = uiState.copy(userPartialText = result, orbState = OrbState.Thinking)
                submitQuery(result)
                voiceManager.speakThinkingPhrase()
                runJob?.join()
            }
        }
    }

    fun stopVoiceSession() {
        voiceLoopJob?.cancel()
        voiceLoopJob = null
        voiceRecognizer.cancelListening()
        uiState = uiState.copy(isCallModeActive = false, orbState = OrbState.Idle, userPartialText = "")
        voiceManager.stop()
    }

    fun toggleMute() {
        uiState = uiState.copy(isCallMuted = !uiState.isCallMuted)
    }

    fun updatePiperProgress(progress: Float) {
        uiState = uiState.copy(piperDownloadProgress = progress)
    }

    fun createNewConversation(projectId: String? = null) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            db.conversations().upsert(
                ConversationEntity(
                    id = newId,
                    projectId = projectId,
                    title = "New Agent Chat",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    status = "idle",
                    costUsd = 0.0,
                )
            )
            val greeting = "Hello! I am ${AppConfigManager.agentName}, " +
                "your ${AppConfigManager.agentPersonality} assistant. " +
                "I'm ready to help you with ${AppConfigManager.agentPurpose}."
            db.messages().insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = newId,
                    role = "assistant",
                    content = greeting,
                    createdAt = System.currentTimeMillis(),
                    tokenCount = 0,
                )
            )
            setConversation(newId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        appContainer.releaseVoiceResources()
    }
}

class ChatViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel() as T
    }
}

private fun String.toViewModelActivityStepType(): ActivityStepType = when (this) {
    "read_file", "list_directory" -> ActivityStepType.File
    "write_file", "edit_file" -> ActivityStepType.Edit
    "browse_web", "web_search" -> ActivityStepType.Web
    "send_notification" -> ActivityStepType.Service
    "start_process", "check_process", "send_input", "kill_process", "list_processes", "execute_command" -> ActivityStepType.Command
    else -> ActivityStepType.Service
}

private fun String.toReadableToolName(): String = split('_')
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
