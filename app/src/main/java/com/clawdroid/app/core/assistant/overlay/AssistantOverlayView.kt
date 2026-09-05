package com.clawdroid.app.core.assistant.overlay

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantMode
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.ui.components.FifMascot
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun AssistantOverlayView(
    invocation: AssistantInvocation?,
    status: String,
    shortLine: String,
    textDelta: String,
    answer: String,
    error: String,
    actionLog: List<String>,
    voiceGreeting: String? = null,
    voiceListenTimeoutSeconds: Int? = null,
    onWindowDrag: (Float, Float) -> Unit,
    onSubmit: (String) -> Unit = {},
    onTranslate: () -> Unit = {},
    onStop: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val tag = "AssistantOverlayView"
    val context = LocalContext.current
    var prompt by remember(invocation?.id) { mutableStateOf("") }
    var helperText by remember(invocation?.id) {
        mutableStateOf("Ask about this screen or choose an action.")
    }
    var showTextOverlayFromVoice by remember(invocation?.id) { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "assistant_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val isRunning = status != "Ready" && status != "Done" && status != "Error"
    val isVoiceOnly = invocation?.mode == AssistantMode.VOICE_CHAT ||
        invocation?.source == AssistantInvocationSource.VOICE_CALL ||
        invocation?.source == AssistantInvocationSource.NOTIFICATION_ACTION

    if (isVoiceOnly && !showTextOverlayFromVoice) {
        val ownerName = AppConfigManager.ownerName.takeIf { it.isNotBlank() } ?: "there"
        val defaultGreeting = when (invocation?.source) {
            AssistantInvocationSource.NOTIFICATION_ACTION -> "Listening, $ownerName."
            else -> "Ready, $ownerName."
        }
        VoiceAssistantWidget(
            invocationId = invocation?.id,
            isRunning = isRunning,
            status = status,
            textDelta = textDelta,
            answer = answer,
            error = error,
            actionLog = actionLog,
            initialGreeting = voiceGreeting?.takeIf { it.isNotBlank() } ?: defaultGreeting,
            listenTimeoutSeconds = voiceListenTimeoutSeconds,
            pulseAlpha = pulseAlpha,
            onWindowDrag = onWindowDrag,
            onSubmit = { text ->
                val clean = text.trim()
                if (clean.isBlank()) return@VoiceAssistantWidget
                if (invocation?.source == AssistantInvocationSource.NOTIFICATION_ACTION && isAffirmativeTaskAnswer(clean)) {
                    NotificationHelper.sendAgentNotification(
                        context = context,
                        title = "Marked complete",
                        body = "ClawDroid marked the task complete.",
                    )
                    onDismiss()
                    return@VoiceAssistantWidget
                }
                onSubmit(clean)
            },
            onKeyboard = {
                showTextOverlayFromVoice = true
            },
            onStop = onStop,
            onDismiss = onDismiss,
        )
    } else {
        ChatAssistantWidget(
            status = status,
            shortLine = shortLine,
            textDelta = textDelta,
            answer = answer,
            error = error,
            actionLog = actionLog,
            prompt = prompt,
            onPromptChange = {
                prompt = it
                helperText = "Ready when you are."
            },
            isRunning = isRunning,
            pulseAlpha = pulseAlpha,
            onWindowDrag = onWindowDrag,
            onSubmit = {
                val text = prompt.trim()
                if (text.isNotEmpty()) {
                    Log.i(tag, "floating submit invocationId=${invocation?.id} len=${text.length}")
                    prompt = ""
                    onSubmit(text)
                }
            },
            onStop = onStop,
            onDismiss = onDismiss,
        )
    }
}

private fun isAffirmativeTaskAnswer(text: String): Boolean {
    val normalized = text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    if (normalized in setOf("yes", "yeah", "yep", "done", "mark done", "mark it done", "mark complete", "complete")) {
        return true
    }
    return normalized.contains("mark it complete") ||
        normalized.contains("mark it done") ||
        normalized.contains("task is done") ||
        normalized.contains("it is done") ||
        normalized.contains("its done")
}

// ── Chat Assistant Widget (Gemini-style bottom dock) ──────────────────────

@Composable
private fun ChatAssistantWidget(
    status: String,
    shortLine: String,
    textDelta: String,
    answer: String,
    error: String,
    actionLog: List<String>,
    prompt: String,
    onPromptChange: (String) -> Unit,
    isRunning: Boolean,
    pulseAlpha: Float,
    onWindowDrag: (Float, Float) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val mascotRandomKey = remember { System.nanoTime() }
    val context = LocalContext.current
    val displayLine = when {
        error.isNotBlank() -> error
        answer.isNotBlank() -> answer
        textDelta.isNotBlank() -> textDelta
        shortLine.isNotBlank() -> shortLine
        else -> "Watching the screen and planning the next move."
    }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(180)
    val lastTaskLine = actionLog.lastOrNull()
        ?.replace('\n', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(120)
        ?: displayLine.ifBlank { if (isRunning) "Thinking about the current screen." else "Ready for a follow-up." }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(140)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            error.isNotBlank() -> MaterialTheme.colorScheme.error
                                            isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                            else -> MaterialTheme.colorScheme.tertiary
                                        },
                                    ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (status == "Ready") "ClawDroid" else status,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = lastTaskLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        IconButton(onClick = { expanded = false }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Hide dock", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = onPromptChange,
                            modifier = Modifier.weight(1f),
                            enabled = true,
                            placeholder = { Text(if (isRunning) "Steer while I work" else "Ask a follow-up") },
                            minLines = 1,
                            maxLines = 2,
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.82f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.64f),
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSubmit() }),
                            trailingIcon = {
                                IconButton(enabled = prompt.isNotBlank(), onClick = onSubmit) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                                }
                            },
                        )
                        if (isRunning) {
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f)),
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        IconButton(
                            onClick = {
                                OverlayWindowService.startVoice(context)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.44f), CircleShape),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = "Start voice chat", tint = MaterialTheme.colorScheme.primary)
                        }
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                .border(
                                    1.dp,
                                    if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
                                    CircleShape,
                                )
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FifMascot(
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = "ClawDroid assistant mascot",
                                randomize = true,
                                randomKey = mascotRandomKey,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !expanded,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FifMascot(
                        modifier = Modifier.size(34.dp),
                        contentDescription = "Show ClawDroid dock",
                        randomize = true,
                        randomKey = mascotRandomKey,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ── Voice Assistant Widget (audio-focused bottom overlay) ─────────────────

@Composable
private fun VoiceAssistantWidget(
    invocationId: String?,
    isRunning: Boolean,
    status: String,
    textDelta: String,
    answer: String,
    error: String,
    actionLog: List<String>,
    initialGreeting: String,
    listenTimeoutSeconds: Int?,
    pulseAlpha: Float,
    onWindowDrag: (Float, Float) -> Unit,
    onSubmit: (String) -> Unit,
    onKeyboard: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val recognizer = remember(context) {
        SpeechRecognizerClient(context.applicationContext, forceSystemRecognizer = true, fastMode = true)
    }
    val voiceManager = remember(context) { VoiceManager(context.applicationContext) }
    val isListening by recognizer.isListening.collectAsState()
    val partial by recognizer.partialResult.collectAsState()
    val userAmplitude by recognizer.userVoiceAmplitude.collectAsState()
    val agentSpeaking by voiceManager.isSpeaking.collectAsState()
    val agentAmplitude by voiceManager.agentVoiceAmplitude.collectAsState()
    var lastSpokenAnswer by remember { mutableStateOf("") }
    var listenTick by remember { mutableIntStateOf(0) }
    val shouldPlayGreeting = remember(invocationId) {
        AssistantOverlayCoordinator.shouldPlayVoiceGreeting(invocationId)
    }
    var speechMuted by remember(invocationId) { mutableStateOf(false) }
    var greetingComplete by remember(invocationId, initialGreeting, shouldPlayGreeting) {
        mutableStateOf(initialGreeting.isBlank() || !shouldPlayGreeting)
    }
    var greetingStarted by remember(invocationId, initialGreeting, shouldPlayGreeting) {
        mutableStateOf(!shouldPlayGreeting)
    }

    fun submitRecognizedSpeech(text: String) {
        val clean = text.trim()
        if (clean.isNotBlank()) {
            onSubmit(clean)
        } else {
            listenTick++
        }
    }

    fun startInterruptListening() {
        speechMuted = false
        greetingComplete = true
        voiceManager.stop()
        recognizer.cancelListening()
        recognizer.startListening(
            onResult = { text -> submitRecognizedSpeech(text) },
            onError = {
                if (!isRunning) {
                    listenTick++
                }
            },
        )
    }

    DisposableEffect(Unit) {
        AssistantOverlayCoordinator.setVoiceInputActive(true)
        onDispose {
            AssistantOverlayCoordinator.setVoiceInputActive(false)
            recognizer.destroy()
            voiceManager.destroy()
        }
    }

    LaunchedEffect(invocationId, initialGreeting, shouldPlayGreeting, speechMuted) {
        if (!shouldPlayGreeting || initialGreeting.isBlank()) {
            greetingComplete = true
            return@LaunchedEffect
        }
        if (!greetingStarted && !speechMuted) {
            greetingStarted = true
            voiceManager.speakWithNaturalBreaks(initialGreeting) {
                greetingComplete = true
                listenTick++
            }
        } else if (speechMuted) {
            greetingComplete = true
        }
    }

    LaunchedEffect(isRunning, agentSpeaking, listenTick, greetingComplete) {
        if (greetingComplete && !isRunning && !agentSpeaking) {
            recognizer.startListening(
                onResult = { text -> submitRecognizedSpeech(text) },
                onError = {
                    listenTick++
                },
            )
        } else {
            recognizer.cancelListening()
        }
    }

    LaunchedEffect(isListening, listenTick, listenTimeoutSeconds, greetingComplete) {
        val timeoutSeconds = listenTimeoutSeconds ?: return@LaunchedEffect
        if (greetingComplete && isListening && timeoutSeconds > 0) {
            kotlinx.coroutines.delay(timeoutSeconds * 1_000L)
            if (recognizer.isListening.value) {
                recognizer.cancelListening()
                voiceManager.speakWithNaturalBreaks("I did not hear a response.") {
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(answer) {
        val clean = answer.trim()
        if (clean.isNotBlank() && clean != lastSpokenAnswer) {
            lastSpokenAnswer = clean
            if (speechMuted) {
                listenTick++
                return@LaunchedEffect
            }
            voiceManager.speakWithNaturalBreaks(clean) {
                listenTick++
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "voice_particles")
    val ripple by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening || agentSpeaking || isRunning) 900 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "ripple",
    )

    val activeAmplitude = when {
        agentSpeaking -> agentAmplitude
        isListening -> userAmplitude
        isRunning -> 0.35f + 0.18f * kotlin.math.sin(ripple * PI.toFloat() * 2)
        else -> 0.08f
    }.coerceIn(0f, 1f)
    val primaryLine = when {
        error.isNotBlank() -> error
        partial.isNotBlank() -> partial
        answer.isNotBlank() -> answer
        textDelta.isNotBlank() -> textDelta
        isRunning -> status.ifBlank { "Thinking..." }
        isListening -> "Listening..."
        else -> "Ready"
    }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val compactLine = primaryLine.take(170)
    val detailLine = primaryLine.take(420)
    val stateLabel = when {
        error.isNotBlank() -> "Error"
        agentSpeaking -> "Speaking"
        isRunning -> "Thinking"
        isListening -> "Listening"
        else -> "Tap the keyboard to type"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.64f),
        ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    error.isNotBlank() -> MaterialTheme.colorScheme.error
                                    isListening || agentSpeaking || isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                            ),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stateLabel,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Text(
                            text = compactLine.ifBlank { "Waiting for speech..." },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp, letterSpacing = 0.sp),
                            maxLines = 2,
                        )
                    }
                    IconButton(
                        onClick = {
                            recognizer.cancelListening()
                            voiceManager.stop()
                            onKeyboard()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)),
                    ) {
                        Icon(Icons.Rounded.Keyboard, contentDescription = "Switch to keyboard", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    if (isRunning) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.84f)),
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    IconButton(
                        onClick = {
                            recognizer.cancelListening()
                            voiceManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close voice overlay", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = when {
                            partial.isNotBlank() -> "Heard"
                            answer.isNotBlank() || textDelta.isNotBlank() -> "Response"
                            isRunning -> "Working"
                            else -> "Voice"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = detailLine.ifBlank { "Listening for your request." },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp, letterSpacing = 0.sp),
                        maxLines = 4,
                    )
                }

                if (actionLog.isNotEmpty()) {
                    ActionTimeline(actions = actionLog, isRunning = isRunning, pulseAlpha = pulseAlpha)
                }

                VoiceLineBarVisualizer(
                    amplitude = activeAmplitude,
                    phase = ripple,
                    active = isListening || agentSpeaking || isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { startInterruptListening() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.56f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Talk now and interrupt assistant",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(18.dp))
                    IconButton(
                        onClick = {
                            speechMuted = !speechMuted
                            if (speechMuted) {
                                voiceManager.stop()
                            }
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (speechMuted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.86f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                            )
                            .border(
                                1.dp,
                                if (speechMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = if (speechMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = if (speechMuted) "Unmute assistant voice" else "Mute assistant voice",
                            tint = if (speechMuted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLineBarVisualizer(
    amplitude: Float,
    phase: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val barCount = 34
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2.dp.toPx())
        val centerY = size.height / 2f
        for (i in 0 until barCount) {
            val x = i * (barWidth + gap)
            val wave = ((sin(phase * PI.toFloat() * 2f + i * 0.48f) + 1f) / 2f)
            val base = if (active) amplitude.coerceAtLeast(0.08f) else 0.05f
            val height = (8.dp.toPx() + size.height * base * (0.35f + wave * 0.75f))
                .coerceIn(6.dp.toPx(), size.height)
            val alpha = if (active) (0.34f + wave * 0.52f).coerceIn(0.34f, 0.86f) else 0.22f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = alpha),
                        colors.secondary.copy(alpha = alpha * 0.72f),
                    ),
                ),
                topLeft = Offset(x, centerY - height / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth, barWidth),
            )
        }
    }
}

// ── Action Timeline (shared) ──────────────────────────────────────────────

@Composable
private fun ActionTimeline(
    actions: List<String>,
    isRunning: Boolean,
    pulseAlpha: Float,
) {
    val visibleActions = actions.takeLast(4)
    if (visibleActions.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.50f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleActions.forEachIndexed { index, action ->
            val active = index == visibleActions.lastIndex && isRunning
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        ),
                )
                Text(
                    text = action,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}
