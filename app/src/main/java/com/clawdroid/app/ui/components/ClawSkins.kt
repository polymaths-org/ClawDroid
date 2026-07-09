package com.clawdroid.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clawdroid.app.R
import com.clawdroid.app.core.config.AppConfigManager
import kotlin.math.max

enum class ClawSkin {
    ClawMagic,
    Material,
    Minimalist,
    LiquidGlass,
    Cyberpunk,
    Jarvis,
}

@Composable
fun currentClawSkin(): ClawSkin {
    val themeKey by AppConfigManager.appThemeFlow.collectAsState()
    return remember(themeKey) {
        when (themeKey) {
            "claw_magic" -> ClawSkin.ClawMagic
            "minimalist" -> ClawSkin.Minimalist
            "liquid_glass_light", "liquid_glass_dark" -> ClawSkin.LiquidGlass
            "cyberpunk" -> ClawSkin.Cyberpunk
            "jarvis" -> ClawSkin.Jarvis
            "dark", "light" -> ClawSkin.Material
            else -> ClawSkin.ClawMagic
        }
    }
}

fun ClawSkin.isHud(): Boolean = this == ClawSkin.Cyberpunk || this == ClawSkin.Jarvis

@Composable
fun ClawSkinBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = currentClawSkin()
    val colors = MaterialTheme.colorScheme
    val backgroundBrush = when (skin) {
        ClawSkin.ClawMagic -> Brush.verticalGradient(listOf(colors.background, colors.background))
        ClawSkin.LiquidGlass -> Brush.linearGradient(
            colors = listOf(
                colors.background.copy(alpha = 0.85f),
                colors.surfaceContainerLowest.copy(alpha = 0.80f),
                colors.surfaceContainerHigh.copy(alpha = 0.60f),
            ),
            start = Offset.Zero,
            end = Offset(1200f, 1800f),
        )
        ClawSkin.Cyberpunk -> Brush.verticalGradient(
            listOf(
                Color(0xFF04010A),
                Color(0xFF080314),
                Color(0xFF020106),
            ),
        )
        ClawSkin.Jarvis -> Brush.verticalGradient(
            listOf(
                Color(0xFF01080B),
                Color(0xFF031116),
                Color(0xFF010507),
            ),
        )
        ClawSkin.Minimalist -> Brush.verticalGradient(
            listOf(colors.background, colors.surfaceContainerLowest),
        )
        ClawSkin.Material -> Brush.verticalGradient(
            listOf(colors.background, colors.background),
        )
    }
    val configuration = LocalConfiguration.current

    Box(modifier = modifier.fillMaxSize()) {
        if (skin == ClawSkin.LiquidGlass) {
            val isDark = configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            val bgRes = if (isDark) R.drawable.bg_dark else R.drawable.bg_light
            Image(
                painter = painterResource(bgRes),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundBrush)
        )
        when (skin) {
            ClawSkin.ClawMagic -> ClawMagicAmbient(Modifier.matchParentSize())
            ClawSkin.LiquidGlass -> LiquidGlassAmbient(Modifier.matchParentSize())
            ClawSkin.Cyberpunk, ClawSkin.Jarvis -> HudAmbient(skin = skin, modifier = Modifier.matchParentSize())
            else -> Unit
        }
        content()
    }
}

