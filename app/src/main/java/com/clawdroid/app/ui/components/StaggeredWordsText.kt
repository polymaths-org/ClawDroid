package com.clawdroid.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaggeredWordsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Center,
    delayStepMs: Long = 48L,
) {
    val words = remember(text) { text.split(Regex("\\s+")).filter { it.isNotBlank() } }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        words.forEachIndexed { index, word ->
            AnimatedWord(
                word = if (index == words.lastIndex) word else "$word ",
                color = color,
                style = style,
                fontWeight = fontWeight,
                textAlign = textAlign,
                delayMs = index * delayStepMs,
            )
        }
    }
}

@Composable
private fun AnimatedWord(
    word: String,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight?,
    textAlign: TextAlign,
    delayMs: Long,
) {
    val alpha = remember(word) { Animatable(0f) }
    val offsetY = remember(word) { Animatable(18f) }
    val scale = remember(word) { Animatable(0.96f) }

    LaunchedEffect(word) {
        delay(delayMs)
        launch { alpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, tween(520, easing = FastOutSlowInEasing)) }
        scale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }

    Text(
        text = word,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
