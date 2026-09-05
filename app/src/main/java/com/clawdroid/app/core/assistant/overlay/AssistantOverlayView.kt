package com.clawdroid.app.core.assistant.overlay

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clawdroid.app.R
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.voice.OpenAIRealtimeClient
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
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
    onWindowDrag: (Float, Float) -> Unit,
    onSubmit: (String) -> Unit = {},
    onTranslate: () -> Unit = {},
    onStop: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val tag = "AssistantOverlayView"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val recognizer = remember(context) { SpeechRecognizerClient(context.applicationContext) }
    val realtimeClient = remember { OpenAIRealtimeClient() }
    var prompt by remember(invocation?.id) { mutableStateOf("") }
    var helperText by remember(invocation?.id) {
        mutableStateOf("Ask about this screen or choose an action.")
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.destroy() }
    }

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
    val isVoiceOnly = invocation?.source == AssistantInvocationSource.VOICE_CALL

    if (isVoiceOnly) {
        VoiceAssistantWidget(
            isRunning = isRunning,
            pulseAlpha = pulseAlpha,
            onWindowDrag = onWindowDrag,
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

// ── Chat Assistant Widget (old bubble style) ──────────────────────────────

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
    val displayLine = when {
        error.isNotBlank() -> error
        answer.isNotBlank() -> answer
        textDelta.isNotBlank() -> textDelta
        shortLine.isNotBlank() -> shortLine
        else -> "Watching the screen and planning the next move."
    }.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(180)

    Row(
        modifier = Modifier
            .padding(10.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ),
                    ),
                    CircleShape,
                )
                .border(
                    2.dp,
                    when {
                        error.isNotBlank() -> MaterialTheme.colorScheme.error
                        isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    CircleShape,
                )
                .clickable { expanded = !expanded }
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.clawdroid_logo),
                contentDescription = "ClawDroid",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier.width(286.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 4.dp,
                shadowElevation = 5.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (status == "Ready") "ClawDroid" else status,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = if (isRunning) "Working..." else "Ready for follow-up",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (isRunning) {
                            IconButton(onClick = onStop, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        text = displayLine.ifBlank { "Thinking..." },
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                    )

                    ActionTimeline(
                        actions = actionLog,
                        isRunning = isRunning,
                        pulseAlpha = pulseAlpha,
                    )

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = onPromptChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning,
                        placeholder = { Text(if (isRunning) "Working..." else "Type a follow-up") },
                        minLines = 1,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSubmit() }),
                        trailingIcon = {
                            IconButton(enabled = !isRunning && prompt.isNotBlank(), onClick = onSubmit) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── Voice Assistant Widget (voice circle with lines visualizer) ───────────

@Composable
private fun VoiceAssistantWidget(
    isRunning: Boolean,
    pulseAlpha: Float,
    onWindowDrag: (Float, Float) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition(label = "voice_particles")
    val rot1 by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 4000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "rot1",
    )
    val rot2 by transition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 5500 else 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "rot2",
    )
    val rot3 by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 7000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "rot3",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 1200 else 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "ripple",
    )

    var tapCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .padding(10.dp)
            .size(80.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onWindowDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(isRunning) {
                detectTapGestures {
                    tapCount++
                    if (tapCount >= 2) {
                        tapCount = 0
                        onDismiss()
                    }
                    coroutineScope.launch {
                        delay(400)
                        tapCount = 0
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val colors = MaterialTheme.colorScheme
        val primary = colors.primary
        val secondary = colors.secondary
        val tertiary = colors.tertiary

        Canvas(modifier = Modifier.size(80.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val baseR = minOf(cx, cy) * 0.48f
            val ampWave = if (isRunning) 8f + sin(ripple * PI.toFloat() * 6) * 5f else 2f

            // Ripple ring
            if (isRunning) {
                val rr = baseR * (1.6f + sin(ripple * PI.toFloat() * 2) * 0.3f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.12f * (1f - (rr - baseR * 1.6f) / (baseR * 0.6f))),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = rr,
                    ),
                    radius = rr,
                    center = Offset(cx, cy),
                )
            }

            // Sweep gradient rotating ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primary.copy(alpha = if (isRunning) 0.35f else 0.12f),
                        secondary.copy(alpha = if (isRunning) 0.15f else 0.05f),
                        Color.Transparent,
                        primary.copy(alpha = if (isRunning) 0.35f else 0.12f),
                    ),
                ),
                radius = baseR * 1.5f,
                center = Offset(cx, cy),
                alpha = 0.4f,
            )

            // Draw radial line segments (circular equalizer) — 3 rings
            // Inner ring — short lines, fast
            val innerCount = 16
            for (i in 0 until innerCount) {
                val angle = (i / innerCount.toFloat()) * 360f + rot1 + sin(ripple * PI.toFloat() * 3 + i * 0.5f) * 8f
                val rad = angle * (PI.toFloat() / 180f)
                val wave = sin(ripple * PI.toFloat() * 4 + i * 0.9f) * ampWave
                val r = baseR * 0.85f
                val len = (3f + (wave * 0.5f).coerceAtLeast(0f))
                val a = (0.35f + wave / 18f).coerceIn(0.08f, 0.7f)
                val startR = r - len * 0.3f
                val endR = r + len * 0.7f
                drawLine(
                    color = primary.copy(alpha = a),
                    start = Offset(cx + cos(rad) * startR, cy + sin(rad) * startR),
                    end = Offset(cx + cos(rad) * endR, cy + sin(rad) * endR),
                    strokeWidth = 1.8f + (wave * 0.06f).coerceAtLeast(0f),
                )
            }

            // Mid ring — longer lines, counter-rotating
            val midCount = 14
            for (i in 0 until midCount) {
                val angle = (i / midCount.toFloat()) * 360f + rot2 + 30f + sin(ripple * PI.toFloat() * 2.5f + i * 0.6f) * 6f
                val rad = angle * (PI.toFloat() / 180f)
                val wave = cos(ripple * PI.toFloat() * 3 + i * 0.7f) * (ampWave * 0.9f)
                val r = baseR * 1.08f
                val len = (4f + (wave * 0.6f).coerceAtLeast(0f))
                val a = (0.25f + wave / 16f).coerceIn(0.05f, 0.6f)
                val startR = r - len * 0.3f
                val endR = r + len * 0.7f
                drawLine(
                    color = secondary.copy(alpha = a),
                    start = Offset(cx + cos(rad) * startR, cy + sin(rad) * startR),
                    end = Offset(cx + cos(rad) * endR, cy + sin(rad) * endR),
                    strokeWidth = 2f + (wave * 0.08f).coerceAtLeast(0f),
                )
            }

            // Outer ring — longest lines, tertiary rotation
            val outerCount = 20
            for (i in 0 until outerCount) {
                val angle = (i / outerCount.toFloat()) * 360f + rot3 + 60f + sin(ripple * PI.toFloat() * 2 + i * 0.4f) * 5f
                val rad = angle * (PI.toFloat() / 180f)
                val wave = sin(ripple * PI.toFloat() * 3.5f + i * 0.5f) * (ampWave * 1.2f)
                val r = baseR * 1.35f
                val len = (5f + (wave * 0.7f).coerceAtLeast(0f))
                val a = (0.15f + wave / 22f).coerceIn(0.03f, 0.5f)
                val startR = r - len * 0.3f
                val endR = r + len * 0.7f
                drawLine(
                    color = tertiary.copy(alpha = a * 0.7f),
                    start = Offset(cx + cos(rad) * startR, cy + sin(rad) * startR),
                    end = Offset(cx + cos(rad) * endR, cy + sin(rad) * endR),
                    strokeWidth = 2.2f + (wave * 0.1f).coerceAtLeast(0f),
                )
            }

            // Pulsing border ring
            drawCircle(
                color = primary.copy(alpha = pulseAlpha * 0.4f),
                radius = baseR * 1.0f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
            )
        }

        Image(
            painter = painterResource(R.drawable.clawdroid_logo),
            contentDescription = "ClawDroid voice",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ),
                    ),
                    CircleShape,
                )
                .border(
                    1.5f.dp,
                    if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    CircleShape,
                )
                .padding(8.dp),
        )
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
