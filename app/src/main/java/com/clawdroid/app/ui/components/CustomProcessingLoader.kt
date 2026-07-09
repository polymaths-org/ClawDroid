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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val colors = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "loader_anim")
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
    var quoteOrder by remember { mutableStateOf(shuffledQuoteOrder(quotes.size)) }
    var orderIndex by remember { mutableIntStateOf(0) }
    val quoteIndex = quoteOrder.getOrElse(orderIndex) { 0 }

    LaunchedEffect(quotes.size) {
        while (true) {
            delay(4200)
            val nextIndex = orderIndex + 1
            if (nextIndex < quoteOrder.size) {
                orderIndex = nextIndex
            } else {
                quoteOrder = shuffledQuoteOrder(quotes.size, avoidFirst = quoteIndex)
                orderIndex = 0
            }
        }
    }

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
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
                        colors.surfaceContainerLow.copy(alpha = 0.78f),
                        colors.surfaceContainerHigh.copy(alpha = 0.58f),
                        colors.primary.copy(alpha = 0.06f + 0.04f * glowPulse),
                    )
                )
            )
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.16f + 0.08f * glowPulse),
                            colors.surfaceContainerHighest.copy(alpha = 0.46f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(1.dp, colors.primary.copy(alpha = 0.24f + 0.20f * glowPulse), RoundedCornerShape(16.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FifMascot(
                modifier = Modifier.fillMaxSize(),
                contentDescription = "Thinking mascot",
                animation = MascotAnimation.Thinking,
            )
        }

        AnimatedContent(
            targetState = quoteIndex,
            modifier = Modifier.weight(1f),
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
                        color = colors.primary.copy(alpha = 0.84f),
                        letterSpacing = 0.sp,
                    )
                )
                Box {
                    Text(
                        text = "\"${quote.text}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
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
                                        colors.primary.copy(alpha = 0.10f * glowPulse),
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
                        color = colors.onSurfaceVariant.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Start,
                    )
                )
            }
        }
    }
}

private fun shuffledQuoteOrder(size: Int, avoidFirst: Int? = null): List<Int> {
    if (size <= 0) return emptyList()
    if (size == 1) return listOf(0)
    var order = (0 until size).shuffled()
    if (avoidFirst != null && order.firstOrNull() == avoidFirst) {
        order = order.drop(1) + order.first()
    }
    return order
}
