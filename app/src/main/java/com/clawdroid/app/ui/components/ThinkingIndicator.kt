package com.clawdroid.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Claude-style thinking indicator showing dynamic processing messages
 * Displays phrases like "Thinking...", "Analyzing...", etc. with animated dots
 */
@Composable
fun ThinkingIndicator(
    message: String = "Thinking...",
    modifier: Modifier = Modifier
) {
    val dotAnimation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        dotAnimation.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing)
            )
        )
    }

    val dots = remember(dotAnimation.value) {
        when {
            dotAnimation.value < 0.33f -> "."
            dotAnimation.value < 0.66f -> ".."
            else -> "..."
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.8f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Thinking",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .alpha(0.7f),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "$message$dots",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

/**
 * Dynamic thinking phrases provider
 * Returns contextual messages based on the type of processing
 */
object DynamicThinkingPhrases {
    private val phases = listOf(
        // Analysis phase
        "Analyzing...",
        "Examining...",
        "Reviewing...",
        "Studying...",

        // Processing phase
        "Processing...",
        "Computing...",
        "Evaluating...",
        "Working on...",

        // Generation phase
        "Drafting...",
        "Composing...",
        "Creating...",
        "Generating...",

        // Refinement phase
        "Refining...",
        "Polishing...",
        "Perfecting...",
        "Optimizing...",

        // Code-specific
        "Debugging...",
        "Building...",
        "Compiling...",
        "Testing...",

        // Research-specific
        "Searching...",
        "Verifying...",
        "Cross-referencing...",
        "Checking..."
    )

    private var lastPhaseIndex = -1

    fun nextPhrase(): String {
        lastPhaseIndex = (lastPhaseIndex + 1) % phases.size
        return phases[lastPhaseIndex]
    }

    fun randomPhrase(): String = phases.random()

    fun phraseForContext(contextHint: String): String {
        return when {
            contextHint.contains("code", ignoreCase = true) -> 
                listOf("Debugging...", "Compiling...", "Building...").random()
            contextHint.contains("search", ignoreCase = true) || 
            contextHint.contains("research", ignoreCase = true) ->
                listOf("Searching...", "Verifying...", "Cross-referencing...").random()
            contextHint.contains("write", ignoreCase = true) || 
            contextHint.contains("generate", ignoreCase = true) ->
                listOf("Drafting...", "Composing...", "Creating...").random()
            contextHint.contains("analyze", ignoreCase = true) ->
                listOf("Analyzing...", "Examining...", "Studying...").random()
            else -> randomPhrase()
        }
    }

    fun reset() {
        lastPhaseIndex = -1
    }
}
