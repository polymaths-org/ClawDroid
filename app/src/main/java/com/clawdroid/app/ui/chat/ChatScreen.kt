package com.clawdroid.app.ui.chat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.data.db.MessageWithToolCalls
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
            com.clawdroid.app.core.config.AppConfigManager.activeProjectId = conv?.projectId
        }
    }

    // Observe DB messages for active session
    val dbMessages by remember(currentConversationId) {
        currentConversationId?.let { id ->
            db.messages().observeMessagesWithToolCalls(id)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Local streaming message states
    var streamingText by remember { mutableStateOf("") }
    var streamingSteps by remember { mutableStateOf<List<ActivityStepItem>>(emptyList()) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }

    // Combine historical & streaming messages into visual items
    val displayItems = remember(dbMessages, isStreaming, streamingText, streamingSteps) {
        val ctx = context
        val historical = dbMessages.map { m ->
            val role = m.message.role
            val content = m.message.content
            val msgId = m.message.id
            if (role == "user") {
                UserChatItem(id = msgId, text = content)
            } else {
                val steps = m.toolCalls.map { t ->
                    ActivityStepItem(
                        id = t.id,
                        type = t.toolName.toActivityStepType(),
                        summary = t.toolName.readableToolName(),
                        detail = t.result ?: t.arguments,
                        running = t.status == "running"
                    )
                }
                val previews = buildFilePreviews(steps, ctx)
                AgentChatItem(
                    id = msgId,
                    text = content,
                    streaming = false,
                    activitySteps = steps,
                    activityRunning = steps.any { it.running },
                    filePreviews = previews,
                )
            }
        }

        if (isStreaming) {
            val previews = if (streamingSteps.isNotEmpty()) {
                buildFilePreviews(streamingSteps, ctx)
            } else emptyList()
            historical + AgentChatItem(
                id = streamingMessageId ?: "streaming",
                text = streamingText,
                streaming = true,
                activitySteps = streamingSteps,
                activityRunning = true,
                filePreviews = previews,
            )
        } else {
            historical
        }
    }

    var input by remember { mutableStateOf("") }
    var runtimeState by remember { mutableStateOf(AgentRuntimeState.Idle) }
    var engine by remember { mutableStateOf<AgentEngine?>(null) }
    var runJob by remember { mutableStateOf<Job?>(null) }

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

    suspend fun finalizeStreamingMessage(convId: String, finalText: String) {
        val msgId = streamingMessageId ?: UUID.randomUUID().toString()
        db.messages().insert(
            MessageEntity(
                id = msgId,
                conversationId = convId,
                role = "assistant",
                content = finalText,
                createdAt = System.currentTimeMillis(),
                tokenCount = 0
            )
        )
        streamingSteps.forEach { step ->
            db.toolCalls().upsert(
                ToolCallEntity(
                    id = step.id,
                    messageId = msgId,
                    toolName = step.summary.lowercase().replace(" ", "_"),
                    arguments = step.detail,
                    result = step.detail,
                    status = "completed",
                    durationMs = 0L
                )
            )
        }

        isStreaming = false
        streamingText = ""
        streamingSteps = emptyList()
        streamingMessageId = null
        runtimeState = AgentRuntimeState.Idle
    }

    fun submitQuery(text: String) {
        val convId = currentConversationId ?: return

        scope.launch {
            db.messages().insert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "user",
                    content = text,
                    createdAt = System.currentTimeMillis(),
                    tokenCount = 0
                )
            )

            streamingText = ""
            streamingSteps = emptyList()
            streamingMessageId = UUID.randomUUID().toString()
            isStreaming = true
            runtimeState = AgentRuntimeState.Running
            orbState = OrbState.Thinking

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

            val newEngine = AgentEngine(context.applicationContext, projectId = conv?.projectId)
            engine = newEngine

            if (isCallModeActive) {
                voiceManager.speakThinkingPhrase()
            }

            runJob = scope.launch {
                runCatching {
                    newEngine.run(text, maxTurns = AppConfigManager.maxAgentTurns).collect { event ->
                        when (event) {
                            is AgentRunEvent.TextDelta -> {
                                streamingText += event.text
                                agentResponseText += event.text
                            }

                            is AgentRunEvent.ToolCallRequested -> {
                                streamingSteps = streamingSteps + ActivityStepItem(
                                    id = event.call.id,
                                    type = event.call.name.toActivityStepType(),
                                    summary = event.call.name.readableToolName(),
                                    detail = event.call.arguments,
                                    running = true
                                )
                            }

                            is AgentRunEvent.ToolResultReceived -> {
                                streamingSteps = streamingSteps.map { step ->
                                    if (step.id == event.result.callId) {
                                        step.copy(detail = event.result.content, running = false)
                                    } else {
                                        step
                                    }
                                }
                            }

                            is AgentRunEvent.Completed -> {
                                finalizeStreamingMessage(convId, event.finalText)
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
                                finalizeStreamingMessage(convId, streamingText.ifBlank { "Stopped: ${event.reason}" })
                                listenTrigger++
                            }

                            else -> {}
                        }
                    }
                }.onFailure { error ->
                    finalizeStreamingMessage(
                        convId,
                        streamingText.ifBlank { "Error: ${error.message ?: error::class.java.simpleName}" })
                    listenTrigger++
                }
            }
        }
    }

    fun stopCurrentRun(reason: String = "Stopped") {
        engine?.stop()
        runJob?.cancel()
        currentConversationId?.let { convId ->
            scope.launch {
                finalizeStreamingMessage(convId, streamingText.ifBlank { reason })
            }
        }
        listenTrigger++
    }

    fun submitVoiceQuery(text: String) {
        if (text.isBlank()) return
        userPartialText = text
        agentResponseText = ""
        submitQuery(text)
    }

    // Dynamic Orb State updates based on speaking / thinking states
    LaunchedEffect(isCallModeActive, isCallMuted, voiceSpeaking, runtimeState) {
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
    var isListening by remember { mutableStateOf(false) }
    LaunchedEffect(isCallModeActive, isCallMuted, listenTrigger) {
        if (isCallModeActive) {
            if (isCallMuted) {
                isListening = false
                voiceRecognizer.cancelListening()
            } else if (!isListening) {
                isListening = true
                voiceRecognizer.startListening(
                    onResult = { text ->
                        isListening = false
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
                        isListening = false
                        scope.launch {
                            delay(1000)
                            listenTrigger++
                        }
                    }
                )
            }
        } else {
            isListening = false
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

    fun submit() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""

        if (runtimeState == AgentRuntimeState.Running) {
            engine?.steer(text)
            currentConversationId?.let { convId ->
                scope.launch {
                    db.messages().insert(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            conversationId = convId,
                            role = "user",
                            content = text,
                            createdAt = System.currentTimeMillis(),
                            tokenCount = 0
                        )
                    )
                }
            }
            return
        }

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

    // Back handling inside call mode
    if (isCallModeActive) {
        BackHandler {
            isCallModeActive = false
            voiceManager.stop()
        }
    }

    LaunchedEffect(displayItems.size) {
        if (displayItems.isNotEmpty()) listState.animateScrollToItem(displayItems.lastIndex)
    }

    // Modal navigation drawer layout
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
                        // Premium glowing Live Call button in the top action bar
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
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(displayItems, key = { it.id }) { item ->
                            when (item) {
                                is UserChatItem -> UserMessageBubble(item)
                                is AgentChatItem -> AgentMessageCard(
                                    item = item,
                                    onReadAloud = { voiceManager.speak(item.text) },
                                    onCopy = {
                                        val annotated = buildAnnotatedString { append(item.text) }
                                        clipboardManager.setText(annotated)
                                    },
                                    onRegenerate = {
                                        val index = dbMessages.indexOfFirst { it.message.id == item.id }
                                        if (index > 0) {
                                            val prevMsg = dbMessages[index - 1].message
                                            if (prevMsg.role == "user") {
                                                scope.launch {
                                                    db.messages().deleteById(item.id)
                                                    submitQuery(prevMsg.content)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    InputBar(
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
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
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
                        if (!AppConfigManager.ultraAgentEnabled) ServiceManager.stop(context.applicationContext)
                    },
                )
            }
        }
    }
}

// ── User Message Bubble (glassmorphic) ─────────────────────────────────

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

// ── Agent Message Card ─────────────────────────────────────────────────

@Composable
private fun AgentMessageCard(
    item: AgentChatItem,
    onReadAloud: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    var previewFile by remember { mutableStateOf<FilePreview?>(null) }

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

        if (item.activitySteps.isNotEmpty() || item.activityRunning) {
            InlineActivityTrail(
                steps = item.activitySteps,
                running = item.activityRunning,
            )
        }

        // File previews (canvas)
        if (item.filePreviews.isNotEmpty()) {
            FilePreviewStrip(
                previews = item.filePreviews,
                onPreview = { previewFile = it },
            )
        }

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

    // Full preview dialog
    previewFile?.let { file ->
        FilePreviewDialog(preview = file, onDismiss = { previewFile = null })
    }
}

// ── File Preview Strip ──────────────────────────────────────────────────

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

// ── File Preview Dialog ─────────────────────────────────────────────────

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
                // Header
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

                // Preview content
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

// ── Gemini-Style Message Action Row ────────────────────────────────────

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

// ── Activity Trail (glassmorphic) ──────────────────────────────────────

@Composable
private fun InlineActivityTrail(
    steps: List<ActivityStepItem>,
    running: Boolean,
) {
    var expanded by remember(running, steps.size) { mutableStateOf(running) }
    val latest = steps.lastOrNull()
    val summary = when {
        latest != null -> "${latest.type.icon} ${latest.summary}${if (latest.running) "…" else ""}"
        running -> "🔄 Preparing…"
        else -> "Activity"
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill, shape)
            .border(1.dp, GlassBorderDim, shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EmberOrange,
                )
                Text(
                    text = if (steps.isEmpty()) "" else "${steps.size} step${if (steps.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedGray,
                )
            }

            AnimatedVisibility(visible = expanded && steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.takeLast(4).forEach { step ->
                        InlineActivityStep(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineActivityStep(step: ActivityStepItem) {
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardDark.copy(alpha = 0.7f), shape)
            .border(1.dp, GlassBorderDim.copy(alpha = 0.5f), shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${step.type.icon} ${step.summary}${if (step.running) "…" else ""}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = SoftWhite,
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = step.detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 96.dp)
                        .verticalScroll(rememberScrollState())
                        .background(DeepBlack.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ── Input Bar (glassmorphic) ───────────────────────────────────────────

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    state: AgentRuntimeState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardDark, shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(GlassBorderDim, Color.Transparent)),
                shape = shape,
            )
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val tfShape = RoundedCornerShape(16.dp)
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(tfShape)
                    .border(1.dp, GlassBorderDim, tfShape),
                placeholder = {
                    Text(
                        if (state == AgentRuntimeState.Running) "Steer the agent…" else "Type a message…",
                        color = MutedGray.copy(alpha = 0.6f),
                    )
                },
                textStyle = TextStyle(color = SoftWhite, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                maxLines = 4,
                singleLine = false,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassFill,
                    unfocusedContainerColor = GlassFill.copy(alpha = 0.4f),
                    cursorColor = EmberOrange,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            Spacer(modifier = Modifier.width(4.dp))

            if (state == AgentRuntimeState.Running) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(FireRed.copy(alpha = 0.3f), Color.Transparent)),
                            CircleShape,
                        )
                        .border(1.dp, FireRed.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        tint = FireRed,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BlueGradientHorizontal, CircleShape)
                        .clickable(onClick = onSubmit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = SoftWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

private fun List<ActivityStepItem>.markLastComplete(detail: String): List<ActivityStepItem> {
    if (isEmpty()) return this
    return dropLast(1) + last().copy(detail = detail, running = false)
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

private val PREVIEWABLE_EXTENSIONS = setOf("html", "htm", "svg", "png", "jpg", "jpeg", "gif", "webp")

private data class ParsedFilePath(
    val path: String,
    val extension: String,
)

/**
 * Extracts file paths from tool call step details (JSON with "path" field)
 * that have previewable extensions.
 */
private fun extractFilePaths(steps: List<ActivityStepItem>): List<ParsedFilePath> {
    val results = mutableListOf<ParsedFilePath>()
    for (step in steps) {
        if (step.type != ActivityStepType.Edit && step.type != ActivityStepType.File) continue
        val json = try {
            org.json.JSONObject(step.detail)
        } catch (_: Exception) { continue }
        val path = json.optString("path").takeIf { it.isNotBlank() } ?: continue
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext in PREVIEWABLE_EXTENSIONS) {
            results.add(ParsedFilePath(path, ext))
        }
    }
    return results
}

/**
 * Reads a file from disk and returns its content as a string (for text-based formats like HTML/SVG)
 * or base64 (for binary formats like images).
 */
private fun readFileContent(context: android.content.Context, path: String): String? {
    val file = java.io.File(path)
    if (!file.exists() || !file.isFile) return null
    val ext = path.substringAfterLast('.', "").lowercase()
    return try {
        if (ext in setOf("html", "htm", "svg")) {
            file.readText()
        } else {
            // For images, return base64 so WebView can render <img src="data:...">
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    } catch (_: Exception) { null }
}

private fun buildFilePreviews(
    steps: List<ActivityStepItem>,
    context: android.content.Context,
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
