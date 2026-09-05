package com.clawdroid.app.core.engine

import com.clawdroid.app.data.api.ChatMessage

data class CompactionDecision(
    val shouldCompact: Boolean,
    val estimatedTokens: Int,
    val limitTokens: Int,
)

object CompactionManager {
    fun shouldCompact(
        messages: List<ChatMessage>,
        limitTokens: Int,
        headroomRatio: Double = 0.20,
    ): CompactionDecision {
        val estimated = TokenEstimator.estimateMessages(messages)
        val threshold = (limitTokens * (1.0 - headroomRatio)).toInt()
        return CompactionDecision(
            shouldCompact = estimated >= threshold,
            estimatedTokens = estimated,
            limitTokens = limitTokens,
        )
    }

    fun compactSummary(existingSummary: String?, recentMessages: List<ChatMessage>): String {
        val recentText = recentMessages.joinToString("\n") { message ->
            "${message.role}: ${message.content.orEmpty().take(1_000)}"
        }
        return buildString {
            if (!existingSummary.isNullOrBlank()) {
                appendLine("Previous summary:")
                appendLine(existingSummary)
                appendLine()
            }
            appendLine("Recent conversation summary source:")
            append(recentText.take(8_000))
        }
    }
}
