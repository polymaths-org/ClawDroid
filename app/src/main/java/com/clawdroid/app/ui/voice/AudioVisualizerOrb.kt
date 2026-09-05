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

    val breathe by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_breathe",
    )

    val rotTertiary by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb_rot_3",
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
        val audioScale = 1.0f + amplitude * 0.35f
        val baseRadius = minOf(cx, cy) * 0.42f * pulse * audioScale

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorA.copy(alpha = 0.06f),
                    colorB.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseRadius * 2.8f * breathe,
            ),
            radius = baseRadius * 2.8f * breathe,
            center = Offset(cx, cy),
        )

        val glowRadius = baseRadius * (2.0f + amplitude * 0.5f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorA.copy(alpha = (0.25f + amplitude * 0.15f).coerceIn(0f, 0.6f)),
                    colorB.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(cx, cy),
        )

        if (state != OrbState.Idle) {
            for (i in 0..1) {
                val phase = (ripple + i * 0.5f) % 1f
                val rr = baseRadius * (1f + phase * 0.6f + amplitude * 0.25f)
                val alpha = ((1f - phase) * 0.35f).coerceIn(0f, 0.35f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colorB.copy(alpha = alpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = rr,
                    ),
                    radius = rr,
                    center = Offset(cx, cy),
                )
            }
        }

        rotate(rotCW, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        colorA.copy(alpha = 0.55f),
                        colorB.copy(alpha = 0.10f),
                        Color.Transparent,
                        colorA.copy(alpha = 0.55f),
                    ),
                ),
                radius = baseRadius * 1.3f,
                center = Offset(cx, cy),
                alpha = 0.30f,
            )
        }

        rotate(rotCCW, pivot = Offset(cx, cy)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        colorB.copy(alpha = 0.45f),
                        Color.Transparent,
                        colorA.copy(alpha = 0.35f),
                        Color.Transparent,
                        colorB.copy(alpha = 0.45f),
                    ),
                ),
                radius = baseRadius * 1.1f,
                center = Offset(cx, cy),
                alpha = 0.35f,
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.3f)
                ),
                center = Offset(cx + baseRadius * 0.15f, cy + baseRadius * 0.2f),
                radius = baseRadius * 0.9f,
            ),
            radius = baseRadius,
            center = Offset(cx, cy),
            alpha = 0.5f,
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    colorA.copy(alpha = 0.95f),
                    colorB.copy(alpha = 0.7f),
                    colorA.copy(alpha = 0.3f),
                    Color.Transparent
                ),
                center = Offset(cx - baseRadius * 0.15f, cy - baseRadius * 0.15f),
                radius = baseRadius * 1.1f,
            ),
            radius = baseRadius,
            center = Offset(cx, cy),
            alpha = 0.90f,
        )

        val specRadius = baseRadius * 0.35f
        val specCx = cx - baseRadius * 0.28f
        val specCy = cy - baseRadius * 0.30f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.75f),
                    Color.White.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = Offset(specCx, specCy),
                radius = specRadius,
            ),
            radius = specRadius,
            center = Offset(specCx, specCy),
        )

        val spec2R = baseRadius * 0.12f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    Color.Transparent
                ),
                center = Offset(specCx + spec2R * 0.5f, specCy + spec2R * 0.5f),
                radius = spec2R,
            ),
            radius = spec2R,
            center = Offset(specCx + spec2R * 0.5f, specCy + spec2R * 0.5f),
        )

        val baseParticleAmp = when (state) {
            OrbState.Idle -> 2f
            OrbState.Listening -> 8f + sin(ripple * PI.toFloat() * 4) * 4f
            OrbState.Thinking -> 6f + sin(ripple * PI.toFloat() * 6) * 3f
            OrbState.Speaking -> 14f + sin(ripple * PI.toFloat() * 8) * 7f
        }
        val particleAmp = baseParticleAmp + amplitude * 30f

        val innerCount = 28
        for (i in 0 until innerCount) {
            val angle = (i / innerCount.toFloat()) * 360f + rotCW * 0.6f
            val radians = angle * (PI.toFloat() / 180f)
            val wave = sin(ripple * PI.toFloat() * 2 + i * 0.5f) * particleAmp
            val r = baseRadius * 0.92f + wave
            val x = cx + cos(radians) * r
            val y = cy + sin(radians) * r
            val a = (0.35f + wave / 28f).coerceIn(0.08f, 0.75f)
            drawCircle(
                color = colorA.copy(alpha = a),
                radius = 2.5f + (wave * 0.08f).coerceAtLeast(0f),
                center = Offset(x, y),
            )
        }

        val midCount = 22
        for (i in 0 until midCount) {
            val angle = (i / midCount.toFloat()) * 360f + rotCCW * 0.4f + 30f
            val radians = angle * (PI.toFloat() / 180f)
            val wave = cos(ripple * PI.toFloat() * 3 + i * 0.7f) * (particleAmp * 0.8f)
            val r = baseRadius * 1.15f + wave
            val x = cx + cos(radians) * r
            val y = cy + sin(radians) * r
            val a = (0.25f + wave / 22f).coerceIn(0.05f, 0.6f)
            drawCircle(
                color = colorB.copy(alpha = a),
                radius = 2f + (wave * 0.06f).coerceAtLeast(0f),
                center = Offset(x, y),
            )
        }

        val outerCount = 32
        for (i in 0 until outerCount) {
            val angle = (i / outerCount.toFloat()) * 360f + rotTertiary * 0.35f + 60f
            val radians = angle * (PI.toFloat() / 180f)
            val wave = sin(ripple * PI.toFloat() * 2.5f + i * 0.3f) * (particleAmp * 1.2f)
            val r = baseRadius * 1.42f + wave
            val x = cx + cos(radians) * r
            val y = cy + sin(radians) * r
            val a = (0.18f + wave / 35f).coerceIn(0.03f, 0.55f)
            drawCircle(
                color = colorA.copy(alpha = a * 0.6f),
                radius = 1.8f + (wave * 0.05f).coerceAtLeast(0f),
                center = Offset(x, y),
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorB.copy(alpha = 0.30f),
                    Color.Transparent
                ),
                center = Offset(cx + baseRadius * 0.35f, cy + baseRadius * 0.35f),
                radius = baseRadius * 0.6f,
            ),
            radius = baseRadius * 0.6f,
            center = Offset(cx + baseRadius * 0.35f, cy + baseRadius * 0.35f),
        )

        if (state != OrbState.Idle) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(cx - baseRadius * 0.2f, cy - baseRadius * 0.2f),
                    radius = baseRadius * 0.45f,
                ),
                radius = baseRadius * 0.45f,
                center = Offset(cx - baseRadius * 0.2f, cy - baseRadius * 0.2f),
            )
        }
    }
}
