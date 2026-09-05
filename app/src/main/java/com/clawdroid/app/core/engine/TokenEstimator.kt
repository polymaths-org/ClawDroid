package com.clawdroid.app.core.engine

import com.clawdroid.app.data.api.ChatMessage

object TokenEstimator {
    fun estimate(text: String): Int = (text.length / 4).coerceAtLeast(1)

    fun estimateMessages(messages: List<ChatMessage>): Int = messages.sumOf { message ->
        estimate(message.role) + estimate(message.content.orEmpty()) +
            message.toolCalls.sumOf { call -> estimate(call.name) + estimate(call.arguments) }
    }
}
