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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.assistant.AssistantMode
import com.clawdroid.app.core.assistant.AssistantInvocationRouter
import com.clawdroid.app.core.assistant.overlay.OverlayWindowService
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.control.AndroidControlTools
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.engine.AgentRunManager
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.voice.RealtimeAudioSession
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.api.INTERNAL_USER_PROMPT_PREFIX
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.ui.components.ClawInputPanel
import com.clawdroid.app.ui.components.ClawPanel
import com.clawdroid.app.ui.components.ClawSkin
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.components.CustomProcessingLoader
import com.clawdroid.app.ui.components.FifMascot
import com.clawdroid.app.ui.components.PiperDownloadDialog
import com.clawdroid.app.ui.components.StaggeredWordsText
import com.clawdroid.app.ui.components.currentClawSkin
import com.clawdroid.app.ui.components.isHud
import com.clawdroid.app.ui.markdown.MarkdownText
import com.clawdroid.app.ui.sidebar.SidebarContent
import com.clawdroid.app.ui.voice.OrbState
import com.clawdroid.app.ui.voice.VoiceOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun shouldUseOverlayForTask(text: String): Boolean {
    val lower = text.lowercase()
    val directControlPhrase = listOf(
        "open app",
        "open ",
        "launch app",
        "launch ",
        "start app",
        "go to ",
        "tap ",
        "click ",
        "scroll",
        "swipe",
        "type ",
        "send message",
        "reply to",
        "perform",
        "in instagram",
        "in whatsapp",
        "in spotify",
        "in gmail",
        "in chrome",
        "on screen",
        "current app",
    ).any { lower.contains(it) }
    if (directControlPhrase) return true

    val appVerb = listOf("open", "launch", "start").any { verb ->
        lower.startsWith("$verb ") || lower.contains(" $verb ")
    }
    if (!appVerb) return false

    val knownAppName = listOf(
        "whatsapp",
        "telegram",
        "instagram",
        "spotify",
        "youtube",
        "gmail",
        "chrome",
        "settings",
        "maps",
        "phone",
        "dialer",
        "messages",
        "play store",
    ).any { lower.contains(it) }

    return knownAppName
}

