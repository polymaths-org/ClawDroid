package com.clawdroid.app.core.engine

import android.content.Context

object BackgroundAgentRunner {
    suspend fun runAgentInBackground(
        context: Context,
        projectId: String?,
        conversationId: String,
        prompt: String
    ): String {
        val engine = AgentEngine(context, projectId)
        val responseText = StringBuilder()
        var finalResult = ""

        engine.run(prompt, targetConversationId = conversationId).collect { event ->
            when (event) {
                is AgentRunEvent.TextDelta -> {
                    responseText.append(event.text)
                }
                is AgentRunEvent.Completed -> {
                    finalResult = event.finalText
                }
                is AgentRunEvent.Stopped -> {
                    finalResult = responseText.toString().ifBlank { "Stopped: ${event.reason}" }
                }
                else -> {}
            }
        }
        return finalResult
    }
}
