package com.clawdroid.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
    val alpha = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        alpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }
    Text(
        text = text,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}
