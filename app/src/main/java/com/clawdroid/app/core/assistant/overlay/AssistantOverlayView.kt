package com.clawdroid.app.core.assistant.overlay

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.voice.OpenAIRealtimeClient
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun AssistantOverlayView(
    invocation: AssistantInvocation?,
    status: String,
    shortLine: String,
    textDelta: String,
    answer: String,
    error: String,
    onSubmit: (String) -> Unit,
    onTranslate: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
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

    val isActing = status.startsWith("Doing:")
    val isRunning = status != "Ready" && status != "Done" && status != "Error"
    val screenshotPath = invocation?.contextSnapshot?.screenshotPath ?: invocation?.mediaPath
    val screenshotBitmap = remember(screenshotPath) {
        screenshotPath
            ?.takeIf { File(it).exists() }
            ?.let { BitmapFactory.decodeFile(it) }
            ?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isActing) 0.04f else 0.18f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (isActing) {
            CompactStatusPill(
                status = status,
                shortLine = shortLine.ifBlank { "Working in the current app..." },
                pulseAlpha = pulseAlpha,
                onStop = onStop,
                onDismiss = onDismiss,
            )
            return@Box
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (status) {
                                        "Done" -> Color(0xFF4ADE80)
                                        "Error" -> MaterialTheme.colorScheme.error
                                        "Ready" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                    },
                                ),
                        )
                        Column {
                            Text(
                                text = if (status == "Ready") "ClawDroid" else status,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isRunning || status == "Done" || status == "Error") {
                                    shortLine.ifBlank { helperText }
                                } else {
                                    helperText
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isRunning) {
                            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop assistant",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                ScreenPreview(
                    screenshotBitmap = screenshotBitmap,
                    visibleText = invocation?.contextSnapshot?.visibleText.orEmpty(),
                    contentDescriptionText = invocation?.contextSnapshot?.contentDescriptionText.orEmpty(),
                )

                AnimatedVisibility(visible = error.isNotBlank() || answer.isNotBlank() || textDelta.isNotBlank()) {
                    ResponsePane(
                        error = error,
                        answer = answer,
                        textDelta = textDelta,
                    )
                }

                AnimatedVisibility(visible = status == "Done") {
                    Text(
                        text = "Ask a follow-up to continue this assistant session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        helperText = "Ready when you are."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask about this screen") },
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isRunning,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = {
                            val text = prompt.trim()
                            if (text.isNotEmpty()) {
                                Log.i(tag, "keyboard submit invocationId=${invocation?.id} len=${text.length}")
                                prompt = ""
                                onSubmit(text)
                            }
                        },
                    ),
                    trailingIcon = {
                        IconButton(
                            enabled = !isRunning && prompt.isNotBlank(),
                            onClick = {
                                val text = prompt.trim()
                                if (text.isNotEmpty()) {
                                    Log.i(tag, "button submit invocationId=${invocation?.id} len=${text.length}")
                                    prompt = ""
                                    onSubmit(text)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(visible = isRunning) {
                        AssistantActionButton(
                            label = "Stop",
                            icon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
                            enabled = true,
                            onClick = {
                                Log.i(tag, "stop clicked invocationId=${invocation?.id}")
                                onStop()
                            },
                        )
                    }
                    AssistantActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Voice",
                        icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                        enabled = !isRunning,
                        onClick = {
                            Log.i(tag, "voice clicked invocationId=${invocation?.id}")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                Log.w(tag, "voice blocked missing RECORD_AUDIO invocationId=${invocation?.id}")
                                helperText = "Microphone permission is needed for voice input. You can still type."
                                return@AssistantActionButton
                            }
                            helperText = "Listening..."
                            recognizer.startListening(
                                onResult = { text ->
                                    Log.i(tag, "voice result invocationId=${invocation?.id} len=${text.length}")
                                    if (text.isNotBlank()) {
                                        prompt = text
                                        helperText = "Voice captured. Edit or send."
                                    } else {
                                        helperText = "No speech detected. Try again or type."
                                    }
                                },
                                onError = { message ->
                                    Log.w(tag, "voice error invocationId=${invocation?.id} message=$message")
                                    helperText = message
                                },
                            )
                        },
                    )
                    AssistantActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Translate",
                        icon = { Icon(Icons.Rounded.Translate, contentDescription = null) },
                        enabled = !isRunning,
                        onClick = {
                            Log.i(tag, "translate clicked invocationId=${invocation?.id}")
                            onTranslate()
                        },
                    )
                    AssistantActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Realtime",
                        icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                        enabled = !isRunning,
                        onClick = {
                            Log.i(tag, "realtime clicked invocationId=${invocation?.id}")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                Log.w(tag, "realtime blocked missing RECORD_AUDIO invocationId=${invocation?.id}")
                                helperText = "Microphone permission is needed for realtime voice. You can still type."
                                return@AssistantActionButton
                            }
                            helperText = "Preparing Realtime voice..."
                            coroutineScope.launch {
                                val result = realtimeClient.createClientSecret()
                                helperText = result.fold(
                                    onSuccess = {
                                        Log.i(tag, "realtime token success invocationId=${invocation?.id}")
                                        "Realtime voice is ready, but native audio transport is not connected in this build yet. Use Voice for speech-to-text."
                                    },
                                    onFailure = { error ->
                                        Log.e(tag, "realtime token failed invocationId=${invocation?.id}", error)
                                        "Realtime voice unavailable: ${error.message ?: "token request failed"}. Use Voice or type instead."
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatusPill(
    status: String,
    shortLine: String,
    pulseAlpha: Float,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = shortLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Stop assistant",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenPreview(
    screenshotBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    visibleText: String,
    contentDescriptionText: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        if (screenshotBitmap != null) {
            Image(
                bitmap = screenshotBitmap,
                contentDescription = "Current screen screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(screenshotBitmap.width.toFloat() / screenshotBitmap.height.toFloat()),
                contentScale = ContentScale.Fit,
            )
        } else {
            val fallbackText = visibleText.ifBlank { contentDescriptionText }
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Current screen",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = fallbackText.ifBlank { "No screenshot was available. I can still use visible screen text when present." }.take(220),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResponsePane(
    error: String,
    answer: String,
    textDelta: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val displayContent = when {
            error.isNotBlank() -> error
            answer.isNotBlank() -> answer
            else -> textDelta
        }

        Text(
            text = displayContent,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = if (error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AssistantActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
