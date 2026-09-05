package com.clawdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

data class TranscriptionEntry(
    val speaker: Speaker,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class Speaker {
    USER, AGENT
}

/**
 * Shows transcription of both user and agent speech in a conversational format
 * Used in voice chat to display what was said and what the agent is replying with
 */
@Composable
fun TranscriptionPanel(
    entries: List<TranscriptionEntry>,
    onDismiss: () -> Unit = {},
    showDismissButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorderDim, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with title and dismiss button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transcription",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmberOrange,
                    modifier = Modifier.weight(1f)
                )
                if (showDismissButton) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.width(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            tint = MutedGray,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable transcript entries
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                entries.forEach { entry ->
                    TranscriptionEntryView(entry)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * Single transcription entry showing who spoke and what they said
 */
@Composable
private fun TranscriptionEntryView(entry: TranscriptionEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Speaker label with timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (entry.speaker) {
                    Speaker.USER -> "You said:"
                    Speaker.AGENT -> "Agent replying:"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (entry.speaker) {
                    Speaker.USER -> Color(0xFF42A5F5)  // Light blue for user
                    Speaker.AGENT -> EmberOrange  // Orange for agent
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                formatTime(entry.timestamp),
                fontSize = 9.sp,
                fontStyle = FontStyle.Italic,
                color = MutedGray
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Transcribed text in a mini card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (entry.speaker) {
                        Speaker.USER -> Color(0xFF1A2332)  // Dark blue-ish
                        Speaker.AGENT -> Color(0xFF2A2020)  // Dark orange-ish
                    }
                )
                .padding(8.dp)
        ) {
            Text(
                text = entry.text,
                fontSize = 12.sp,
                color = SoftWhite,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Format timestamp for display (e.g., "2s ago", "1m ago")
 */
private fun formatTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < 1000 -> "now"
        elapsed < 60_000 -> "${elapsed / 1000}s"
        elapsed < 3_600_000 -> "${elapsed / 60_000}m"
        else -> "${elapsed / 3_600_000}h"
    }
}

/**
 * Compact version of transcription panel (single line per entry)
 */
@Composable
fun CompactTranscriptionPanel(
    entries: List<TranscriptionEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GlassFill)
            .border(1.dp, GlassBorderDim, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        val recentEntries = entries.takeLast(3)  // Show last 3 entries max

        recentEntries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Speaker indicator dot
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .width(6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (entry.speaker) {
                                Speaker.USER -> Color(0xFF42A5F5)
                                Speaker.AGENT -> EmberOrange
                            }
                        )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = entry.text,
                    fontSize = 11.sp,
                    color = SoftWhite,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }

            if (entry != recentEntries.last()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Dialog-style transcription panel that overlays the screen
 */
@Composable
fun FullscreenTranscriptionPanel(
    entries: List<TranscriptionEntry>,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    Box(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .background(DeepBlack)
            .border(2.dp, EmberOrange, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Voice Transcription",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmberOrange,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, "Close", tint = MutedGray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Entries
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                entries.forEach { entry ->
                    TranscriptionEntryView(entry)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

