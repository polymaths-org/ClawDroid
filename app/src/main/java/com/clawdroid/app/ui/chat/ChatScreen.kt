package com.clawdroid.app.ui.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.data.db.ToolCallEntity
import com.clawdroid.app.ui.components.BlueGradientHorizontal
import com.clawdroid.app.ui.components.CustomProcessingLoader
import com.clawdroid.app.ui.components.PiperDownloadDialog
import com.clawdroid.app.ui.markdown.MarkdownText
import com.clawdroid.app.ui.sidebar.SidebarContent
import com.clawdroid.app.ui.theme.CardDark
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillMedium
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import com.clawdroid.app.ui.voice.OrbState
import com.clawdroid.app.ui.voice.VoiceOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    startVoiceTrigger: Boolean = false,
    onVoiceTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val clipboardManager = LocalClipboardManager.current

    // Database Setup
    val db = remember { ClawDroidDatabase.get(context) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }

    // Pick latest conversation on start
    val allConversations by db.conversations().observeConversations().collectAsState(initial = null)
    LaunchedEffect(allConversations) {
        if (currentConversationId == null && allConversations != null) {
            val latest = allConversations?.firstOrNull()
            if (latest != null) {
                currentConversationId = latest.id
            } else {
                val newId = UUID.randomUUID().toString()
                db.conversations().upsert(
                    ConversationEntity(
                        id = newId,
                        projectId = null,
                        title = "New Agent Chat",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        status = "idle",
                        costUsd = 0.0
                    )
                )
                val greeting = "Hello! I am ${AppConfigManager.agentName}, your ${AppConfigManager.agentPersonality} assistant. I'm ready to help you with ${AppConfigManager.agentPurpose}."
                db.messages().insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = newId,
                        role = "assistant",
                        content = greeting,
                        createdAt = System.currentTimeMillis(),
                        tokenCount = 0
                    )
                )
                currentConversationId = newId
            }
        }
    }

    LaunchedEffect(currentConversationId) {
        currentConversationId?.let { convId ->
            val conv = db.conversations().getById(convId)
            AppConfigManager.activeProjectId = conv?.projectId
        }
    }

    // Chronological Visual Items
    val items = remember { mutableStateListOf<ChatItem>() }
    LaunchedEffect(currentConversationId) {
        val convId = currentConversationId ?: return@LaunchedEffect
        items.clear()
        val messages = db.messages().getAll(convId)
        for (msg in messages) {
            if (msg.role == "user") {
                if (!msg.content.startsWith("Previous conversation summary:")) {
                    items += UserChatItem(id = msg.id, text = msg.content)
                }
            } else if (msg.role == "assistant") {
                if (!msg.content.startsWith("[Compacted Summary]")) {
                    val toolCalls = db.toolCalls().getForMessage(msg.id)
                    if (toolCalls.isNotEmpty()) {
                        val steps = toolCalls.map { t ->
                            ActivityStepItem(
                                id = t.id,
                                callId = t.id,
                                type = t.toolName.toActivityStepType(),
                                summary = t.toolName.readableToolName(),
                                detail = t.result ?: t.arguments,
                                result = t.result ?: "",
                                arguments = t.arguments,
                                running = t.status == "running",
                                isError = t.status == "failed" || runCatching {
                                    val obj = JSONObject(t.result ?: "")
                                    obj.optInt("exit_code", 0) != 0 || obj.has("error")
                                }.getOrDefault(false)
                            )
                        }
                        items += ActivityChatItem(steps = steps, running = steps.any { it.running })
                    }
                    items += AgentChatItem(id = msg.id, text = msg.content, streaming = false)
                }
            }
        }
    }

    var input by remember { mutableStateOf("") }
    var runtimeState by remember { mutableStateOf(AgentRuntimeState.Idle) }
    var engine by remember { mutableStateOf<AgentEngine?>(null) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var runningAgentMessageId by remember { mutableStateOf<String?>(null) }
    var runningActivityId by remember { mutableStateOf<String?>(null) }

    // Voice & Call states
    val voiceManager = remember { VoiceManager(context.applicationContext) }
    val voiceRecognizer = remember { SpeechRecognizerClient(context.applicationContext) }
    var isCallModeActive by remember { mutableStateOf(false) }
    var isCallMuted by remember { mutableStateOf(false) }
    var orbState by remember { mutableStateOf(OrbState.Idle) }

    // Real-time transcript components
    var userPartialText by remember { mutableStateOf("") }
    var agentResponseText by remember { mutableStateOf("") }
    var listenTrigger by remember { mutableStateOf(0) }

    val voiceSpeaking by voiceManager.isSpeaking.collectAsState()
    val partialSpeech by voiceRecognizer.partialResult.collectAsState()
    val piperDownloadProgress by voiceManager.downloadProgress.collectAsState()

    // Permissions
    var showPermissionsDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (AppConfigManager.isOnboardingComplete
            && !AppConfigManager.permissionsAsked
            && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            showPermissionsDialog = true
            AppConfigManager.permissionsAsked = true
        }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}

    // Real-time Voice Amplitudes
    val userAmplitude by voiceRecognizer.userVoiceAmplitude.collectAsState()
    val agentAmplitude by voiceManager.agentVoiceAmplitude.collectAsState()
    val currentAmplitude = when (orbState) {
        OrbState.Listening -> userAmplitude
        OrbState.Speaking -> agentAmplitude
        else -> 0f
    }

    // Dynamically feed partial speech results into transcript
    LaunchedEffect(partialSpeech) {
        if (isCallModeActive && partialSpeech.isNotBlank()) {
            userPartialText = partialSpeech
        }
    }

    fun showSystemNotification(title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clawdroid_agent_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ClawDroid Agent Actions", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        try {
            val resId = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
            if (resId != 0) {
                builder.setSmallIcon(resId)
            }
        } catch (e: Exception) {}

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    fun processSimulatedSystemCommand(text: String) {
        val lower = text.lowercase()
        try {
            if (lower.contains("call ") || lower.contains("dial ")) {
                val query = text.substringAfter("call", "").substringAfter("dial", "").trim().removeSuffix(".")
                if (query.isNotEmpty()) {
                    Toast.makeText(context, "Initiating call to $query...", Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${Uri.encode(query)}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } else if (lower.contains("alarm for") || lower.contains("set alarm")) {
                Toast.makeText(context, "Opening System Alarm Clock...", Toast.LENGTH_LONG).show()
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "ClawDroid Agent Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else if (lower.contains("remind me") || lower.contains("reminder")) {
                showSystemNotification("ClawDroid Reminder", text)
            } else if (lower.contains("save note") || lower.contains("take a note") || lower.contains("write down")) {
                showSystemNotification("ClawDroid Note Saved", text)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Command simulated: $text", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureAgentMessage(): String {
        val existingId = runningAgentMessageId
        if (existingId != null) return existingId
        val message = AgentChatItem(text = "", streaming = true)
        items += message
        runningAgentMessageId = message.id
        return message.id
    }

    fun finishCurrentAgentText() {
        runningAgentMessageId?.let { id ->
            items.replaceAgentMessage(id) { it.copy(streaming = false) }
        }
        runningAgentMessageId = null
    }

    fun finishCurrentActivity() {
        runningActivityId?.let { id ->
            items.replaceActivityItem(id) { it.copy(running = false, steps = it.steps.markAllComplete()) }
        }
        runningActivityId = null
    }

    fun stopCurrentRun(reason: String = "Stopped") {
        engine?.stop()
        runJob?.cancel()
        runningAgentMessageId?.let { id ->
            items.replaceAgentMessage(id) { current ->
                current.copy(text = current.text.ifBlank { reason }, streaming = false)
            }
        }
        runningActivityId?.let { id ->
            items.replaceActivityItem(id) { current ->
                current.copy(running = false, steps = current.steps.markLastComplete(reason))
            }
        }
        runtimeState = AgentRuntimeState.Idle
        runningAgentMessageId = null
        runningActivityId = null
        listenTrigger++
    }

    fun submitQuery(text: String) {
        val convId = currentConversationId ?: return

        val userMsgId = UUID.randomUUID().toString()
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    id = userMsgId,
                    conversationId = convId,
                    role = "user",
                    content = text,
                    createdAt = System.currentTimeMillis(),
                    tokenCount = 0
                )
            )
            val conv = db.conversations().getById(convId)
            if (conv?.title == "New Agent Chat") {
                db.conversations().update(
                    conv.copy(
                        title = text.take(30) + if (text.length > 30) "..." else "",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else if (conv != null) {
                db.conversations().update(conv.copy(updatedAt = System.currentTimeMillis()))
            }
        }

        items += UserChatItem(id = userMsgId, text = text)

        runningAgentMessageId = null
        runningActivityId = null
        runtimeState = AgentRuntimeState.Running
        orbState = OrbState.Thinking

        val newEngine = AgentEngine(context.applicationContext, projectId = AppConfigManager.activeProjectId)
        engine = newEngine

        if (isCallModeActive) {
            voiceManager.speakThinkingPhrase()
        }

        runJob = scope.launch {
            runCatching {
                newEngine.run(text, maxTurns = AppConfigManager.maxAgentTurns).collect { event ->
                    when (event) {
                        is AgentRunEvent.TextDelta -> {
                            finishCurrentActivity()
                            val messageId = ensureAgentMessage()
                            items.replaceAgentMessage(messageId) { current ->
                                current.copy(text = current.text + event.text, streaming = true)
                            }
                            agentResponseText += event.text
                        }

                        is AgentRunEvent.ToolCallRequested -> {
                            finishCurrentAgentText()
                            val step = ActivityStepItem(
                                callId = event.call.id,
                                type = event.call.name.toActivityStepType(),
                                summary = event.call.name.readableToolName(),
                                detail = event.call.arguments,
                                arguments = event.call.arguments,
                                running = true,
                            )
                            val activityId = runningActivityId
                            if (activityId == null) {
                                val activity = ActivityChatItem(steps = listOf(step), running = true)
                                items += activity
                                runningActivityId = activity.id
                            } else {
                                items.replaceActivityItem(activityId) { current ->
                                    current.copy(running = true, steps = current.steps + step)
                                }
                            }
                        }

                        is AgentRunEvent.ToolOutputUpdated -> {
                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
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

                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
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
                            items += activity
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
                            items += activity
                        }

                        is AgentRunEvent.Completed -> {
                            runningAgentMessageId?.let { id ->
                                items.replaceAgentMessage(id) { current ->
                                    current.copy(text = current.text.ifBlank { event.finalText }, streaming = false)
                                }
                            }
                            finishCurrentActivity()
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                            runningActivityId = null

                            processSimulatedSystemCommand(event.finalText)

                            if (isCallModeActive) {
                                voiceManager.speakWithNaturalBreaks(event.finalText) {
                                    scope.launch {
                                        userPartialText = ""
                                        agentResponseText = ""
                                        listenTrigger++
                                    }
                                }
                            } else {
                                userPartialText = ""
                                agentResponseText = ""
                            }
                        }

                        is AgentRunEvent.Stopped -> {
                            runningAgentMessageId?.let { id ->
                                items.replaceAgentMessage(id) { current ->
                                    current.copy(text = current.text.ifBlank { "Stopped: ${event.reason}" }, streaming = false)
                                }
                            }
                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
                                    current.copy(running = false, steps = current.steps.markLastComplete(event.reason))
                                }
                            }
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                            runningActivityId = null
                            listenTrigger++
                        }
                    }
                }
            }.onFailure { error ->
                val messageId = runningAgentMessageId
                if (messageId != null) {
                    items.replaceAgentMessage(messageId) { current ->
                        current.copy(
                            text = current.text.ifBlank { "Error: ${error.message ?: error::class.java.simpleName}" },
                            streaming = false,
                        )
                    }
                }
                runningActivityId?.let { id ->
                    items.replaceActivityItem(id) { current ->
                        current.copy(running = false, steps = current.steps.markLastComplete(error.message ?: "Run failed", isError = true))
                    }
                }
                runtimeState = AgentRuntimeState.Idle
                runningAgentMessageId = null
                runningActivityId = null
                listenTrigger++
            }
        }
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""

        if (runtimeState == AgentRuntimeState.Running) {
            engine?.steer(text)
            scope.launch {
                db.messages().insert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = currentConversationId ?: "",
                        role = "user",
                        content = text,
                        createdAt = System.currentTimeMillis(),
                        tokenCount = 0
                    )
                )
            }
            items += UserChatItem(text = text)
            return
        }

        submitQuery(text)
    }

    // Dynamic Orb State updates based on speaking / thinking states
    LaunchedEffect(isCallModeActive, voiceSpeaking, runtimeState) {
        if (isCallModeActive) {
            orbState = when {
                isCallMuted -> OrbState.Idle
                voiceSpeaking -> OrbState.Speaking
                runtimeState == AgentRuntimeState.Running -> OrbState.Thinking
                else -> OrbState.Listening
            }
        }
    }

    // Track if interruption was already handled to prevent double-fire
    var wasInterrupted by remember { mutableStateOf(false) }

    // Interruption / Cut-off loop checking user Voice Amplitude during agent playbacks or thinking
    LaunchedEffect(isCallModeActive, isCallMuted, userAmplitude) {
        if (isCallModeActive && !isCallMuted && userAmplitude > 0.15f && !wasInterrupted) {
            if (voiceSpeaking || runtimeState == AgentRuntimeState.Running) {
                wasInterrupted = true
                voiceManager.stop()
                stopCurrentRun("Interrupted by user speech")
                agentResponseText = "Listening..."
                scope.launch {
                    delay(1000)
                    wasInterrupted = false
                    listenTrigger++
                }
            }
        }
    }

    // Always-on speech processing loop with guarded re-listen
    var isRecognizerListening by remember { mutableStateOf(false) }
    LaunchedEffect(isCallModeActive, isCallMuted, listenTrigger) {
        if (isCallModeActive) {
            if (isCallMuted) {
                isRecognizerListening = false
                voiceRecognizer.cancelListening()
            } else if (!isRecognizerListening) {
                isRecognizerListening = true
                voiceRecognizer.startListening(
                    onResult = { text ->
                        isRecognizerListening = false
                        if (text.isNotBlank()) {
                            if (voiceSpeaking || runtimeState == AgentRuntimeState.Running) {
                                voiceManager.stop()
                                stopCurrentRun("Interrupted by user speech")
                            }
                            submitVoiceQuery(text)
                        } else {
                            scope.launch {
                                delay(500)
                                listenTrigger++
                            }
                        }
                    },
                    onError = { error ->
                        isRecognizerListening = false
                        scope.launch {
                            delay(1000)
                            listenTrigger++
                        }
                    }
                )
            }
        } else {
            isRecognizerListening = false
            wasInterrupted = false
            voiceRecognizer.cancelListening()
            if (orbState != OrbState.Idle) {
                voiceManager.stop()
                orbState = OrbState.Idle
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
            voiceRecognizer.destroy()
        }
    }

    fun submitVoiceQuery(text: String) {
        if (text.isBlank()) return
        userPartialText = text
        agentResponseText = ""
        submitQuery(text)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            isCallModeActive = true
            isCallMuted = false
        }
    }

    fun startVoiceSession() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isCallModeActive = true
            isCallMuted = false
            ServiceManager.start(context.applicationContext)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(startVoiceTrigger) {
        if (startVoiceTrigger) {
            startVoiceSession()
            onVoiceTriggerHandled()
        }
    }

    if (isCallModeActive) {
        BackHandler {
            isCallModeActive = false
            voiceManager.stop()
        }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DeepBlack.copy(alpha = 0.95f),
                drawerContentColor = SoftWhite,
            ) {
                SidebarContent(
                    activeConversationId = currentConversationId,
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onSelectConversation = { id ->
                        scope.launch {
                            currentConversationId = id
                            drawerState.close()
                        }
                    },
                    onNewConversation = { projectId ->
                        scope.launch {
                            val newId = UUID.randomUUID().toString()
                            db.conversations().upsert(
                                ConversationEntity(
                                    id = newId,
                                    projectId = projectId,
                                    title = "New Agent Chat",
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    status = "idle",
                                    costUsd = 0.0
                                )
                            )
                            val greeting = "Hello! I am ${AppConfigManager.agentName}, your ${AppConfigManager.agentPersonality} assistant. I'm ready to help you with ${AppConfigManager.agentPurpose}."
                            db.messages().insert(
                                MessageEntity(
                                    id = UUID.randomUUID().toString(),
                                    conversationId = newId,
                                    role = "assistant",
                                    content = greeting,
                                    createdAt = System.currentTimeMillis(),
                                    tokenCount = 0
                                )
                            )
                            currentConversationId = newId
                            drawerState.close()
                        }
                    }
                )
            }
        },
    ) {
        Scaffold(
            containerColor = DeepBlack,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isCallModeActive || voiceSpeaking) AppConfigManager.agentName else "ClawDroid",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "Open navigation",
                                tint = SoftWhite,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = ::startVoiceSession,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .background(GlassFill, CircleShape)
                                .border(1.dp, GlassBorderDim, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Voice Call",
                                tint = EmberOrange,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepBlack,
                        titleContentColor = SoftWhite,
                    ),
                )
            },
            modifier = modifier,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepBlack)
                    .padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (items.isEmpty()) {
                            EmptyGreeting(modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 16.dp,
                                ),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    when (item) {
                                        is UserChatItem -> UserMessageBubble(item)
                                        is AgentChatItem -> AgentMessageCard(
                                            item = item,
                                            onReadAloud = { voiceManager.speak(item.text) },
                                            onCopy = {
                                                val annotated = AnnotatedString(item.text)
                                                clipboardManager.setText(annotated)
                                            },
                                            onRegenerate = {
                                                val idx = items.indexOfFirst { it.id == item.id }
                                                if (idx > 0) {
                                                    var userMsgIdx = -1
                                                    for (i in (idx - 1) downTo 0) {
                                                        if (items[i] is UserChatItem) {
                                                            userMsgIdx = i
                                                            break
                                                        }
                                                    }
                                                    if (userMsgIdx >= 0) {
                                                        val userMsg = items[userMsgIdx] as UserChatItem
                                                        scope.launch {
                                                            db.messages().deleteById(item.id)
                                                            while (items.size > userMsgIdx + 1) {
                                                                items.removeAt(items.size - 1)
                                                            }
                                                            submitQuery(userMsg.text)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        is ActivityChatItem -> ActivityMessageCard(item)
                                    }
                                }
                            }
                        }
                    }

                    PremiumInputBar(
                        value = input,
                        onValueChange = { input = it },
                        state = runtimeState,
                        onSubmit = ::submit,
                        onStop = { stopCurrentRun() },
                    )
                }

                // Piper download progress dialog
                if (piperDownloadProgress > 0f && piperDownloadProgress < 1f) {
                    PiperDownloadDialog(progress = piperDownloadProgress)
                }

                // Permissions reminder dialog
                if (showPermissionsDialog) {
                    PermissionsDialog(
                        onDismiss = { showPermissionsDialog = false },
                        onGrantAll = {
                            val permissions = mutableListOf(
                                Manifest.permission.RECORD_AUDIO,
                            )
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(permissions.toTypedArray())
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                                && !android.provider.Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }
                            showPermissionsDialog = false
                        },
                    )
                }

                // Immersive full-screen Live Call Session Overlay
                VoiceOverlay(
                    visible = isCallModeActive,
                    orbState = orbState,
                    amplitude = currentAmplitude,
                    isMuted = isCallMuted,
                    onMuteToggle = { isCallMuted = !isCallMuted },
                    userPartialText = userPartialText,
                    agentResponseText = agentResponseText,
                    onBack = {
                        isCallModeActive = false
                        voiceManager.stop()
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyGreeting(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hi Rushikesh,",
            color = SoftWhite,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.7).sp,
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "what's the plan?",
            color = MutedGray,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.7).sp,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserMessageBubble(item: UserChatItem) {
    val shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(shape)
                .background(GlassFillMedium, shape)
                .border(1.dp, GlassBorderDim, shape)
                .padding(14.dp, 12.dp),
        ) {
            Text(
                text = item.text,
                color = SoftWhite,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AgentMessageCard(
    item: AgentChatItem,
    onReadAloud: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.labelLarge.copy(
                color = EmberOrange,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        if (item.text.isBlank() && item.streaming) {
            CustomProcessingLoader()
        } else if (item.text.isNotBlank()) {
            MarkdownText(
                markdown = item.text,
                color = SoftWhite,
            )
        }

        if (!item.streaming && item.text.isNotBlank()) {
            MessageActionRow(
                text = item.text,
                onReadAloud = onReadAloud,
                onCopy = onCopy,
                onRegenerate = onRegenerate
            )
        }
    }
}

@Composable
private fun ActivityMessageCard(item: ActivityChatItem) {
    val context = LocalContext.current
    val previews = remember(item.steps) { buildFilePreviews(item.steps, context) }
    var previewFile by remember { mutableStateOf<FilePreview?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineActivityTrail(steps = item.steps, running = item.running)
        if (previews.isNotEmpty()) {
            FilePreviewStrip(
                previews = previews,
                onPreview = { previewFile = it }
            )
        }
    }

    previewFile?.let { file ->
        FilePreviewDialog(preview = file, onDismiss = { previewFile = null })
    }
}

@Composable
private fun InlineActivityTrail(
    steps: List<ActivityStepItem>,
    running: Boolean,
) {
    var expanded by remember(running, steps.size) { mutableStateOf(running) }
    val commandCount = steps.count { it.type == ActivityStepType.Command }
    val latest = steps.lastOrNull()
    val title = when {
        commandCount > 0 -> "$commandCount command${if (commandCount == 1) "" else "s"}"
        latest != null -> latest.summary
        else -> "Preparing activity"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (running) "Running ·" else "Done ·",
                    color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (expanded) "Hide" else "Details",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        AnimatedVisibility(visible = expanded && steps.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.takeLast(4).forEach { step -> InlineActivityStep(step) }
                }
            }
        }
    }
}

@Composable
private fun InlineActivityStep(step: ActivityStepItem) {
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = step.type.icon, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${step.summary}${if (step.running) "…" else ""}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            if (step.isError) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "❌",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val parsed = formatStepContent(step)

                if (parsed.copyText != null || parsed.displayText.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (parsed.title.isNotEmpty()) {
                                Text(
                                    text = parsed.title,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = parsed.displayText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        if (parsed.copyText != null) {
                            val clipboardManager = LocalClipboardManager.current
                            val context = LocalContext.current
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(parsed.copyText))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy text",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (parsed.outputText.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = parsed.outputText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    state: AgentRuntimeState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    var commandMenuVisible by remember { mutableStateOf(false) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { /* Attachment handling will be wired into message state later. */ },
    )
    val showCommandButton = value.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = commandMenuVisible && showCommandButton) {
            CommandMenu(
                onCommandSelected = { command ->
                    onValueChange(command)
                    commandMenuVisible = false
                },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        ),
                    ),
                    shape = RoundedCornerShape(999.dp),
                ),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedVisibility(visible = showCommandButton) {
                    CompactIconButton(onClick = { commandMenuVisible = !commandMenuVisible }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Command menu",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CompactIconButton(onClick = { attachmentPicker.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close, // Using Close as a placeholder for add/plus
                        contentDescription = "Attach file",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        commandMenuVisible = false
                        onValueChange(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp, max = 112.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primaryContainer),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = if (state == AgentRuntimeState.Running) "Steer ClawDroid..." else "Message ClawDroid...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (state == AgentRuntimeState.Running) {
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 13.sp)
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = onSubmit),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandMenu(onCommandSelected: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CommandMenuItem("/help", "Show available commands", onCommandSelected)
            CommandMenuItem("/clear", "Start a fresh chat", onCommandSelected)
            CommandMenuItem("/runtime", "Check Linux runtime", onCommandSelected)
        }
    }
}

