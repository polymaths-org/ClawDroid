package com.clawdroid.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import com.clawdroid.app.R
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.AstraPrimary
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

/**
 * Processing state: thinking.gif avatar + status lines + rotating quote.
 * Falls back to the drawn spinner if the GIF can't decode (low memory, etc.).
 */
@Composable
fun CustomProcessingLoader(
    modifier: Modifier = Modifier,
    statusTitle: String = "Thinking…",
    statusSubtitle: String = "Analyzing query and planning actions",
    showQuote: Boolean = true,
) {
    val context = LocalContext.current
    var gifFailed by remember { mutableStateOf(false) }
    val gifLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (gifFailed) {
                CanvasSpinner(modifier = Modifier.size(48.dp))
            } else {
                AsyncImage(
                    model = R.drawable.thinking,
                    contentDescription = "Agent thinking",
                    imageLoader = gifLoader,
                    onError = { gifFailed = true },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = SoftWhite,
                        letterSpacing = 0.5.sp,
                    ),
                )
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MutedGray.copy(alpha = 0.7f),
                    ),
                )
            }
        }

        if (showQuote) {
            QuoteCard()
        }
    }
}

/** Original drawn spinner, kept as the offline fallback. */
@Composable
private fun CanvasSpinner(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader_anim")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    val centerDotRadius by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "center_dot",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = size.width / 2 - 3.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ActivePurple.copy(alpha = 0.2f * glowPulse),
                    AstraPrimary.copy(alpha = 0.05f * glowPulse),
                    Color.Transparent,
                ),
                center = center,
                radius = outerRadius + 8.dp.toPx(),
            ),
            radius = outerRadius + 8.dp.toPx(),
        )

        drawCircle(
            color = ActivePurple.copy(alpha = 0.08f),
            radius = outerRadius,
            style = Stroke(width = 3.dp.toPx()),
        )

        rotate(rotationAngle, center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        ActivePurple.copy(alpha = 0.1f),
                        ActivePurple,
                        ActivePurple.copy(alpha = 0.1f),
                    ),
                    center = center,
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        drawCircle(
            color = ActivePurple,
            radius = centerDotRadius.dp.toPx(),
            center = center,
        )
    }
}
