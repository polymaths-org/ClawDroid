package com.clawdroid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

@Composable
fun PiperDownloadDialog(
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "download_progress",
    )

    val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DeepBlack, RoundedCornerShape(24.dp))
                .border(1.dp, GlassBorderDim, RoundedCornerShape(24.dp))
                .padding(28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Spinning indicator
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (animatedProgress < 1f) FireRed.copy(alpha = 0.2f) else GlassFillStrong,
                            CircleShape,
                        )
                        .border(
                            3.dp,
                            if (animatedProgress < 1f) EmberOrange else GlassFillStrong,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (animatedProgress < 1f) "⚙" else "✓",
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (animatedProgress < 1f) "Downloading Voice Model" else "Ready!",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = if (animatedProgress < 1f) {
                        "Setting up Piper neural TTS engine…"
                    } else {
                        "Piper neural TTS is ready to use."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray,
                    textAlign = TextAlign.Center,
                )

                if (animatedProgress < 1f) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                GlassFill,
                                RoundedCornerShape(3.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    EmberOrange,
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                    }

                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmberOrange,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
