package com.clawdroid.app.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.clawdroid.app.core.config.AppConfigManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.components.ClawPanel
import com.clawdroid.app.ui.components.ClawSkin
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.components.currentClawSkin
import com.clawdroid.app.ui.components.isHud

@Composable
fun VoiceOverlay(
    visible: Boolean,
    orbState: OrbState,
    amplitude: Float,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    userPartialText: String,
    agentResponseText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = currentClawSkin()
    val primaryGlow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(350)),
        modifier = modifier,
    ) {
        ClawSkinBackground(modifier = Modifier.fillMaxSize()) {
            // ── Background Glows ───────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top Right Cyan Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.2f),
                        radius = size.maxDimension * 0.5f,
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                )
                // Bottom Left Blue Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.8f),
                        radius = size.maxDimension * 0.5f,
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.2f, size.height * 0.8f),
                )
            }

            // ── Main Content Container ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 1. Top Bar Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = AppConfigManager.agentName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.sp,
                            )
                        )
                        if (skin == ClawSkin.Jarvis) {
                            Text(
                                text = "AI ASSISTANT SYSTEM",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotTransition = rememberInfiniteTransition(label = "pulse_dot")
                            val dotAlpha by dotTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                                label = "dot_alpha",
                            )

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(dotAlpha)
                                    .background(if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMuted) "Microphone Muted" else "Listening...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 2. Central 3D Audio Visualizer Orb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AudioVisualizerOrb(
                        state = orbState,
                        amplitude = amplitude,
                        modifier = Modifier.size(280.dp),
                    )
                }

                // 3. Immersive Live Transcript Box
                val scrollState = rememberScrollState()
                LaunchedEffect(userPartialText, agentResponseText) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                ClawPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    cornerRadius = if (skin.isHud()) 12.dp else 20.dp,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (userPartialText.isBlank() && agentResponseText.isBlank()) {
                            Text(
                                text = "Start speaking to ClawDroid...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                            )
                        } else {
                            if (userPartialText.isNotBlank()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = userPartialText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            if (agentResponseText.isNotBlank()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "ClawDroid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = agentResponseText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 4. Bottom Controls (Mute Toggle & End Session)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mute Button
                    val muteBg = if (isMuted) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
                    }
                    val muteBorder = if (isMuted) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                    }
                    val muteIconColor = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

                    IconButton(
                        onClick = onMuteToggle,
                        modifier = Modifier
                            .size(60.dp)
                            .background(muteBg, CircleShape)
                            .border(1.dp, muteBorder, CircleShape),
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = "Mute Mic",
                            tint = muteIconColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    // End Session Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.86f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End Session",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}
