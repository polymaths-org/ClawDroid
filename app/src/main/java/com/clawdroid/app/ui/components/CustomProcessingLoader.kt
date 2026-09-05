package com.clawdroid.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.clawdroid.app.ui.theme.ElectricBlue
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CustomProcessingLoader(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader_anim")

    // Rotate the gears/claws
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse the glow
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(
            modifier = Modifier.size(56.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.width / 2 - 4.dp.toPx()
            val innerRadius = outerRadius - 10.dp.toPx()

            // 1. Draw Glass/Metal background container with glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.25f * glowPulse),
                        NeonCyan.copy(alpha = 0.05f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius + 8.dp.toPx()
                ),
                radius = outerRadius + 8.dp.toPx()
            )

            // Container Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(ElectricBlue, NeonCyan, ElectricBlue.copy(alpha = 0.2f), ElectricBlue),
                    center = center
                ),
                radius = outerRadius,
                style = Stroke(width = 2.dp.toPx())
            )

            // 2. Draw Rotating Gears/Claws inside
            rotate(rotationAngle, center) {
                // Gear Center
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.4f),
                    radius = 8.dp.toPx()
                )

                // Draw 3 Claws/Teeth curved outwards from the center to inner ring
                val numClaws = 3
                for (i in 0 until numClaws) {
                    val angleRad = Math.toRadians((i * (360 / numClaws)).toDouble()).toFloat()
                    val startX = center.x + 6.dp.toPx() * cos(angleRad)
                    val startY = center.y + 6.dp.toPx() * sin(angleRad)
                    val endX = center.x + innerRadius * cos(angleRad + 0.3f)
                    val endY = center.y + innerRadius * sin(angleRad + 0.3f)

                    // Draw curved claw using line with stroke cap Round
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(NeonCyan, ElectricBlue),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY)
                        ),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Small gear tip at the outer tip
                    drawCircle(
                        color = NeonCyan,
                        radius = 2.dp.toPx(),
                        center = Offset(endX, endY)
                    )
                }
            }

            // Draw counter-rotating inner ring for high-tech premium feel
            rotate(-rotationAngle * 1.5f, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.1f), ElectricBlue, NeonCyan.copy(alpha = 0.1f)),
                        center = center
                    ),
                    radius = innerRadius - 4.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = "Processing…",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = SoftWhite,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = "Autonomous ClawDroid Engine active",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedGray.copy(alpha = 0.8f)
                )
            )
        }
    }
}
