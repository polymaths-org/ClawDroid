package com.clawdroid.app.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    Idle,
    Listening,
    Thinking,
    Speaking,
}

@Composable
fun AudioVisualizerOrb(
    state: OrbState,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    val tertiary = colors.tertiary

    val transition = rememberInfiniteTransition(label = "orb_3d")

    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == OrbState.Idle) 3000 else 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_pulse",
    )

    val rotCW by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == OrbState.Idle) 18000 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb_rot_cw",
    )

    val rotCCW by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == OrbState.Idle) 22000 else 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb_rot_ccw",
    )

    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == OrbState.Idle) 2500 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb_ripple",
    )

    val (colorA, colorB) = when (state) {
        OrbState.Idle -> primary.copy(alpha = 0.4f) to secondary.copy(alpha = 0.2f)
        OrbState.Listening -> primary to tertiary
        OrbState.Thinking -> tertiary to secondary
        OrbState.Speaking -> secondary to tertiary.copy(alpha = 0.7f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val audio = amplitude.coerceIn(0f, 1f)
        val baseRadius = minOf(cx, cy) * 0.44f * pulse * (1f + audio * 0.22f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorA.copy(alpha = 0.18f + audio * 0.18f),
                    colorB.copy(alpha = 0.08f + audio * 0.10f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = baseRadius * 2.2f,
            ),
            radius = baseRadius * 2.2f,
            center = Offset(cx, cy),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    colorA.copy(alpha = 0.82f),
                    colorB.copy(alpha = 0.56f),
                    colors.surface.copy(alpha = 0.88f),
                ),
                center = Offset(cx - baseRadius * 0.22f, cy - baseRadius * 0.24f),
                radius = baseRadius * 1.18f,
            ),
            radius = baseRadius,
            center = Offset(cx, cy),
        )

        rotate(rotCW, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.34f + audio * 0.18f),
                        colorA.copy(alpha = 0.22f),
                        Color.Transparent,
                    ),
                ),
                radius = baseRadius * 0.98f,
                center = Offset(cx, cy),
                alpha = 0.72f,
            )
        }

        rotate(rotCCW, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        colorB.copy(alpha = 0.28f + audio * 0.22f),
                        Color.Transparent,
                        colorA.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
                radius = baseRadius * 1.14f,
                center = Offset(cx, cy),
                alpha = 0.60f,
            )
        }

        drawCircle(
            color = colors.outlineVariant.copy(alpha = 0.26f),
            radius = baseRadius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f + audio * 2f),
        )

        val baseParticleAmp = when (state) {
            OrbState.Idle -> 2f
            OrbState.Listening -> 7f + sin(ripple * PI.toFloat() * 4) * 4f
            OrbState.Thinking -> 5f + sin(ripple * PI.toFloat() * 6) * 3f
            OrbState.Speaking -> 12f + sin(ripple * PI.toFloat() * 8) * 6f
        }
        val particleAmp = baseParticleAmp + audio * 24f
        val tickCount = 56
        for (i in 0 until tickCount) {
            val angle = (i / tickCount.toFloat()) * 360f + rotCW * 0.15f
            val radians = angle * (PI.toFloat() / 180f)
            val wave = (sin(ripple * PI.toFloat() * 2 + i * 0.42f) * 0.5f + 0.5f) * particleAmp
            val startR = baseRadius * 1.10f
            val endR = startR + 8f + wave
            val tickColor = if (i % 2 == 0) colorA else colorB
            drawLine(
                color = tickColor.copy(alpha = (0.22f + audio * 0.48f).coerceIn(0.16f, 0.82f)),
                start = Offset(cx + cos(radians) * startR, cy + sin(radians) * startR),
                end = Offset(cx + cos(radians) * endR, cy + sin(radians) * endR),
                strokeWidth = 1.6f + audio * 2.2f,
            )
        }
    }
}