@Composable
private fun CommandMenuItem(
    command: String,
    description: String,
    onCommandSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .clickable { onCommandSelected(command) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = command,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MessageActionRow(
    text: String,
    onReadAloud: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                isLiked = !isLiked
                if (isLiked) isDisliked = false
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ThumbUp,
                contentDescription = "Thumbs Up",
                tint = if (isLiked) EmberOrange else MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = {
                isDisliked = !isDisliked
                if (isDisliked) isLiked = false
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ThumbDown,
                contentDescription = "Thumbs Down",
                tint = if (isDisliked) EmberOrange else MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onRegenerate,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Regenerate",
                tint = MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy text",
                tint = MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = onReadAloud,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = "Read aloud",
                tint = MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }

        IconButton(
            onClick = {},
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share",
                tint = MutedGray.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FilePreviewStrip(
    previews: List<FilePreview>,
    onPreview: (FilePreview) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        previews.forEach { preview ->
            val name = preview.path.substringAfterLast('/')
            val icon = when (preview.previewType) {
                FilePreviewType.Html -> "🌐"
                FilePreviewType.Svg -> "🎨"
                FilePreviewType.Image -> "🖼"
                FilePreviewType.Text -> "📄"
            }
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(GlassFill, shape)
                    .border(1.dp, GlassBorderDim, shape)
                    .clickable { onPreview(preview) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = name,
                        color = SoftWhite,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Preview →",
                        color = EmberOrange,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePreviewDialog(
    preview: FilePreview,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(DeepBlack, RoundedCornerShape(20.dp))
                .border(1.dp, GlassBorderDim, RoundedCornerShape(20.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preview.path.substringAfterLast('/'),
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close preview",
                            tint = MutedGray,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    when (preview.previewType) {
                        FilePreviewType.Html, FilePreviewType.Svg -> {
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        val mimeType = if (preview.previewType == FilePreviewType.Svg) "image/svg+xml" else "text/html"
                                        loadDataWithBaseURL(null, preview.content, mimeType, "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        FilePreviewType.Image -> {
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        val html = """
                                            <html><body style="margin:0;display:flex;align-items:center;justify-content:center;height:100%;background:transparent;">
                                                <img src="data:image/${preview.path.substringAfterLast('.')};base64,${preview.content}"
                                                     style="max-width:100%;max-height:100%;object-fit:contain;">
                                            </body></html>
                                        """.trimIndent()
                                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        FilePreviewType.Text -> {
                            Text(
                                text = preview.content,
                                color = MutedGray,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsDialog(
    onDismiss: () -> Unit,
    onGrantAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔓 Permissions Required",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = "ClawDroid needs the following permissions to work properly:\n\n" +
                    "🎤 Microphone — for voice input and call mode\n" +
                    "🔔 Notifications — to keep you updated on background tasks\n" +
                    "📱 Overlay — to show the agent status while you use other apps\n\n" +
                    "These help the agent assist you even when the app is minimized.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onGrantAll) {
                Text("Grant Permissions", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

private fun MutableList<ChatItem>.replaceAgentMessage(id: String, transform: (AgentChatItem) -> AgentChatItem) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index] as AgentChatItem)
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

private data class StepDetails(
    val title: String,
    val copyText: String?,
    val displayText: String,
    val outputText: String
)

private fun formatStepContent(step: ActivityStepItem): StepDetails {
    val argsObj = runCatching { JSONObject(step.arguments) }.getOrNull()
    val resultObj = if (!step.result.isNullOrBlank()) {
        runCatching { JSONObject(step.result) }.getOrNull()
    } else {
        null
    }

    var title = "Input:"
    var copyText: String? = null
    var displayText = ""
    var outputText = ""

    val isError = resultObj?.has("error") == true || step.isError
    val errorMessage = resultObj?.optString("error")?.takeIf { it.isNotBlank() }
        ?: if (step.isError && step.result.isNotBlank() && !step.result.startsWith("{")) step.result else null

    val toolName = step.summary.lowercase().replace(" ", "_")

    when {
        toolName == "execute_command" || toolName == "start_process" -> {
            title = "Command:"
            val cmd = argsObj?.optString("command") ?: ""
            copyText = cmd
            displayText = cmd
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    if (toolName == "execute_command") {
                        val exitCode = resultObj.optInt("exit_code", 0)
                        val out = resultObj.optString("output") ?: ""
                        if (exitCode != 0) {
                            "Exit Code: $exitCode\n$out".trim()
                        } else {
                            out
                        }
                    } else {
                        val procId = resultObj.optString("process_id") ?: ""
                        val initOut = resultObj.optString("initial_output") ?: ""
                        "Process Started (ID: $procId)\n$initOut".trim()
                    }
                }
                step.running -> "Executing..."
                else -> ""
            }
        }
        
        toolName == "check_process" || toolName == "kill_process" -> {
            title = "Process ID:"
            val procId = argsObj?.optString("process_id") ?: ""
            copyText = procId
            displayText = procId
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val cmd = resultObj.optString("command") ?: ""
                    val state = resultObj.optString("state") ?: ""
                    val exitCode = resultObj.optInt("exit_code", -1)
                    val recent = resultObj.optString("recent_output") ?: ""
                    buildString {
                        append("Command: $cmd\n")
                        append("State: $state")
                        if (exitCode != -1) append(" (Exit Code: $exitCode)")
                        if (recent.isNotEmpty()) append("\n\nOutput:\n$recent")
                    }
                }
                step.running -> "Checking process..."
                else -> ""
            }
        }

        toolName == "send_input" -> {
            title = "Send Input:"
            val procId = argsObj?.optString("process_id") ?: ""
            val inputVal = argsObj?.optString("input") ?: ""
            copyText = inputVal
            displayText = "Process ID: $procId\nInput: $inputVal"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val state = resultObj.optString("state") ?: ""
                    val recent = resultObj.optString("recent_output") ?: ""
                    buildString {
                        append("State: $state\n\nRecent Output:\n$recent")
                    }
                }
                step.running -> "Sending input..."
                else -> ""
            }
        }

        toolName == "list_processes" -> {
            title = ""
            copyText = null
            displayText = "Listing active processes"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val array = resultObj.optJSONArray("processes")
                    if (array != null && array.length() > 0) {
                        buildString {
                            for (i in 0 until array.length()) {
                                val proc = array.optJSONObject(i) ?: continue
                                val pid = proc.optString("process_id")
                                val cmd = proc.optString("command")
                                val state = proc.optString("state")
                                append("[$pid] $state: $cmd\n")
                            }
                        }.trim()
                    } else {
                        "No active processes found."
                    }
                }
                step.running -> "Retrieving process list..."
                else -> ""
            }
        }

        toolName == "read_file" -> {
            title = "Read File Path:"
            val path = argsObj?.optString("path") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> resultObj.optString("content") ?: ""
                step.running -> "Reading file..."
                else -> ""
            }
        }

        toolName == "write_file" -> {
            title = "Write File Path:"
            val path = argsObj?.optString("path") ?: ""
            val content = argsObj?.optString("content") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val bytes = resultObj.optLong("bytes", 0)
                    "Successfully wrote $bytes bytes."
                }
                step.running -> "Writing file..."
                else -> content
            }
        }

        toolName == "edit_file" -> {
            title = "Edit File Path:"
            val path = argsObj?.optString("path") ?: ""
            val search = argsObj?.optString("search") ?: ""
            val replace = argsObj?.optString("replace") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val replacements = resultObj.optInt("replacements", 0)
                    "Made $replacements replacement(s)."
                }
                step.running -> "Editing file...\nSearch:\n$search\n\nReplace:\n$replace"
                else -> ""
            }
        }

        toolName == "list_directory" -> {
            title = "Directory Path:"
            val path = argsObj?.optString("path") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val entries = resultObj.optJSONArray("entries")
                    if (entries != null && entries.length() > 0) {
                        buildString {
                            for (i in 0 until entries.length()) {
                                val entry = entries.optJSONObject(i) ?: continue
                                val name = entry.optString("name")
                                val type = entry.optString("type")
                                val bytes = entry.optLong("bytes", -1)
                                val sizeStr = if (type == "file" && bytes != -1L) {
                                    formatBytes(bytes)
                                } else ""
                                append("- [${type.capitalize()}] $name${if (sizeStr.isNotEmpty()) " ($sizeStr)" else ""}\n")
                            }
                        }.trim()
                    } else {
                        "Directory is empty."
                    }
                }
                step.running -> "Listing directory contents..."
                else -> ""
            }
        }

        toolName == "browse_web" -> {
            title = "Browse URL:"
            val url = argsObj?.optString("url") ?: ""
            copyText = url
            displayText = url
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> resultObj.optString("content") ?: ""
                step.running -> "Browsing webpage..."
                else -> ""
            }
        }

        toolName == "web_search" -> {
            title = "Search Query:"
            val query = argsObj?.optString("query") ?: ""
            copyText = query
            displayText = query
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val results = resultObj.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        buildString {
                            for (i in 0 until results.length()) {
                                val item = results.optJSONObject(i) ?: continue
                                val titleText = item.optString("title")
                                val url = item.optString("url")
                                val snippet = item.optString("snippet")
                                append("${i + 1}. $titleText\n   $url\n   $snippet\n\n")
                            }
                        }.trim()
                    } else {
                        "No search results found."
                    }
                }
                step.running -> "Searching DuckDuckGo..."
                else -> ""
            }
        }

        toolName == "send_notification" -> {
            title = "Notification:"
            val noteTitle = argsObj?.optString("title") ?: ""
            val noteBody = argsObj?.optString("body") ?: ""
            copyText = null
            displayText = "Title: $noteTitle\nBody: $noteBody"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> "Notification sent successfully."
                step.running -> "Sending notification..."
                else -> ""
            }
        }

        else -> {
            title = "Arguments:"
            copyText = step.arguments
            displayText = step.arguments
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> step.result ?: ""
                step.detail.isNotEmpty() -> step.detail
                else -> ""
            }
        }
    }

    if (errorMessage != null) {
        outputText = "Error: $errorMessage"
    }

    return StepDetails(title, copyText, displayText, outputText)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    val formattedVal = (bytes * 10 / Math.pow(1024.0, exp.toDouble())).toLong() / 10.0
    return "$formattedVal ${pre}B"
}

private fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private val PREVIEWABLE_EXTENSIONS = setOf("html", "htm", "svg", "png", "jpg", "jpeg", "gif", "webp")

private data class ParsedFilePath(
    val path: String,
    val extension: String,
)

private fun extractFilePaths(steps: List<ActivityStepItem>): List<ParsedFilePath> {
    val results = mutableListOf<ParsedFilePath>()
    for (step in steps) {
        if (step.type != ActivityStepType.Edit && step.type != ActivityStepType.File) continue
        val json = try {
            JSONObject(step.arguments)
        } catch (_: Exception) { continue }
        val path = json.optString("path").takeIf { it.isNotBlank() } ?: continue
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in PREVIEWABLE_EXTENSIONS) {
            results.add(ParsedFilePath(path, ext))
        }
    }
    return results
}

private fun readFileContent(context: Context, path: String): String? {
    val file = java.io.File(path)
    if (!file.exists() || !file.isFile) return null
    val ext = path.substringAfterLast('.', "").lowercase()
    return try {
        if (ext in setOf("html", "htm", "svg")) {
            file.readText()
        } else {
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (_: Exception) { null }
}

private fun buildFilePreviews(
    steps: List<ActivityStepItem>,
    context: Context,
): List<FilePreview> {
    return extractFilePaths(steps).mapNotNull { parsed ->
        val content = readFileContent(context, parsed.path) ?: return@mapNotNull null
        FilePreview(
            path = parsed.path,
            content = content,
            previewType = when (parsed.extension) {
                "html", "htm" -> FilePreviewType.Html
                "svg" -> FilePreviewType.Svg
                "png", "jpg", "jpeg", "gif", "webp" -> FilePreviewType.Image
                else -> FilePreviewType.Text
            },
        )
    }
}
