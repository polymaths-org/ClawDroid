package com.clawdroid.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

enum class ChannelConnectionStatus {
    Connected, Disconnected, Connecting, Error
}

@Composable
fun ChannelStatusCard(
    icon: ImageVector,
    name: String,
    status: ChannelConnectionStatus,
    lastActivity: String = "",
    unreadCount: Int = 0,
    onSettings: () -> Unit = {},
    onToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            ChannelConnectionStatus.Connected -> Color(0xFF4CAF50)
            ChannelConnectionStatus.Connecting -> EmberOrange
            ChannelConnectionStatus.Disconnected -> MutedGray
            ChannelConnectionStatus.Error -> Color(0xFFFF5252)
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "status_color",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_channel")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    val isPulsing = status == ChannelConnectionStatus.Connected || status == ChannelConnectionStatus.Connecting

    val borderColor by animateColorAsState(
        targetValue = when (status) {
            ChannelConnectionStatus.Connected -> Color(0xFF4CAF50).copy(alpha = 0.3f)
            ChannelConnectionStatus.Connecting -> EmberOrange.copy(alpha = 0.3f)
            ChannelConnectionStatus.Error -> Color(0xFFFF5252).copy(alpha = 0.3f)
            ChannelConnectionStatus.Disconnected -> GlassBorderDim
        },
        label = "border_color",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (status == ChannelConnectionStatus.Connected) GlassFillStrong else GlassFill)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = when (status) {
                    ChannelConnectionStatus.Connected -> NeonCyan
                    ChannelConnectionStatus.Error -> Color(0xFFFF5252)
                    else -> MutedGray
                },
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = SoftWhite, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = if (isPulsing) pulseAlpha else 1f)),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (status) {
                            ChannelConnectionStatus.Connected -> "Connected"
                            ChannelConnectionStatus.Connecting -> "Connecting..."
                            ChannelConnectionStatus.Disconnected -> "Disconnected"
                            ChannelConnectionStatus.Error -> "Error"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (lastActivity.isNotBlank()) {
                        Text(" · $lastActivity", color = MutedGray, style = MaterialTheme.typography.bodySmall)
                    }
                    if (unreadCount > 0) {
                        Text(" · ", color = MutedGray, style = MaterialTheme.typography.bodySmall)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmberOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("$unreadCount", color = EmberOrange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MutedGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}
