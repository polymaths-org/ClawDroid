package com.clawdroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.delay

/**
 * Rotating quotes shown while the agent is working.
 * Short, professional, calm — no hype, no emoji.
 */
object AgentQuotes {
    val quotes: List<Pair<String, String>> = listOf(
        "Simplicity is the soul of efficiency." to "Austin Freeman",
        "The best way out is always through." to "Robert Frost",
        "Action is the foundational key to all success." to "Pablo Picasso",
        "Well begun is half done." to "Aristotle",
        "What we think, we become." to "Buddha",
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "It always seems impossible until it's done." to "Nelson Mandela",
        "Quality is not an act, it is a habit." to "Aristotle",
        "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
        "Stay hungry, stay foolish." to "Steve Jobs",
        "The future depends on what you do today." to "Mahatma Gandhi",
        "Done is better than perfect." to "Sheryl Sandberg",
    )
}

/**
 * Small quote card for the processing state. Rotates through [AgentQuotes]
 * every [rotateMs] so long waits never feel frozen.
 */
@Composable
fun QuoteCard(
    modifier: Modifier = Modifier,
    rotateMs: Long = 6_000L,
) {
    var index by remember { mutableIntStateOf((AgentQuotes.quotes.indices).random()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(rotateMs)
            index = (index + 1) % AgentQuotes.quotes.size
        }
    }

    val (text, author) = AgentQuotes.quotes[index]
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill, shape)
            .border(1.dp, GlassBorderDim, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "“",
            color = MutedGray.copy(alpha = 0.7f),
            fontSize = 22.sp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                color = SoftWhite.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            )
            Text(
                text = "— $author",
                color = MutedGray.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