@Composable
fun ClawPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    emphasis: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = currentClawSkin()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (skin.isHud()) max(8f, cornerRadius.value - 8f).dp else cornerRadius)
    val panelBrush = when (skin) {
        ClawSkin.ClawMagic -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.030f + emphasis * 0.018f),
                Color.White.copy(alpha = 0.024f + emphasis * 0.014f),
            ),
        )
        ClawSkin.LiquidGlass -> Brush.verticalGradient(
            listOf(
                colors.surfaceContainerLowest.copy(alpha = 0.86f + emphasis * 0.08f),
                colors.surface.copy(alpha = 0.68f + emphasis * 0.10f),
                colors.surfaceContainerHigh.copy(alpha = 0.54f + emphasis * 0.10f),
            ),
        )
        ClawSkin.Cyberpunk -> Brush.linearGradient(
            listOf(
                colors.surfaceContainerLowest.copy(alpha = 0.90f),
                colors.surface.copy(alpha = 0.82f),
                Color(0xFF15081D).copy(alpha = 0.76f),
            ),
            start = Offset.Zero,
            end = Offset(900f, 300f),
        )
        ClawSkin.Jarvis -> Brush.linearGradient(
            listOf(
                colors.surfaceContainerLowest.copy(alpha = 0.88f),
                colors.surface.copy(alpha = 0.76f),
                Color(0xFF041E24).copy(alpha = 0.66f),
            ),
            start = Offset.Zero,
            end = Offset(800f, 320f),
        )
        ClawSkin.Minimalist -> Brush.verticalGradient(
            listOf(
                colors.surfaceContainerLow.copy(alpha = 0.86f),
                colors.surfaceContainer.copy(alpha = 0.86f),
            ),
        )
        ClawSkin.Material -> Brush.verticalGradient(
            listOf(
                colors.surfaceContainerLow.copy(alpha = 0.82f),
                colors.surfaceContainer.copy(alpha = 0.82f),
            ),
        )
    }
    val borderBrush = when (skin) {
        ClawSkin.ClawMagic -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.070f),
                colors.primary.copy(alpha = 0.08f + emphasis * 0.08f),
                Color.White.copy(alpha = 0.050f),
            ),
        )
        ClawSkin.Cyberpunk -> Brush.horizontalGradient(
            listOf(
                Color(0xFF00E5FF).copy(alpha = 0.92f),
                Color(0xFFFF3FD7).copy(alpha = 0.88f),
                Color(0xFF7C4DFF).copy(alpha = 0.82f),
            ),
        )
        ClawSkin.Jarvis -> Brush.horizontalGradient(
            listOf(
                Color(0xFF12D8FF).copy(alpha = 0.94f),
                Color(0xFF92F4FF).copy(alpha = 0.72f),
                Color(0xFF00A8D6).copy(alpha = 0.88f),
            ),
        )
        ClawSkin.LiquidGlass -> Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.62f),
                colors.primary.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.18f),
            ),
        )
        else -> Brush.verticalGradient(
            listOf(
                colors.outlineVariant.copy(alpha = 0.68f),
                colors.outlineVariant.copy(alpha = 0.38f),
            ),
        )
    }
    val glow = when (skin) {
        ClawSkin.ClawMagic -> colors.primary.copy(alpha = 0.08f + emphasis * 0.04f)
        ClawSkin.Cyberpunk -> colors.secondary.copy(alpha = 0.30f)
        ClawSkin.Jarvis -> colors.primary.copy(alpha = 0.28f)
        ClawSkin.LiquidGlass -> Color.White.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = 0.18f)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = when (skin) {
                    ClawSkin.ClawMagic -> if (emphasis > 0.25f) 4.dp else 0.dp
                    ClawSkin.Cyberpunk, ClawSkin.Jarvis -> 14.dp
                    ClawSkin.LiquidGlass -> 10.dp
                    else -> 3.dp
                },
                shape = shape,
                ambientColor = glow,
                spotColor = glow,
            )
            .clip(shape)
            .background(panelBrush, shape)
            .border(1.dp, borderBrush, shape),
    ) {
        if (skin.isHud()) {
            HudCornerOverlay(
                skin = skin,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ClawInputPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = currentClawSkin()
    ClawPanel(
        modifier = modifier,
        cornerRadius = if (skin.isHud()) 14.dp else if (skin == ClawSkin.ClawMagic) 18.dp else 28.dp,
        contentPadding = PaddingValues(0.dp),
        emphasis = 0.35f,
        content = content,
    )
}

@Composable
fun ClawMagicMark(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "claw_magic_mark")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "claw_magic_mark_rotation",
    )
    Canvas(modifier = modifier.size(42.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val long = size.minDimension * 0.42f
        val short = size.minDimension * 0.18f
        val stroke = size.minDimension * 0.085f
        rotate(rotation, pivot = center) {
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                val startRadius = if (i % 2 == 0) short * 0.35f else short * 0.65f
                val endRadius = if (i % 2 == 0) long else long * 0.70f
                val color = when (i % 4) {
                    0 -> colors.primary
                    1 -> colors.tertiary
                    2 -> Color(0xFF79F2D0)
                    else -> Color(0xFFFF7B91)
                }
                drawLine(
                    color = color.copy(alpha = if (i % 2 == 0) 0.94f else 0.64f),
                    start = Offset(
                        x = center.x + kotlin.math.cos(angle).toFloat() * startRadius,
                        y = center.y + kotlin.math.sin(angle).toFloat() * startRadius,
                    ),
                    end = Offset(
                        x = center.x + kotlin.math.cos(angle).toFloat() * endRadius,
                        y = center.y + kotlin.math.sin(angle).toFloat() * endRadius,
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ClawMagicAmbient(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "claw_magic_ambient")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "claw_magic_pulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    primary.copy(alpha = 0.035f * pulse),
                ),
                startY = size.height * 0.76f,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height * 0.70f),
            size = Size(size.width, size.height * 0.30f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.035f * pulse),
                    tertiary.copy(alpha = 0.018f * pulse),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.50f, size.height * 0.22f),
                radius = size.width * 0.80f,
            ),
            radius = size.width * 0.80f,
            center = Offset(size.width * 0.50f, size.height * 0.22f),
        )
    }
}

