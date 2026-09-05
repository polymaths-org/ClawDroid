package com.clawdroid.app.ui.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clawdroid.app.R
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.delay

@Composable
fun HatchingScreen(onComplete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var phase by remember { mutableIntStateOf(0) }
    var typedLine by remember { mutableStateOf("") }

    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val headline = when (phase) {
        0 -> "Claw Droid is dormant"
        1 -> "The shell is cracking"
        else -> "Claw Droid is awake"
    }
    val line = when (phase) {
        0 -> "Tap the egg to start the agent core."
        1 -> "Tap again to hatch your pocket agent."
        else -> "Preparing memory, tools, and workspace."
    }

    LaunchedEffect(line) {
        typedLine = ""
        line.forEachIndexed { index, _ ->
            typedLine = line.take(index + 1)
            delay(16)
        }
    }

    LaunchedEffect(phase) {
        if (phase >= 2) {
            delay(1500)
            AppConfigManager.hasSeenHatching = true
            onComplete()
        }
    }

    val infinite = rememberInfiniteTransition(label = "hatch_motion")
    val breathe by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "egg_breathe",
    )
    val lift by infinite.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "egg_lift",
    )
    val wiggle by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(80), RepeatMode.Reverse),
        label = "egg_wiggle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hint_pulse",
    )
    val scan by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "scan",
    )
    val eggSize by animateFloatAsState(
        targetValue = when (phase) {
            0 -> 214f
            1 -> 232f
            else -> 252f
        },
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "egg_size",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * (0.48f + scan * 0.04f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.28f),
                        accent.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.maxDimension * 0.48f,
                ),
                radius = size.maxDimension,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondary.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.maxDimension * 0.38f,
                ),
                radius = size.maxDimension,
                center = Offset(size.width * 0.22f, size.height * 0.18f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Claw Droid",
                color = onSurface,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                headline,
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                typedLine,
                color = onVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                minLines = 2,
            )

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "egg_phase",
            ) { currentPhase ->
                val drawable = when (currentPhase) {
                    0 -> R.drawable.egg_00
                    1 -> R.drawable.egg_01_cracked
                    else -> R.drawable.egg_02_open
                }
                Image(
                    painter = painterResource(drawable),
                    contentDescription = headline,
                    modifier = Modifier
                        .size(eggSize.dp)
                        .scale(if (currentPhase == 0) breathe else 1.05f)
                        .graphicsLayer(
                            translationY = if (currentPhase == 0) lift else 0f,
                            rotationZ = if (currentPhase == 1) wiggle else 0f,
                        )
                        .clickable(enabled = phase < 2) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            phase = (phase + 1).coerceAtMost(2)
                        },
                )
            }

            Spacer(modifier = Modifier.height(34.dp))
            HatchProgress(phase = phase, accent = accent, onVariant = onVariant)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = when (phase) {
                    0 -> "Tap the egg to hatch"
                    1 -> "One more tap"
                    else -> "Booting workspace"
                },
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(if (phase < 2) pulse else 1f),
            )
        }
    }
}

@Composable
private fun HatchProgress(
    phase: Int,
    accent: Color,
    onVariant: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(if (index <= phase) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (index <= phase) accent else onVariant.copy(alpha = 0.35f)),
            )
        }
    }
}
