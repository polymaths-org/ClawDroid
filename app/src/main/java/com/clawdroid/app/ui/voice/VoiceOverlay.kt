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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallEnd
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.components.GlowText
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillMedium
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonBlue
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(350)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack),
        ) {
            // ── Background Glows ───────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top Right Cyan Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.2f),
                        radius = size.maxDimension * 0.5f,
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                )
                // Bottom Left Blue Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonBlue.copy(alpha = 0.08f), Color.Transparent),
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
                            .background(GlassFill, CircleShape)
                            .border(1.dp, GlassBorderDim, CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = SoftWhite,
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = AppConfigManager.agentName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite,
                            )
                        )
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
                                    .background(if (isMuted) Color.Red else NeonCyan, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMuted) "Microphone Muted" else "Listening...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedGray,
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

                val transcriptShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(transcriptShape)
                        .background(GlassFill, transcriptShape)
                        .border(1.dp, GlassBorderDim, transcriptShape)
                        .padding(16.dp)
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
                                color = MutedGray.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                            )
                        } else {
                            if (userPartialText.isNotBlank()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = userPartialText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SoftWhite,
                                    )
                                }
                            }

                            if (agentResponseText.isNotBlank()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "ClawDroid",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ActivePurple,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = agentResponseText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SoftWhite,
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
                    val muteBg = if (isMuted) Color.Red.copy(alpha = 0.2f) else GlassFill
                    val muteBorder = if (isMuted) Color.Red.copy(alpha = 0.4f) else GlassBorderDim
                    val muteIconColor = if (isMuted) Color.Red else SoftWhite

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
                            .background(Color.Red.copy(alpha = 0.8f), CircleShape)
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
