package com.clawdroid.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.AstraPrimary
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private data class ProcessingQuote(
    val text: String,
    val author: String,
)

@Composable
fun CustomProcessingLoader(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader_anim")
    var quoteIndex by remember { mutableIntStateOf(0) }
    val quotes = remember {
        listOf(
            ProcessingQuote("Privacy is the Price you pay to Agents", "Paris K."),
            ProcessingQuote("Stay hungry. Stay foolish.", "Steve Jobs"),
            ProcessingQuote("It's Day 1.", "Jeff Bezos"),
            ProcessingQuote("Our industry does not respect tradition. What it respects is innovation.", "Satya Nadella"),
            ProcessingQuote("The best way to predict the future is to invent it.", "Alan Kay"),
            ProcessingQuote("Make every detail perfect and limit the number of details to perfect.", "Jack Dorsey"),
            ProcessingQuote("If you are changing the world, you are working on important things.", "Larry Page"),
            ProcessingQuote("Great companies are built on great products.", "Elon Musk"),
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4200)
            quoteIndex = (quoteIndex + 1) % quotes.size
        }
    }

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val centerDotRadius by infiniteTransition.animateFloat(
        initialValue = 2.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(940, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "center_dot"
    )

    val sheenOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "quote_sheen"
    )

    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        GlassFill,
                        ActivePurple.copy(alpha = 0.08f + 0.05f * glowPulse),
                        AstraPrimary.copy(alpha = 0.06f),
                    )
                )
            )
            .border(1.dp, GlassBorderDim.copy(alpha = 0.9f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Canvas(
            modifier = Modifier.size(56.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.width / 2 - 4.dp.toPx()
            val innerRadius = outerRadius * 0.62f
            val orbitRadius = outerRadius * 0.78f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ActivePurple.copy(alpha = 0.22f * glowPulse),
                        AstraPrimary.copy(alpha = 0.08f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius + 10.dp.toPx()
                ),
                radius = outerRadius + 10.dp.toPx()
            )

            drawCircle(
                color = ActivePurple.copy(alpha = 0.10f),
                radius = outerRadius,
                style = Stroke(width = 2.dp.toPx())
            )

            rotate(rotationAngle, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            ActivePurple,
                            AstraPrimary,
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = -30f,
                    sweepAngle = 240f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            rotate(-rotationAngle * 0.72f, center) {
                drawArc(
                    color = AstraPrimary.copy(alpha = 0.66f),
                    startAngle = 120f,
                    sweepAngle = 92f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = ActivePurple.copy(alpha = 0.95f),
                radius = centerDotRadius.dp.toPx(),
                center = center
            )

            repeat(3) { index ->
                val angle = Math.toRadians((rotationAngle + index * 120f).toDouble())
                drawCircle(
                    color = AstraPrimary.copy(alpha = 0.32f + index * 0.16f),
                    radius = (2.2f + index * 0.45f).dp.toPx(),
                    center = Offset(
                        x = center.x + cos(angle).toFloat() * orbitRadius,
                        y = center.y + sin(angle).toFloat() * orbitRadius,
                    )
                )
            }
        }

        AnimatedContent(
            targetState = quoteIndex,
            transitionSpec = {
                (
                    slideInVertically(
                        animationSpec = tween(520, easing = FastOutSlowInEasing),
                        initialOffsetY = { height -> height / 3 },
                    ) + fadeIn(tween(420)) + scaleIn(
                        animationSpec = tween(520, easing = FastOutSlowInEasing),
                        initialScale = 0.96f,
                    )
                ).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        targetOffsetY = { height -> -height / 3 },
                    ) + fadeOut(tween(220)) + scaleOut(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        targetScale = 0.98f,
                    )
                ).using(SizeTransform(clip = false))
            },
            label = "processing_quote_swap",
        ) { index ->
            val quote = quotes[index]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "PROCESSING REQUEST",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ActivePurple.copy(alpha = 0.84f),
                        letterSpacing = 0.sp,
                    )
                )
                Box {
                    Text(
                        text = "\"${quote.text}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = SoftWhite,
                            lineHeight = 20.sp,
                            letterSpacing = 0.sp,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        AstraPrimary.copy(alpha = 0.12f * glowPulse),
                                        Color.Transparent,
                                    ),
                                    start = Offset(160f * sheenOffset, 0f),
                                    end = Offset(160f * sheenOffset + 80f, 90f),
                                )
                            )
                    )
                }
                Text(
                    text = quote.author,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MutedGray.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Start,
                    )
                )
            }
        }
    }
}
