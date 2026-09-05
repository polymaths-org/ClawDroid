package com.clawdroid.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

enum class PresetStatus {
    Idle, Selected, Configured, Error
}

@Composable
fun AnimatedPresetCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: PresetStatus = PresetStatus.Idle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
    cornerRadius: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor by animateColorAsState(
        targetValue = when (status) {
            PresetStatus.Selected -> EmberOrange
            PresetStatus.Configured -> NeonCyan
            PresetStatus.Error -> Color(0xFFFF5252)
            PresetStatus.Idle -> GlassBorderDim
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "border_color",
    )
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            PresetStatus.Selected -> GlassFillStrong
            PresetStatus.Configured -> NeonCyan.copy(alpha = 0.08f)
            else -> GlassFill
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "bg_color",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor, shape)
            .border(1.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when (status) {
                    PresetStatus.Configured -> NeonCyan
                    PresetStatus.Error -> Color(0xFFFF5252)
                    else -> EmberOrange
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                badge()
            }
        }
    }
}

@Composable
fun PresetBadge(text: String, color: Color = NeonCyan) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}
