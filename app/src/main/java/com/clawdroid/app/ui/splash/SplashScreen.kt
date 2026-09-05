package com.clawdroid.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.R
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
) {
    // ── Animation values ──────────────────────────────────────
    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    
    // Text cool slide/scale/glow animators
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(40f) }
    val titleScale = remember { Animatable(0.85f) }
    
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(30f) }

    // Glow pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowBlurRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_blur"
    )

    // ── Animation sequence ────────────────────────────────────
    LaunchedEffect(Unit) {
        // Logo appears with overshoot bounce
        launch {
            logoAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1.1f, tween(500, easing = FastOutSlowInEasing))
            logoScale.animateTo(1.0f, tween(250, easing = LinearOutSlowInEasing))
        }

        // Title slides up with overshoot spring feel
        delay(300)
        launch {
            titleAlpha.animateTo(1f, tween(400, easing = EaseOutCubic))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(500, easing = EaseOutBack))
        }
        launch {
            titleScale.animateTo(1.0f, tween(500, easing = EaseOutBack))
        }

        // Subtitle slides up shortly after
        delay(150)
        launch {
            subtitleAlpha.animateTo(1f, tween(450, easing = EaseOutCubic))
        }
        launch {
            subtitleOffsetY.animateTo(0f, tween(550, easing = EaseOutBack))
        }

        // Hold for splash duration then navigate
        delay(1800)
        onSplashComplete()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Static and highly optimized gradient background ─────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = DeepBlack)

            // Dynamic ambient neon cyan/blue background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FireRed.copy(alpha = 0.18f),
                        EmberOrange.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.55f,
                ),
                radius = size.maxDimension,
                center = Offset(size.width * 0.5f, size.height * 0.5f),
            )
        }

        // ── Content ──────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.clawdroid_logo),
                contentDescription = "ClawDroid Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Title - White with dynamic pulsing neon glow shadow and slide-up transition
            Text(
                text = "ClawDroid",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    shadow = Shadow(
                        color = EmberOrange.copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = glowBlurRadius
                    )
                ),
                modifier = Modifier
                    .graphicsLayer(
                        translationY = titleOffsetY.value,
                        scaleX = titleScale.value,
                        scaleY = titleScale.value
                    )
                    .alpha(titleAlpha.value),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle - Sleek subtitle with slide-up transition
            Text(
                text = "Your AI Agent",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MutedGray,
                    fontWeight = FontWeight.Light,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = Offset(0f, 2f),
                        blurRadius = 6f
                    )
                ),
                modifier = Modifier
                    .graphicsLayer(
                        translationY = subtitleOffsetY.value
                    )
                    .alpha(subtitleAlpha.value),
            )
        }
    }
}