private fun extractDirectLaunchAppQuery(text: String): String? {
    val match = Regex(
        pattern = "^\\s*(?:please\\s+)?(?:open|launch|start)(?:\\s+the)?(?:\\s+app)?\\s+(.+?)\\s*[.!?]*\\s*$",
        option = RegexOption.IGNORE_CASE,
    ).find(text) ?: return null
    val query = match.groupValues[1]
        .substringBefore(" and ")
        .substringBefore(" then ")
        .trim()
    if (query.length < 2) return null
    val blocked = setOf("it", "this", "that", "app", "application")
    return query.takeUnless { it.lowercase(Locale.US) in blocked }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
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
    var currentConversationId by remember { mutableStateOf<String?>(AppConfigManager.activeConversationId) }

    // Visual / Chat Items
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedMediaMimeType by remember { mutableStateOf<String?>(null) }
    var selectedMediaName by remember { mutableStateOf<String?>(null) }
    var voiceOverlayText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Run Manager States
    val activeRunsState by AgentRunManager.activeRuns.collectAsState()
    val activeAssistantConversationId by AssistantInvocationRouter.activeConversationId.collectAsState()
    val currentRunState = activeRunsState[currentConversationId]

    val isAgentRunning by remember(currentConversationId, currentRunState) {
        currentRunState?.isRunning ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    }.collectAsState()

    val activeChatItems by remember(currentConversationId, currentRunState) {
        currentRunState?.activeChatItems ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }.collectAsState()

    val agentResponseText by remember(currentConversationId, currentRunState) {
        currentRunState?.agentResponseText ?: kotlinx.coroutines.flow.MutableStateFlow("")
    }.collectAsState()

    val isAssistantConversationRunning = currentConversationId != null &&
        currentConversationId == activeAssistantConversationId
    val runtimeState = if (isAgentRunning || isAssistantConversationRunning) AgentRuntimeState.Running else AgentRuntimeState.Idle
    val engine = remember(currentConversationId, isAgentRunning, currentRunState) {
        currentRunState?.engine
    }
    val displayItems by remember(activeChatItems) {
        derivedStateOf {
            val activeOnly = activeChatItems.filter { item ->
                when (item) {
                    is AgentChatItem -> item.streaming
                    is ActivityChatItem -> item.running
                    else -> true
                }
            }
            items + activeOnly
        }
    }

    // Voice & Call states
    val voiceManager = remember { VoiceManager(context.applicationContext) }
    val voiceRecognizer = remember { SpeechRecognizerClient(context.applicationContext) }
    val realtimeSession = remember { RealtimeAudioSession(context.applicationContext) }
    var isCallModeActive by remember { mutableStateOf(false) }
    var isCallMuted by remember { mutableStateOf(false) }
    var orbState by remember { mutableStateOf(OrbState.Idle) }
    var realtimeModeRequested by remember { mutableStateOf(false) }
    var pendingVoiceStartAfterPermission by remember { mutableStateOf(false) }

    // Real-time transcript components
    var userPartialText by remember { mutableStateOf("") }
    var listenTrigger by remember { mutableStateOf(0) }
    var isRecognizerListening by remember { mutableStateOf(false) }
    var liveTtsBuffer by remember { mutableStateOf("") }
    var liveTtsSpokeAny by remember { mutableStateOf(false) }
    var realtimeAgentTranscript by remember { mutableStateOf("") }
    var realtimeLastUserCommit by remember { mutableStateOf("") }
    var realtimeLastAgentCommit by remember { mutableStateOf("") }

    val voiceSpeaking by voiceManager.isSpeaking.collectAsState()
    val partialSpeech by voiceRecognizer.partialResult.collectAsState()
    val piperDownloadProgress by voiceManager.downloadProgress.collectAsState()
    val realtimeActive by realtimeSession.isActive.collectAsState()

    var showPermissionsDialog by remember { mutableStateOf(false) }
    var wasInterrupted by remember { mutableStateOf(false) }

    // Real-time Voice Amplitudes
    val userAmplitude by voiceRecognizer.userVoiceAmplitude.collectAsState()
    val agentAmplitude by voiceManager.agentVoiceAmplitude.collectAsState()
    val realtimeUserAmplitude by realtimeSession.userAmplitude.collectAsState()
    val realtimeAgentAmplitude by realtimeSession.agentAmplitude.collectAsState()
    val currentAmplitude = when (orbState) {
        OrbState.Listening -> if (realtimeModeRequested || realtimeActive) realtimeUserAmplitude else userAmplitude
        OrbState.Speaking -> if (realtimeModeRequested || realtimeActive) realtimeAgentAmplitude else agentAmplitude
        else -> 0f
    }

    // Permission Launchers defined AT THE COMPOSABLE TOP LEVEL
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {}
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingVoiceStartAfterPermission = true
        }
    }

    // ── Local helper functions placed BEFORE any LaunchedEffect / usage ──
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

    fun appendRealtimeChatMessage(role: String, text: String) {
        val trimmed = text.trim()
        val convId = currentConversationId ?: return
        if (trimmed.isBlank()) return
        if (role == "user" && trimmed == realtimeLastUserCommit) return
        if (role == "assistant" && trimmed == realtimeLastAgentCommit) return

        val msgId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        if (role == "user") {
            realtimeLastUserCommit = trimmed
            items += UserChatItem(id = msgId, text = trimmed, createdAt = createdAt)
        } else {
            realtimeLastAgentCommit = trimmed
            items += AgentChatItem(id = msgId, text = trimmed, streaming = false, createdAt = createdAt)
        }
        scope.launch {
            db.messages().insert(
                MessageEntity(
                    id = msgId,
                    conversationId = convId,
                    role = role,
                    content = trimmed,
                    createdAt = createdAt,
                    tokenCount = 0,
                )
            )
            db.conversations().getById(convId)?.let { conv ->
                db.conversations().update(conv.copy(updatedAt = System.currentTimeMillis()))
            }
        }
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

    fun stopCurrentRun(reason: String = "Stopped") {
        if (!AssistantInvocationRouter.stopIfActiveConversation(currentConversationId)) {
            AgentRunManager.stopRun(currentConversationId)
        }
        listenTrigger++
    }

    fun submitQuery(text: String, mediaPath: String? = null, mediaMimeType: String? = null) {
        val convId = currentConversationId ?: return

        val userMsgId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        // Add to items immediately with the same ID to prevent key change / flickering
        items += UserChatItem(id = userMsgId, text = text, mediaPath = mediaPath, mediaMimeType = mediaMimeType, createdAt = createdAt)

        scope.launch {
            db.messages().insert(
                MessageEntity(
                    id = userMsgId,
                    conversationId = convId,
                    role = "user",
                    content = text,
                    createdAt = createdAt,
                    tokenCount = 0,
                    mediaPath = mediaPath,
                    mediaMimeType = mediaMimeType
                )
            )
            val conv = db.conversations().getById(convId)
            if (conv != null) {
                db.conversations().update(conv.copy(updatedAt = System.currentTimeMillis()))
            }
            // Start the run after database insert finishes so context is built correctly
            val directLaunchQuery = if (mediaPath == null) extractDirectLaunchAppQuery(text) else null
            if (directLaunchQuery != null) {
                val result = AndroidControlTools.launchApp(directLaunchQuery, context.applicationContext)
                val success = result.optBoolean("success", false)
                val appName = result.optString("app_name", directLaunchQuery).ifBlank { directLaunchQuery }
                val reply = if (success) {
                    "Opening $appName."
                } else {
                    val message = result.optString("message", "Could not launch $directLaunchQuery.")
                    "$message If this keeps happening, enable Settings > Permissions > Screen Control. If the model provider reports a context-window error, start a new chat and retry."
                }
                val assistantMsg = AgentChatItem(text = reply, streaming = false)
                items += assistantMsg
                db.messages().insert(
                    MessageEntity(
                        id = assistantMsg.id,
                        conversationId = convId,
                        role = "assistant",
                        content = reply,
                        createdAt = System.currentTimeMillis(),
                        tokenCount = 0,
                    ),
                )
                Toast.makeText(context, reply, Toast.LENGTH_SHORT).show()
            } else if (mediaPath == null && shouldUseOverlayForTask(text)) {
                val canOverlay = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                    android.provider.Settings.canDrawOverlays(context)
                if (canOverlay) {
                    context.startService(Intent(context, OverlayWindowService::class.java))
                    AssistantInvocationRouter.invoke(
                        context = context.applicationContext,
                        invocation = AssistantInvocation(
                            id = UUID.randomUUID().toString(),
                            source = AssistantInvocationSource.ANDROID_CONTROL_TASK,
                            mode = AssistantMode.AUTOMATE,
                            userText = text,
                            contextSnapshot = null,
                            mediaPath = null,
                            mediaMimeType = null,
                            projectId = AppConfigManager.activeProjectId,
                            conversationId = convId,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    Toast.makeText(context, "Enable overlay permission so I can show live app-control status.", Toast.LENGTH_LONG).show()
                    overlayPermissionLauncher.launch(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                    AgentRunManager.startRun(context.applicationContext, convId, text, mediaPath, mediaMimeType)
                }
            } else {
                AgentRunManager.startRun(context.applicationContext, convId, text, mediaPath, mediaMimeType)
            }
        }

        if (isCallModeActive && !realtimeModeRequested && !realtimeActive) {
            voiceManager.speakThinkingPhrase()
        }
    }

    fun submit() {
        val text = input.trim()
        val mediaUri = selectedMediaUri
        val mediaMime = selectedMediaMimeType

        if (text.isEmpty() && mediaUri == null) return
        input = ""
        selectedMediaUri = null
        selectedMediaMimeType = null
        selectedMediaName = null

        var cachedPath: String? = null
        if (mediaUri != null) {
            val cachedFile = copyUriToCache(context, mediaUri)
            if (cachedFile != null) {
                cachedPath = cachedFile.absolutePath
            }
        }

        if (runtimeState == AgentRuntimeState.Running) {
            val convId = currentConversationId ?: return
            engine?.steer(text)
            val steerMsgId = UUID.randomUUID().toString()
            val createdAt = System.currentTimeMillis()
            items += UserChatItem(id = steerMsgId, text = text, mediaPath = cachedPath, mediaMimeType = mediaMime, createdAt = createdAt)
            scope.launch {
                db.messages().insert(
                    MessageEntity(
                        id = steerMsgId,
                        conversationId = convId,
                        role = "user",
                        content = text,
                        createdAt = createdAt,
                        tokenCount = 0,
                        mediaPath = cachedPath,
                        mediaMimeType = mediaMime
                    )
                )
            }
            return
        }

        submitQuery(text, cachedPath, mediaMime)
    }

    fun submitVoiceQuery(text: String) {
        if (text.isBlank()) return
        userPartialText = text
        voiceOverlayText = ""
        liveTtsBuffer = ""
        liveTtsSpokeAny = false
        selectedMediaUri = null
        selectedMediaMimeType = null
        selectedMediaName = null
        AgentRunManager.setAgentResponseText(currentConversationId, "")
        submitQuery(text)
    }

    fun startVoiceSession() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            isCallModeActive = true
            isCallMuted = false
            voiceOverlayText = ""
            userPartialText = ""
            liveTtsBuffer = ""
            liveTtsSpokeAny = false
            realtimeAgentTranscript = ""
            realtimeLastUserCommit = ""
            realtimeLastAgentCommit = ""
            ServiceManager.start(context.applicationContext)
            val useRealtimeAudio = AppConfigManager.realtimeVoiceEnabled &&
                AppConfigManager.provider.equals("openai", ignoreCase = true) &&
                AppConfigManager.openaiRealtimeApiKey.isNotBlank()
            if (useRealtimeAudio) {
                realtimeModeRequested = true
                isRecognizerListening = false
                voiceRecognizer.cancelListening()
                voiceManager.stop()
                orbState = OrbState.Listening
                voiceOverlayText = "Connecting realtime voice..."
                scope.launch {
                    val result = realtimeSession.start(
                        onStatus = { status ->
                            withContext(Dispatchers.Main) {
                                voiceOverlayText = status
                            }
                        },
                        onUserTranscriptDelta = { delta ->
                            withContext(Dispatchers.Main) {
                                userPartialText += delta
                            }
                        },
                        onUserTranscriptCompleted = { text ->
                            withContext(Dispatchers.Main) {
                                if (text.isNotBlank()) {
                                    userPartialText = text
                                    appendRealtimeChatMessage("user", text)
                                }
                            }
                        },
                        onAgentTranscriptDelta = { delta ->
                            withContext(Dispatchers.Main) {
                                realtimeAgentTranscript += delta
                                voiceOverlayText = realtimeAgentTranscript
                                orbState = OrbState.Speaking
                            }
                        },
                        onAgentTranscriptCompleted = { text ->
                            withContext(Dispatchers.Main) {
                                val finalText = text.ifBlank { realtimeAgentTranscript }
                                appendRealtimeChatMessage("assistant", finalText)
                            }
                        },
                        onUserSpeechStarted = {
                            realtimeSession.interrupt()
                            withContext(Dispatchers.Main) {
                                userPartialText = ""
                                realtimeAgentTranscript = ""
                                voiceOverlayText = "Listening..."
                                orbState = OrbState.Listening
                            }
                        },
                        onAgentResponseStarted = {
                            withContext(Dispatchers.Main) {
                                realtimeAgentTranscript = ""
                                voiceOverlayText = ""
                                orbState = OrbState.Speaking
                            }
                        },
                        onAgentResponseCompleted = {
                            withContext(Dispatchers.Main) {
                                userPartialText = ""
                                orbState = OrbState.Listening
                            }
                        },
                        onError = { message ->
                            withContext(Dispatchers.Main) {
                                voiceOverlayText = ""
                                Toast.makeText(context, "Realtime voice unavailable: $message", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    if (result.isFailure) {
                        withContext(Dispatchers.Main) {
                            realtimeModeRequested = false
                            isRecognizerListening = false
                            listenTrigger++
                        }
                    }
                }
            } else {
                realtimeModeRequested = false
                listenTrigger++
            }
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ── LaunchedEffects / Observers follow sequentially ──
    LaunchedEffect(pendingVoiceStartAfterPermission) {
        if (pendingVoiceStartAfterPermission) {
            pendingVoiceStartAfterPermission = false
            startVoiceSession()
        }
    }

    LaunchedEffect(Unit) {
        db.conversations().pruneAllEmpty()
    }

    // Pick latest conversation on start
    val allConversations by db.conversations().observeConversations().collectAsState(initial = null)
    LaunchedEffect(allConversations) {
        val list = allConversations ?: return@LaunchedEffect
        val exists = list.any { it.id == currentConversationId }
        if (!exists) {
            val latest = list.firstOrNull()
            if (latest != null) {
                currentConversationId = latest.id
                AppConfigManager.activeConversationId = latest.id
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
                currentConversationId = newId
                AppConfigManager.activeConversationId = newId
            }
        }
    }

    LaunchedEffect(currentConversationId) {
        currentConversationId?.let { convId ->
            val conv = db.conversations().getById(convId)
            AppConfigManager.activeProjectId = conv?.projectId
        }
    }

    LaunchedEffect(currentConversationId) {
        items.clear()
        userPartialText = ""
        input = ""
        selectedMediaUri = null
        selectedMediaMimeType = null
        selectedMediaName = null
        voiceOverlayText = ""
        errorMessage = null
        val convId = currentConversationId ?: return@LaunchedEffect
        db.messages().observeMessagesWithToolCalls(convId).collect { messagesWithTools ->
            val list = mutableListOf<ChatItem>()
            for (msgWithTools in messagesWithTools) {
                val msg = msgWithTools.message
                val toolCalls = msgWithTools.toolCalls
                if (msg.role == "user") {
                    if (!msg.content.startsWith("Previous conversation summary:")
                        && !msg.content.startsWith(INTERNAL_USER_PROMPT_PREFIX)
                    ) {
                        list += UserChatItem(
                            id = msg.id,
                            text = msg.content,
                            mediaPath = msg.mediaPath,
                            mediaMimeType = msg.mediaMimeType,
                            createdAt = msg.createdAt,
                        )
                    }
                } else if (msg.role == "assistant") {
                    if (!msg.content.startsWith("[Compacted Summary]")) {
                        // Chronological: text content first (if any)
                        if (msg.content.isNotBlank()) {
                            list += AgentChatItem(id = msg.id, text = msg.content, streaming = false, createdAt = msg.createdAt)
                        }
                        
                        // Tool calls second
                        if (toolCalls.isNotEmpty()) {
                            val steps = toolCalls.map { t ->
                                val summary = when (t.toolName) {
                                    "write_file" -> {
                                        val content = runCatching { JSONObject(t.arguments).optString("content") }.getOrNull()
                                            ?: extractJsonField(t.arguments, "content").orEmpty()
                                        val lineCount = content.lines().size
                                        "Write File (+$lineCount lines)"
                                    }
                                    "edit_file" -> {
                                        val search = runCatching { JSONObject(t.arguments).optString("search") }.getOrNull()
                                            ?: extractJsonField(t.arguments, "search").orEmpty()
                                        val replace = runCatching { JSONObject(t.arguments).optString("replace") }.getOrNull()
                                            ?: extractJsonField(t.arguments, "replace").orEmpty()
                                        val searchLines = search.lines().size
                                        val replaceLines = replace.lines().size
                                        "Edit File (-$searchLines lines, +$replaceLines lines)"
                                    }
                                    else -> t.toolName.readableToolName()
                                }
                                ActivityStepItem(
                                    id = t.id,
                                    callId = t.id,
                                    type = t.toolName.toActivityStepType(),
                                    summary = summary,
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
                            
                            // Visual Grouping: If the last item is an ActivityChatItem, merge steps
                            val lastItem = list.lastOrNull()
                            if (lastItem is ActivityChatItem) {
                                val mergedSteps = lastItem.steps + steps
                                list[list.size - 1] = lastItem.copy(
                                    steps = mergedSteps,
                                    running = lastItem.running || steps.any { it.running }
                                )
                            } else {
                                list += ActivityChatItem(steps = steps, running = steps.any { it.running })
                            }
                        }
                    }
                }
            }
            items.clear()
            items.addAll(list)
        }
    }

    LaunchedEffect(isAgentRunning) {
        if (isAgentRunning) {
            orbState = OrbState.Thinking
        } else if (orbState == OrbState.Thinking) {
            orbState = OrbState.Idle
        }
    }

    LaunchedEffect(agentResponseText, isAgentRunning) {
        if (realtimeModeRequested || realtimeActive) return@LaunchedEffect
        if (isAgentRunning) {
            voiceOverlayText = agentResponseText
        } else if (agentResponseText.isNotBlank()) {
            voiceOverlayText = agentResponseText
        }
    }

    LaunchedEffect(currentConversationId) {
        val convId = currentConversationId ?: return@LaunchedEffect
        AgentRunManager.events.collect { (eventConvId, event) ->
            if (eventConvId == convId) {
                when (event) {
                    is AgentRunEvent.TextDelta -> {
                        if (isCallModeActive && !realtimeModeRequested && !realtimeActive) {
                            liveTtsBuffer += event.text
                            val (chunk, remaining) = takeRealtimeSpeechSegment(liveTtsBuffer, force = false)
                            if (chunk.isNotBlank()) {
                                liveTtsBuffer = remaining
                                liveTtsSpokeAny = true
                                voiceManager.speakWithNaturalBreaks(chunk)
                            }
                        }
                    }
                    is AgentRunEvent.Completed -> {
                        processSimulatedSystemCommand(event.finalText)
                        if (isCallModeActive && !realtimeModeRequested && !realtimeActive) {
                            val spokeDuringStream = liveTtsSpokeAny
                            val (remainingChunk, remainingTail) = takeRealtimeSpeechSegment(liveTtsBuffer, force = true)
                            liveTtsBuffer = remainingTail
                            liveTtsSpokeAny = false
                            val finalSpeech = when {
                                remainingChunk.isNotBlank() -> remainingChunk
                                !spokeDuringStream -> event.finalText
                                else -> ""
                            }
                            if (finalSpeech.isNotBlank()) {
                                voiceManager.speakWithNaturalBreaks(finalSpeech) {
                                    scope.launch {
                                        userPartialText = ""
                                        listenTrigger++
                                    }
                                }
                            } else {
                                scope.launch {
                                    delay(450)
                                    userPartialText = ""
                                    listenTrigger++
                                }
                            }
                        } else {
                            userPartialText = ""
                        }
                    }
                    is AgentRunEvent.RunError -> {
                        errorMessage = event.message
                        liveTtsBuffer = ""
                        liveTtsSpokeAny = false
                    }
                    else -> {}
                }
            }
        }
    }

    LaunchedEffect(partialSpeech) {
        if (isCallModeActive && partialSpeech.isNotBlank()) {
            userPartialText = partialSpeech
        }
    }

    LaunchedEffect(isCallModeActive, voiceSpeaking, runtimeState, realtimeActive, realtimeModeRequested, realtimeAgentAmplitude) {
        if (isCallModeActive) {
            orbState = when {
                isCallMuted -> OrbState.Idle
                realtimeModeRequested || realtimeActive -> {
                    if (realtimeAgentAmplitude > 0.02f) OrbState.Speaking else OrbState.Listening
                }
                voiceSpeaking -> OrbState.Speaking
                runtimeState == AgentRuntimeState.Running -> OrbState.Thinking
                else -> OrbState.Listening
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (AppConfigManager.isOnboardingComplete
            && !AppConfigManager.permissionsAsked
            && (!hasMic || !hasStorage)
        ) {
            showPermissionsDialog = true
            AppConfigManager.permissionsAsked = true
        }
    }

    LaunchedEffect(isCallModeActive, isCallMuted, userAmplitude) {
        if (isCallModeActive && !isCallMuted && !realtimeModeRequested && !realtimeActive && userAmplitude > AppConfigManager.voiceInterruptThreshold && !wasInterrupted) {
            if (voiceSpeaking || runtimeState == AgentRuntimeState.Running) {
                wasInterrupted = true
                voiceManager.stop()
                stopCurrentRun("Interrupted by user speech")
                AgentRunManager.setAgentResponseText(currentConversationId, "Listening...")
                scope.launch {
                    delay(1000)
                    wasInterrupted = false
                    listenTrigger++
                }
            }
        }
    }

    LaunchedEffect(isCallModeActive, isCallMuted, listenTrigger) {
        if (isCallModeActive) {
            if (realtimeModeRequested || realtimeActive) {
                isRecognizerListening = false
                voiceRecognizer.cancelListening()
            } else if (isCallMuted) {
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
            realtimeModeRequested = false
            scope.launch {
                realtimeSession.stop()
            }
            voiceRecognizer.cancelListening()
            if (orbState != OrbState.Idle) {
                voiceManager.stop()
                orbState = OrbState.Idle
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            realtimeSession.destroy()
            voiceManager.destroy()
            voiceRecognizer.destroy()
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
            realtimeModeRequested = false
            scope.launch {
                realtimeSession.stop()
            }
            voiceManager.stop()
        }
    }

    if (drawerState.isOpen) {
        BackHandler {
            scope.launch { drawerState.close() }
        }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size)
    }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardHeight by remember {
        derivedStateOf {
            imeInsets.getBottom(density)
        }
    }

    val dynamicBottomPadding by remember {
        derivedStateOf {
            val kbHeightDp = with(density) { imeInsets.getBottom(density).toDp() }
            val progress = (kbHeightDp.value / 250f).coerceIn(0f, 1f)
            160.dp - (74.dp * progress)
        }
    }

    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { keyboardHeight }
            .collect { height ->
                if (items.isNotEmpty()) {
                    val layoutInfo = listState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isNotEmpty()) {
                        val isAtBottom = visibleItems.any { it.index >= items.size }
                        if (isAtBottom) {
                            listState.scrollToItem(items.size)
                        }
                    }
                }
            }
    }

    val skin = currentClawSkin()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = if (currentClawSkin() == ClawSkin.ClawMagic) {
                    Modifier.fillMaxWidth(0.88f).widthIn(max = 360.dp)
                } else {
                    Modifier.width(280.dp)
                },
                drawerContainerColor = if (currentClawSkin() == ClawSkin.ClawMagic) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                SidebarContent(
                    activeConversationId = currentConversationId,
                    onSelectConversation = { id ->
                        scope.launch {
                            db.conversations().pruneEmptyExcept(id)
                            currentConversationId = id
                            AppConfigManager.activeConversationId = id
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
                            db.conversations().pruneEmptyExcept(newId)
                            currentConversationId = newId
                            AppConfigManager.activeConversationId = newId
                            drawerState.close()
                        }
                    },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToAudio = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToAutomations = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToChannels = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToSkills = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToMcp = {
                        scope.launch { drawerState.close() }
                        onNavigateToMcp()
                    },
                    onNavigateToAgentConfig = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    },
                    onNavigateToTerminal = {
                        scope.launch { drawerState.close() }
                        onNavigateToTerminal()
                    }
                )
            }
        },
    ) {
        ClawSkinBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (skin == ClawSkin.ClawMagic) {
                        ReferenceChatTopBar(
                            provider = AppConfigManager.provider,
                            model = AppConfigManager.model,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onVoice = ::startVoiceSession,
                        )
                    } else {
                        TopAppBar(
                            title = {
                                Text(
                                    text = if (isCallModeActive || voiceSpeaking) AppConfigManager.agentName else "ClawDroid",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Open navigation")
                                }
                            },
                            actions = {
                                IconButton(onClick = ::startVoiceSession) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Voice call", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            ),
                        )
                    }
                },
                modifier = modifier,
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding()),
                ) {
                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = 86.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyGreeting()
                    }
                } else {
                    val lastAgentMessageId = displayItems.lastOrNull { it is AgentChatItem }?.id
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = dynamicBottomPadding,
                        ),
                    ) {
                        items(displayItems, key = { it.id }) { item ->
                            when (item) {
                                is UserChatItem -> UserMessageBubble(item)
                                is AgentChatItem -> AgentMessageCard(
                                    item = item,
                                    showActionRow = (runtimeState == AgentRuntimeState.Idle && item.id == lastAgentMessageId),
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
                        item {
                            Spacer(modifier = Modifier.fillMaxWidth().height(1.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    PremiumInputBar(
                        value = input,
                        onValueChange = { input = it },
                        state = runtimeState,
                        onSubmit = ::submit,
                        onStop = { stopCurrentRun() },
                        onVoiceInput = ::startVoiceSession,
                        selectedMediaUri = selectedMediaUri,
                        selectedMediaName = selectedMediaName,
                        selectedMediaMimeType = selectedMediaMimeType,
                        onMediaSelected = { uri, name, mime ->
                            selectedMediaUri = uri
                            selectedMediaName = name
                            selectedMediaMimeType = mime
                        }
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
                            if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
                                try {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {}
                                }
                            } else if (android.os.Build.VERSION.SDK_INT < 30) {
                                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
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
                    onMuteToggle = {
                        val muted = !isCallMuted
                        isCallMuted = muted
                        if (realtimeModeRequested || realtimeActive) {
                            if (muted) {
                                scope.launch {
                                    realtimeSession.stop()
                                }
                                orbState = OrbState.Idle
                                voiceOverlayText = "Muted"
                            } else {
                                startVoiceSession()
                            }
                        }
                    },
                    userPartialText = userPartialText,
                    agentResponseText = voiceOverlayText,
                    onBack = {
                        isCallModeActive = false
                        realtimeModeRequested = false
                        scope.launch {
                            realtimeSession.stop()
                        }
                        voiceManager.stop()
                    }
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(300)) + expandVertically(),
                    exit = fadeOut(tween(300)) + shrinkVertically(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                ) {
                    errorMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE53935))
                                .border(1.5.dp, Color(0xFFB71C1C), RoundedCornerShape(12.dp))
                                .clickable { errorMessage = null }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Error",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(errorMessage) {
                    if (errorMessage != null) {
                        delay(4000)
                        errorMessage = null
                    }
                }
            }
        }
    }
}
}

@Composable
private fun EmptyGreeting(modifier: Modifier = Modifier) {
    val name = AppConfigManager.ownerName.trim().ifBlank { "there" }
    val skin = currentClawSkin()
    val prompt = remember {
        listOf(
            "what's on your mind?",
            "what should we move today?",
            "what can I handle for you?",
            "want me to look into something?",
            "drop a task and I will work through it.",
            "need help with an app, file, or idea?",
        ).random()
    }
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FifMascot(
            modifier = Modifier.size(if (skin == ClawSkin.ClawMagic) 86.dp else 112.dp),
            contentDescription = "ClawDroid idle mascot",
            randomize = true,
            randomKey = name,
        )
        Spacer(modifier = Modifier.height(18.dp))
        StaggeredWordsText(
            text = "Hey $name",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (skin == ClawSkin.ClawMagic) 30.sp else 32.sp,
                lineHeight = if (skin == ClawSkin.ClawMagic) 36.sp else 40.sp,
                letterSpacing = 0.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        StaggeredWordsText(
            text = prompt,
            color = if (skin == ClawSkin.ClawMagic) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (skin == ClawSkin.ClawMagic) 30.sp else 32.sp,
                lineHeight = if (skin == ClawSkin.ClawMagic) 36.sp else 42.sp,
                letterSpacing = 0.sp,
            ),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            delayStepMs = 58L,
        )
    }
}

private fun takeRealtimeSpeechSegment(buffer: String, force: Boolean): Pair<String, String> {
    val normalized = buffer
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trimStart()
    if (normalized.isBlank()) return "" to ""

    val sentenceCut = normalized.indexOfFirstIndexed { index, char ->
        index >= 28 && char in ".!?" && (index == normalized.lastIndex || normalized.getOrNull(index + 1)?.isWhitespace() == true)
    }
    val softCut = normalized.indexOfFirstIndexed { index, char ->
        index >= 56 && char in ",;:" && (index == normalized.lastIndex || normalized.getOrNull(index + 1)?.isWhitespace() == true)
    }
    val hardCut = if (normalized.length >= 96) {
        normalized.lastIndexOf(' ', startIndex = 82.coerceAtMost(normalized.lastIndex)).takeIf { it > 42 }
    } else {
        null
    }
    val cut = when {
        sentenceCut >= 0 -> sentenceCut + 1
        softCut >= 0 -> softCut + 1
        hardCut != null -> hardCut
        force -> normalized.length
        else -> -1
    }
    if (cut <= 0) return "" to normalized
    return normalized.take(cut).trim() to normalized.drop(cut).trimStart()
}

private fun formatChatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(timestamp))
}

private inline fun String.indexOfFirstIndexed(predicate: (Int, Char) -> Boolean): Int {
    for (index in indices) {
        if (predicate(index, this[index])) return index
    }
    return -1
}

@Composable
private fun ReferenceChatTopBar(
    provider: String,
    model: String,
    onOpenDrawer: () -> Unit,
    onVoice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReferenceRoundIconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Open navigation",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = provider.replaceFirstChar { it.uppercaseChar() },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    ),
                )
                Text(
                    text = model.substringAfterLast('/').take(18),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp,
                    ),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        ReferenceRoundIconButton(onClick = onVoice) {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = "Voice call",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReferenceRoundIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        ),
        content = { Box(contentAlignment = Alignment.Center) { content() } },
    )
}

