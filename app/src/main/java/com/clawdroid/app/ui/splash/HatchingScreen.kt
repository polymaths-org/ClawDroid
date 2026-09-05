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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clawdroid.app.R
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.delay

@Composable
fun HatchingScreen(onComplete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var hatchPhase by remember { mutableIntStateOf(0) }
    var typedMessage by remember { mutableStateOf("") }

    val message = when (hatchPhase) {
        0 -> "OpenClaw is waiting inside. Tap the egg to begin."
        1 -> "The shell is cracking. Tap once more to hatch it."
        else -> "OpenClaw is awake. Preparing your agent memory."
    }

    LaunchedEffect(message) {
        typedMessage = ""
        message.forEachIndexed { index, _ ->
            typedMessage = message.take(index + 1)
            delay(18)
        }
    }

    LaunchedEffect(hatchPhase) {
        if (hatchPhase >= 2) {
            delay(1500)
            AppConfigManager.hasSeenHatching = true
            onComplete()
        }
    }

    val infinite = rememberInfiniteTransition(label = "egg_motion")
    val idleScale by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idle_scale",
    )
    val idleLift by infinite.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idle_lift",
    )
    val crackWiggle by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(90),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crack_wiggle",
    )
    val hintAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hint_alpha",
    )

    val eggSize by animateFloatAsState(
        targetValue = when (hatchPhase) {
            0 -> 212f
            1 -> 228f
            else -> 252f
        },
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "egg_size",
    )
    val openedScale by animateFloatAsState(
        targetValue = if (hatchPhase >= 2) 1.08f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "opened_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "OpenClaw",
                color = SoftWhite,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = typedMessage,
                color = MutedGray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(42.dp))

            AnimatedContent(
                targetState = hatchPhase,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "egg_phase",
            ) { phase ->
                val drawable = when (phase) {
                    0 -> R.drawable.egg_00
                    1 -> R.drawable.egg_01_cracked
                    else -> R.drawable.egg_02_open
                }
                Image(
                    painter = painterResource(drawable),
                    contentDescription = when (phase) {
                        0 -> "Closed egg"
                        1 -> "Cracked egg"
                        else -> "Open egg"
                    },
                    modifier = Modifier
                        .size(eggSize.dp)
                        .scale(if (phase == 0) idleScale else openedScale)
                        .graphicsLayer(
                            translationY = if (phase == 0) idleLift else 0f,
                            rotationZ = if (phase == 1) crackWiggle else 0f,
                        )
                        .clickable(enabled = hatchPhase < 2) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            hatchPhase = (hatchPhase + 1).coerceAtMost(2)
                        },
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = when (hatchPhase) {
                    0 -> "Tap the egg to hatch"
                    1 -> "Tap again"
                    else -> "Hatched"
                },
                color = EmberOrange,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(if (hatchPhase < 2) hintAlpha else 1f),
            )
        }
    }
}
