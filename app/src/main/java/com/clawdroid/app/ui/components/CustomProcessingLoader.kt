package com.clawdroid.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.AstraPrimary
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.delay

@Composable
fun CustomProcessingLoader(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader_anim")
    var phraseIndex by remember { mutableIntStateOf(0) }
    val phrases = remember {
        listOf(
            "Reading the room before touching the machine" to "ClawDroid is mapping intent, constraints, and next actions",
            "Good agents show their work" to "Planning the smallest useful step before running tools",
            "Measure twice, execute once" to "Checking context so the next action is deliberate",
            "The sandbox is awake" to "Preparing commands, files, and services for the task",
            "Thinking in public, acting with receipts" to "Every action will appear as an inspectable step",
            "Autonomy without opacity" to "Balancing speed with control and reversibility",
            "Following the thread" to "Connecting your request to the current project state",
            "Sharper context, cleaner action" to "Compressing the problem into an executable plan",
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }

    // Smooth continuous rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing glow intensity
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Breathing size of the center dot
    val centerDotRadius by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center_dot"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(
            modifier = Modifier.size(48.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.width / 2 - 3.dp.toPx()

            // 1. Soft radial background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ActivePurple.copy(alpha = 0.2f * glowPulse),
                        AstraPrimary.copy(alpha = 0.05f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius + 8.dp.toPx()
                ),
                radius = outerRadius + 8.dp.toPx()
            )

            // 2. Faint background circular track
            drawCircle(
                color = ActivePurple.copy(alpha = 0.08f),
                radius = outerRadius,
                style = Stroke(width = 3.dp.toPx())
            )

            // 3. Rotating gradient arc spinner (comet tail style)
            rotate(rotationAngle, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ActivePurple.copy(alpha = 0.1f),
                            ActivePurple,
                            ActivePurple.copy(alpha = 0.1f)
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Soft breathing center indicator dot
            drawCircle(
                color = ActivePurple,
                radius = centerDotRadius.dp.toPx(),
                center = center
            )
        }

        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = phrases[phraseIndex].first,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = SoftWhite,
                    letterSpacing = 0.sp
                )
            )
            Text(
                text = phrases[phraseIndex].second,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedGray.copy(alpha = 0.7f)
                )
            )
        }
    }
}