@Composable
private fun LiquidGlassAmbient(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liquid_ambient")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liquid_shift",
    )
    Canvas(modifier = modifier) {
        val bandWidth = size.width * 0.38f
        val startX = -bandWidth + shift * (size.width + bandWidth * 2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.055f),
                    Color.Transparent,
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + bandWidth, size.height),
            ),
            size = size,
        )
    }
}

@Composable
private fun HudAmbient(
    skin: ClawSkin,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "hud_ambient")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (skin == ClawSkin.Cyberpunk) 5200 else 6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hud_scan",
    )
    val primary = if (skin == ClawSkin.Cyberpunk) Color(0xFF00E5FF) else Color(0xFF18E7FF)
    val secondary = if (skin == ClawSkin.Cyberpunk) Color(0xFFFF3FD7) else Color(0xFF80F6FF)
    Canvas(modifier = modifier) {
        val grid = if (skin == ClawSkin.Cyberpunk) 28.dp.toPx() else 34.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = primary.copy(alpha = if (skin == ClawSkin.Cyberpunk) 0.055f else 0.045f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
            x += grid
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = secondary.copy(alpha = if (skin == ClawSkin.Cyberpunk) 0.045f else 0.035f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.8.dp.toPx(),
            )
            y += grid
        }

        val scanY = scan * size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    primary.copy(alpha = 0.09f),
                    Color.Transparent,
                ),
                startY = scanY - 88.dp.toPx(),
                endY = scanY + 88.dp.toPx(),
            ),
            topLeft = Offset(0f, scanY - 88.dp.toPx()),
            size = Size(size.width, 176.dp.toPx()),
        )

        val frameStroke = 1.2.dp.toPx()
        val inset = 10.dp.toPx()
        val segment = 72.dp.toPx()
        drawLine(primary.copy(alpha = 0.42f), Offset(inset, inset), Offset(inset + segment, inset), strokeWidth = frameStroke)
        drawLine(primary.copy(alpha = 0.42f), Offset(inset, inset), Offset(inset, inset + segment), strokeWidth = frameStroke)
        drawLine(secondary.copy(alpha = 0.42f), Offset(size.width - inset - segment, inset), Offset(size.width - inset, inset), strokeWidth = frameStroke)
        drawLine(secondary.copy(alpha = 0.42f), Offset(size.width - inset, inset), Offset(size.width - inset, inset + segment), strokeWidth = frameStroke)
        drawLine(primary.copy(alpha = 0.30f), Offset(inset, size.height - inset), Offset(inset + segment, size.height - inset), strokeWidth = frameStroke)
        drawLine(primary.copy(alpha = 0.30f), Offset(inset, size.height - inset - segment), Offset(inset, size.height - inset), strokeWidth = frameStroke)
        drawLine(secondary.copy(alpha = 0.30f), Offset(size.width - inset - segment, size.height - inset), Offset(size.width - inset, size.height - inset), strokeWidth = frameStroke)
        drawLine(secondary.copy(alpha = 0.30f), Offset(size.width - inset, size.height - inset - segment), Offset(size.width - inset, size.height - inset), strokeWidth = frameStroke)
    }
}

@Composable
private fun HudCornerOverlay(
    skin: ClawSkin,
    modifier: Modifier = Modifier,
) {
    val primary = if (skin == ClawSkin.Cyberpunk) Color(0xFF00E5FF) else Color(0xFF18E7FF)
    val secondary = if (skin == ClawSkin.Cyberpunk) Color(0xFFFF3FD7) else Color(0xFF80F6FF)
    Canvas(modifier = modifier) {
        val corner = 22.dp.toPx()
        val stroke = 1.4.dp.toPx()
        drawLine(primary.copy(alpha = 0.86f), Offset(0f, corner), Offset(corner, 0f), strokeWidth = stroke)
        drawLine(secondary.copy(alpha = 0.72f), Offset(size.width - corner, 0f), Offset(size.width, corner), strokeWidth = stroke)
        drawLine(primary.copy(alpha = 0.62f), Offset(0f, size.height - corner), Offset(corner, size.height), strokeWidth = stroke)
        drawLine(secondary.copy(alpha = 0.78f), Offset(size.width - corner, size.height), Offset(size.width, size.height - corner), strokeWidth = stroke)
    }
}
