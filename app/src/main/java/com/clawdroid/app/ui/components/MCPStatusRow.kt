package com.clawdroid.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

enum class ServerStatus {
    Running, Stopped, Error, Starting
}

data class MCPServerInfo(
    val name: String,
    val command: String,
    val args: String = "",
    val status: ServerStatus = ServerStatus.Stopped,
    val errorMessage: String = "",
)

@Composable
fun MCPStatusRow(
    server: MCPServerInfo,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onRestart: () -> Unit = {},
    onRemove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(
        targetValue = when (server.status) {
            ServerStatus.Running -> Color(0xFF4CAF50)
            ServerStatus.Starting -> EmberOrange
            ServerStatus.Error -> Color(0xFFFF5252)
            ServerStatus.Stopped -> MutedGray
        },
        label = "status_color",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )
    val isPulsing = server.status == ServerStatus.Running || server.status == ServerStatus.Starting
    val dotAlpha = if (isPulsing) pulseAlpha else 1.0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorderDim, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = dotAlpha))
                        .then(
                            if (isPulsing) Modifier
                            else Modifier
                        ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        server.name,
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${server.command} ${server.args}".trim(),
                        color = MutedGray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (server.status) {
                        ServerStatus.Running, ServerStatus.Starting -> {
                            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                            }
                        }
                        ServerStatus.Stopped, ServerStatus.Error -> {
                            IconButton(onClick = onStart, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    IconButton(onClick = onRestart, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = EmberOrange, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MutedGray, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (server.status == ServerStatus.Error && server.errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    server.errorMessage,
                    color = Color(0xFFFF5252).copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.08f))
                        .padding(8.dp),
                )
            }
        }
    }
}