@Composable
private fun AgentIdentityTag() {
    Row(
        modifier = Modifier.padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = AppConfigManager.agentName.ifBlank { "WhiteRose" }.uppercase(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
private fun ReferenceActivityCard(item: ActivityChatItem) {
    var expanded by remember(item.running, item.steps.size) { mutableStateOf(true) }
    val statusText = if (item.running) "RUNNING" else if (item.steps.any { it.isError }) "ERROR" else "DONE"
    val title = "${item.steps.size.coerceAtLeast(1)} step${if (item.steps.size == 1) "" else "s"} ${if (item.running) "running" else "completed"}"
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth(0.90f),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.03f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = statusText,
                        color = if (item.steps.any { it.isError }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (item.steps.any { it.isError }) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            )
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.20f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.steps.takeLast(5).forEach { step ->
                            ReferenceActivityStep(step)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceActivityStep(step: ActivityStepItem) {
    val parsed = formatStepContent(step)
    val detail = parsed.displayText
        .ifBlank { step.result.orEmpty() }
        .ifBlank { step.detail }
        .lineSequence()
        .firstOrNull()
        .orEmpty()
        .take(64)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    if (step.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    CircleShape,
                )
                .background(
                    if (step.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (step.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp),
            )
        }
        Text(
            text = referenceStepLabel(step),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Text(
            text = detail.ifBlank { if (step.running) "working" else "complete" },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun referenceStepLabel(step: ActivityStepItem): String {
    val name = step.summary
        .replace("Write File", "write_file")
        .replace("Edit File", "edit_file")
        .replace("Execute Command", "execute_command")
        .replace("Run Command", "execute_command")
        .substringBefore(" (")
        .substringBefore(":")
        .trim()
    return name.ifBlank { step.type.name.lowercase() }
}

@Composable
private fun UserMessageBubble(item: UserChatItem) {
    val skin = currentClawSkin()
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.85f
        val bubbleShape = if (skin == ClawSkin.ClawMagic) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(if (skin.isHud()) 14.dp else 22.dp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .clip(bubbleShape)
                    .background(
                        if (skin == ClawSkin.ClawMagic) Color.White.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                        bubbleShape,
                    )
                    .border(
                        1.dp,
                        if (skin == ClawSkin.ClawMagic) Color.White.copy(alpha = 0.07f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                        bubbleShape,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.mediaPath != null && item.mediaMimeType != null) {
                        val mediaFile = java.io.File(item.mediaPath)
                        if (mediaFile.exists() && mediaFile.isFile) {
                            val isImage = item.mediaMimeType.startsWith("image/")
                            val uri = Uri.fromFile(mediaFile)
                            val bitmap = rememberBitmapFromUri(context, uri)

                            if (isImage && bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = "User attachment",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = mediaIconForMime(item.mediaMimeType),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = mediaFile.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (item.text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = item.text,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 23.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        }
                        Text(
                            text = formatChatTimestamp(item.createdAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.sp,
                            ),
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentMessageCard(
    item: AgentChatItem,
    showActionRow: Boolean,
    onReadAloud: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val skin = currentClawSkin()
    val alpha = remember(item.id) { Animatable(0f) }
    val offsetY = remember(item.id) { Animatable(12f) }
    LaunchedEffect(item.id) {
        launch { alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
        offsetY.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (skin == ClawSkin.ClawMagic) 34.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (item.text.isBlank() && item.streaming) {
            CustomProcessingLoader()
        } else if (item.text.isNotBlank()) {
            if (skin == ClawSkin.ClawMagic) {
                AgentIdentityTag()
            }
            val contentModifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            }
            if (skin.isHud() || skin == ClawSkin.LiquidGlass) {
                ClawPanel(
                    modifier = contentModifier.fillMaxWidth(),
                    cornerRadius = if (skin.isHud()) 12.dp else 20.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    emphasis = 0.15f,
                ) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = item.text,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                SelectionContainer {
                    MarkdownText(
                        markdown = item.text,
                        modifier = contentModifier,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = formatChatTimestamp(item.createdAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.sp,
                ),
                modifier = Modifier.padding(start = if (skin == ClawSkin.ClawMagic) 2.dp else 0.dp),
            )
        }

        if (showActionRow && !item.streaming && item.text.isNotBlank()) {
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
    if (currentClawSkin() == ClawSkin.ClawMagic) {
        ReferenceActivityCard(item)
        return
    }
    val context = LocalContext.current
    val previews = remember(item.steps) { buildFilePreviews(item.steps, context) }
    var previewFile by remember { mutableStateOf<FilePreview?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.steps.size == 1) {
            InlineActivityStep(step = item.steps[0])
        } else {
            InlineActivityTrail(steps = item.steps, running = item.running)
        }
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
    val skin = currentClawSkin()
    var expanded by remember(running, steps.size) { mutableStateOf(running) }
    val commandCount = steps.count { it.type == ActivityStepType.Command }
    val latest = steps.lastOrNull()
    val title = when {
        steps.size > 1 -> "${steps.size} tools executed"
        commandCount > 0 -> "$commandCount command${if (commandCount == 1) "" else "s"}"
        latest != null -> latest.summary
        else -> "Preparing activity"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClawPanel(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            cornerRadius = if (skin.isHud()) 10.dp else 14.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            emphasis = if (running) 0.35f else 0.10f,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (running) "Running ·" else "Done ·",
                    color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = formatDiffDisplayText(title),
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
            ClawPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = if (skin.isHud()) 8.dp else 10.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.takeLast(4).forEach { step -> InlineActivityStep(step) }
                }
            }
        }
    }
}

@Composable
private fun InlineActivityStep(step: ActivityStepItem) {
    val skin = currentClawSkin()
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    ClawPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        cornerRadius = if (skin.isHud()) 8.dp else 10.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        emphasis = if (step.running) 0.30f else 0f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconForActivityStep(step.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatDiffDisplayText("${step.summary}${if (step.running) "..." else ""}"),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                )
                if (step.isError) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
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
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
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
                                    text = formatDiffDisplayText(parsed.displayText),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.sp,
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
                                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = formatDiffOutputText(parsed.outputText),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    letterSpacing = 0.sp,
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    state: AgentRuntimeState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    selectedMediaUri: Uri?,
    selectedMediaName: String?,
    selectedMediaMimeType: String?,
    onMediaSelected: (Uri?, String?, String?) -> Unit,
    onAttach: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selectedMediaUri != null) {
                AttachmentPreviewRow(
                    uri = selectedMediaUri,
                    name = selectedMediaName ?: "File",
                    mimeType = selectedMediaMimeType,
                    onClear = { onMediaSelected(null, null, null) },
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp, max = 112.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    letterSpacing = 0.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (value.isNotBlank() || selectedMediaUri != null) onSubmit()
                    },
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = if (state == AgentRuntimeState.Running) {
                                    "Steer ${AppConfigManager.agentName.ifBlank { "WhiteRose" }}..."
                                } else {
                                    "Message ${AppConfigManager.agentName.ifBlank { "WhiteRose" }}..."
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    CompactIconButton(onClick = onAttach) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    CompactIconButton(onClick = onVoiceInput) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = if (state == AgentRuntimeState.Running) onStop else onSubmit,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = if (state == AgentRuntimeState.Running) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (state == AgentRuntimeState.Running) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state == AgentRuntimeState.Running) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (state == AgentRuntimeState.Running) "Stop" else "Send",
                            modifier = Modifier.size(19.dp),
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
    onVoiceInput: () -> Unit,
    selectedMediaUri: Uri?,
    selectedMediaName: String?,
    selectedMediaMimeType: String?,
    onMediaSelected: (Uri?, String?, String?) -> Unit,
) {
    var commandMenuVisible by remember { mutableStateOf(false) }
    val skin = currentClawSkin()
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val (name, mime) = getUriMetadata(context, uri)
                onMediaSelected(uri, name, mime)
            }
        },
    )
    val showCommandButton = value.isEmpty()

    if (skin == ClawSkin.ClawMagic) {
        ReferenceInputBar(
            value = value,
            onValueChange = onValueChange,
            state = state,
            onSubmit = onSubmit,
            onStop = onStop,
            onVoiceInput = onVoiceInput,
            selectedMediaUri = selectedMediaUri,
            selectedMediaName = selectedMediaName,
            selectedMediaMimeType = selectedMediaMimeType,
            onMediaSelected = onMediaSelected,
            onAttach = { attachmentPicker.launch("*/*") },
        )
        return
    }

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

        ClawInputPanel(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                if (selectedMediaUri != null) {
                    AttachmentPreviewRow(
                        uri = selectedMediaUri,
                        name = selectedMediaName ?: "File",
                        mimeType = selectedMediaMimeType,
                        onClear = { onMediaSelected(null, null, null) }
                    )
                }

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
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Attach file",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = value.isEmpty() && state == AgentRuntimeState.Idle) {
                        CompactIconButton(onClick = onVoiceInput) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Voice input",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (value.isNotBlank() || selectedMediaUri != null) {
                                    onSubmit()
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = if (state == AgentRuntimeState.Running) "Steer ClawDroid..." else "Ask ClawDroid",
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
                        }
                    } else {
                        Surface(
                            onClick = onSubmit,
                            modifier = Modifier.size(if (skin.isHud()) 44.dp else 42.dp),
                            shape = if (skin.isHud()) RoundedCornerShape(10.dp) else CircleShape,
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
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

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
                tint = if (isLiked) activeTint else inactiveTint,
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
                tint = if (isDisliked) activeTint else inactiveTint,
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
                tint = inactiveTint,
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
                tint = inactiveTint,
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
                tint = inactiveTint,
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
                tint = inactiveTint,
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
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f), shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), shape)
                    .clickable { onPreview(preview) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = iconForPreviewType(preview.previewType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Preview",
                        color = MaterialTheme.colorScheme.primary,
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
        ClawPanel(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            cornerRadius = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    "📱 Overlay — to show the agent status while you use other apps\n" +
                    "📂 Storage — to output and read files from Documents/ClawDroid\n\n" +
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

private fun iconForActivityStep(type: ActivityStepType): ImageVector = when (type) {
    ActivityStepType.Command -> Icons.Rounded.Terminal
    ActivityStepType.File -> Icons.Rounded.Folder
    ActivityStepType.Web -> Icons.Rounded.Language
    ActivityStepType.Edit -> Icons.Rounded.EditNote
    ActivityStepType.Package -> Icons.Rounded.Inventory2
    ActivityStepType.Service -> Icons.Rounded.Cloud
}

private fun mediaIconForMime(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/") -> Icons.Rounded.Image
    mimeType.startsWith("video/") -> Icons.Rounded.Movie
    mimeType.startsWith("audio/") -> Icons.Rounded.Audiotrack
    mimeType.startsWith("text/") -> Icons.Rounded.Description
    else -> Icons.Rounded.Folder
}

private fun iconForPreviewType(type: FilePreviewType): ImageVector = when (type) {
    FilePreviewType.Html -> Icons.Rounded.Language
    FilePreviewType.Svg -> Icons.Rounded.EditNote
    FilePreviewType.Image -> Icons.Rounded.Image
    FilePreviewType.Text -> Icons.Rounded.Description
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
        ?: if (step.isError && step.result?.isNotBlank() == true && step.result?.startsWith("{") != true) step.result else null

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
            val path = argsObj?.optString("path") ?: extractJsonField(step.arguments, "path") ?: ""
            val content = argsObj?.optString("content") ?: extractJsonField(step.arguments, "content") ?: ""
            copyText = path
            
            val lineCount = content.lines().size
            displayText = "$path\n+$lineCount lines"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                else -> {
                    val previewLines = content.lines()
                    val preview = previewLines.take(150).joinToString("\n")
                    val isMore = if (previewLines.size > 150) "\n\n... [truncated, ${previewLines.size} lines total]" else ""
                    "+$lineCount lines\n\n$preview$isMore"
                }
            }
        }

        toolName == "edit_file" -> {
            title = "Edit File Path:"
            val path = argsObj?.optString("path") ?: extractJsonField(step.arguments, "path") ?: ""
            val search = argsObj?.optString("search") ?: extractJsonField(step.arguments, "search") ?: ""
            val replace = argsObj?.optString("replace") ?: extractJsonField(step.arguments, "replace") ?: ""
            copyText = path
            
            val searchLines = search.lines().size
            val replaceLines = replace.lines().size
            displayText = "$path\n-$searchLines lines, +$replaceLines lines"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                else -> {
                    val searchPreviewLines = search.lines()
                    val searchPreview = searchPreviewLines.take(150).joinToString("\n")
                    val searchMore = if (searchPreviewLines.size > 150) "\n\n... [truncated, ${searchPreviewLines.size} lines total]" else ""
                    
                    val replacePreviewLines = replace.lines()
                    val replacePreview = replacePreviewLines.take(150).joinToString("\n")
                    val replaceMore = if (replacePreviewLines.size > 150) "\n\n... [truncated, ${replacePreviewLines.size} lines total]" else ""
                    
                    buildString {
                        appendLine("-$searchLines lines, +$replaceLines lines")
                        appendLine()
                        appendLine("<<<< SEARCH")
                        appendLine(searchPreview + searchMore)
                        appendLine("==== REPLACE")
                        appendLine(replacePreview + replaceMore)
                        append(">>>>")
                    }
                }
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

@Composable
private fun formatDiffDisplayText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val regex = """([+-]\d+\s+lines)""".toRegex()
        var lastIndex = 0
        regex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val matchText = matchResult.value
            
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            
            val color = if (matchText.startsWith("+")) {
                com.clawdroid.app.ui.theme.DiffGreen
            } else {
                com.clawdroid.app.ui.theme.DiffRed
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(matchText)
            }
            
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
private fun formatDiffOutputText(text: String): AnnotatedString {
    val lines = text.split("\n")
    return buildAnnotatedString {
        var inSearchBlock = false
        var inReplaceBlock = false
        
        lines.forEachIndexed { index, line ->
            val isLast = index == lines.lastIndex
            
            when {
                line.trim() == "<<<< SEARCH" -> {
                    inSearchBlock = true
                    inReplaceBlock = false
                    withStyle(SpanStyle(color = com.clawdroid.app.ui.theme.DiffRed, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                line.trim() == "==== REPLACE" -> {
                    inSearchBlock = false
                    inReplaceBlock = true
                    withStyle(SpanStyle(color = com.clawdroid.app.ui.theme.DiffGreen, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                line.trim() == ">>>>" -> {
                    inSearchBlock = false
                    inReplaceBlock = false
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                inSearchBlock -> {
                    withStyle(SpanStyle(color = com.clawdroid.app.ui.theme.DiffRed)) {
                        append(line)
                    }
                }
                inReplaceBlock -> {
                    withStyle(SpanStyle(color = com.clawdroid.app.ui.theme.DiffGreen)) {
                        append(line)
                    }
                }
                else -> {
                    val regex = """([+-]\d+\s+lines)""".toRegex()
                    var lastIndex = 0
                    regex.findAll(line).forEach { matchResult ->
                        val start = matchResult.range.first
                        val end = matchResult.range.last + 1
                        val matchText = matchResult.value
                        
                        if (start > lastIndex) {
                            append(line.substring(lastIndex, start))
                        }
                        
                        val color = if (matchText.startsWith("+")) {
                            com.clawdroid.app.ui.theme.DiffGreen
                        } else {
                            com.clawdroid.app.ui.theme.DiffRed
                        }
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                            append(matchText)
                        }
                        
                        lastIndex = end
                    }
                    if (lastIndex < line.length) {
                        append(line.substring(lastIndex))
                    }
                }
            }
            
            if (!isLast) {
                append("\n")
            }
        }
    }
}

private fun getUriMetadata(context: Context, uri: Uri): Pair<String?, String?> {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)
    var name: String? = null
    if (uri.scheme == "content") {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return Pair(name, mimeType)
}

private fun copyUriToCache(context: Context, uri: Uri): java.io.File? {
    return try {
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "bin"
        val cacheFile = java.io.File(context.cacheDir, "attached_media_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        cacheFile
    } catch (e: Exception) {
        android.util.Log.e("ChatScreen", "Failed to copy URI to cache", e)
        null
    }
}

@Composable
private fun rememberBitmapFromUri(context: Context, uri: Uri?): androidx.compose.ui.graphics.ImageBitmap? {
    if (uri == null) return null
    return remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                bmp?.asImageBitmap()
            }
        }.getOrNull()
    }
}

@Composable
private fun AttachmentPreviewRow(
    uri: Uri,
    name: String,
    mimeType: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
        ) {
            val isImage = mimeType?.startsWith("image/") == true
            val bitmap = rememberBitmapFromUri(context, uri)
            
            if (isImage && bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = "Attachment preview",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Icon(
                            imageVector = mediaIconForMime(mimeType ?: ""),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
