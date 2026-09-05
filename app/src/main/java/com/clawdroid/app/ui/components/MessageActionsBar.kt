package com.clawdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Message action bar with copy, read aloud, and rethink buttons
 * Appears on hover/long-press of message bubbles
 */
@Composable
fun MessageActionsBar(
    messageText: String,
    onReadAloud: (() -> Unit)?,
    onRethink: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isAlreadyReading: Boolean = false,
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Copy button
        ActionIconButton(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = "Copy",
            onClick = {
                clipboardManager.setText(AnnotatedString(messageText))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        // Read aloud button
        if (onReadAloud != null) {
            ActionIconButton(
                icon = Icons.Rounded.VolumeUp,
                contentDescription = if (isAlreadyReading) "Stop reading" else "Read aloud",
                onClick = {
                    onReadAloud()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                isActive = isAlreadyReading
            )
        }

        // Rethink / regenerate button
        if (onRethink != null) {
            ActionIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Regenerate",
                onClick = {
                    onRethink()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
        }
    }
}

/**
 * Individual action icon button with hover effect
 */
@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(
                color = if (isActive)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .alpha(if (isActive) 1f else 0.7f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (isActive)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
