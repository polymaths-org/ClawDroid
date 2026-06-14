package com.clawdroid.app.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MoltenYellow
import com.clawdroid.app.ui.theme.MutedGray

enum class MicState { Idle, Recording, Transcribing }

@Composable
fun VoiceInputButton(
    state: MicState,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        MicState.Idle -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .background(GlassFill, CircleShape)
                    .border(1.dp, GlassBorderDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onToggleRecording, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        tint = MutedGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        MicState.Recording -> {
            val transition = rememberInfiniteTransition(label = "mic_pulse")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse_alpha",
            )
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse_scale",
            )

            Box(
                modifier = modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Fire glow ring
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(scale)
                        .alpha(pulse)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    FireRed.copy(alpha = 0.3f),
                                    EmberOrange.copy(alpha = 0.1f),
                                    Color.Transparent,
                                ),
                            ),
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            Brush.sweepGradient(
                                listOf(FireRed, EmberOrange, MoltenYellow, FireRed),
                            ),
                            CircleShape,
                        ),
                )
                IconButton(onClick = onToggleRecording, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Stop recording",
                        tint = FireRed,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        MicState.Transcribing -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .background(GlassFill, CircleShape)
                    .border(1.dp, GlassBorderDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = {}, enabled = false, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MicOff,
                        contentDescription = "Transcribing…",
                        tint = MutedGray.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
