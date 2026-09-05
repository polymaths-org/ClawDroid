package com.clawdroid.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.ElectricBlue
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill

enum class VoiceState {
    Idle, Listening, Speaking, Processing
}

/**
 * Voice Chat Indicator - Shows voice state with appropriate color and animation
 * 
 * States:
 * - Idle: Light blue, no animation, subtle orb
 * - Listening: Green, slight pulse animation
 * - Speaking: Red/Orange with rotation
 * - Processing: Purple with animation
 */
@Composable
fun VoiceChatIndicator(
    state: VoiceState,
    modifier: Modifier = Modifier,
) {
    val targetColor = when (state) {
        VoiceState.Idle -> ElectricBlue
        VoiceState.Listening -> Color(0xFF4CAF50)  // Green
        VoiceState.Speaking -> FireRed
        VoiceState.Processing -> ActivePurple
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "voice_color"
    )

    when (state) {
        VoiceState.Idle -> {
            IdleIndicator(animatedColor, modifier)
        }
        VoiceState.Listening -> {
            ListeningIndicator(animatedColor, modifier)
        }
        VoiceState.Speaking -> {
            SpeakingIndicator(animatedColor, modifier)
        }
        VoiceState.Processing -> {
            ProcessingIndicator(animatedColor, modifier)
        }
    }
}

@Composable
private fun IdleIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(2.dp, color.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color.copy(alpha = 0.6f), CircleShape)
        )
    }
}

@Composable
private fun ListeningIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(2.dp, color.copy(alpha = 0.4f), CircleShape)
            .scale(pulse),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape)
        )
    }
}

@Composable
private fun SpeakingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = { t -> t })
        ),
        label = "rotate"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(2.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color, CircleShape)
        )

        // Rotating ring
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(2.dp, color.copy(alpha = 0.3f), CircleShape)
        )
    }
}

@Composable
private fun ProcessingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "process_pulse"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(2.dp, color.copy(alpha = 0.5f), CircleShape)
            .scale(pulse),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color.copy(alpha = 0.8f), CircleShape)
        )
    }
}
