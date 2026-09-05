package com.clawdroid.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.ErrorRed
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill

/**
 * Voice Control Buttons - Stop and Listen buttons for voice chat
 * 
 * - STOP button (red): Halts agent immediately, stops listening, clears buffer
 * - LISTEN button (green): Makes agent listen again, resets voice recognition, shows listening indicator
 */
@Composable
fun VoiceControlButtons(
    isListening: Boolean,
    onStopPressed: () -> Unit,
    onListenPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isListening) {
            StopButton(onStopPressed)
        } else {
            ListenButton(onListenPressed)
        }
    }
}

@Composable
private fun StopButton(
    onStopPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = remember { false }

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) ErrorRed else ErrorRed.copy(alpha = 0.8f),
        animationSpec = spring(dampingRatio = 0.6f),
        label = "stop_bg"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onStopPressed,
            interactionSource = interactionSource,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop voice recording",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ListenButton(
    onListenPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = remember { false }

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) EmberOrange else EmberOrange.copy(alpha = 0.8f),
        animationSpec = spring(dampingRatio = 0.6f),
        label = "listen_bg"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onListenPressed,
            interactionSource = interactionSource,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Listen for voice input",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
