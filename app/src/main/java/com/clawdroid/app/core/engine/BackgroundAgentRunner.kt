package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.data.db.ToolCallEntity
import java.util.UUID

object BackgroundAgentRunner {
    suspend fun runAgentInBackground(
        context: Context,
        projectId: String?,
        conversationId: String,
        prompt: String
    ): String {
        val db = ClawDroidDatabase.get(context)

        // 1. Insert user message
        db.messages().insert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "user",
                content = prompt,
                createdAt = System.currentTimeMillis(),
                tokenCount = 0
            )
        )

        // 2. Initialize AgentEngine
        val engine = AgentEngine(context, projectId)
        val responseText = StringBuilder()
        val toolCallsList = mutableListOf<ToolCallEntity>()
        val assistantMessageId = UUID.randomUUID().toString()
        var finalResult = ""

        engine.run(prompt).collect { event ->
            when (event) {
                is AgentRunEvent.TextDelta -> {
                    responseText.append(event.text)
                }
                is AgentRunEvent.ToolCallRequested -> {
                    toolCallsList.add(
                        ToolCallEntity(
                            id = event.call.id,
                            messageId = assistantMessageId,
                            toolName = event.call.name,
                            arguments = event.call.arguments,
                            result = "",
                            status = "running",
                            durationMs = 0L
                        )
                    )
                }
                is AgentRunEvent.ToolResultReceived -> {
                    val idx = toolCallsList.indexOfFirst { it.id == event.result.callId }
                    if (idx != -1) {
                        toolCallsList[idx] = toolCallsList[idx].copy(
                            result = event.result.content,
                            status = "completed"
                        )
                    }
                }
                is AgentRunEvent.Completed -> {
                    finalResult = event.finalText
                    // Update assistant message with final result
                    db.messages().insert(
                        MessageEntity(
                            id = assistantMessageId,
                            conversationId = conversationId,
                            role = "assistant",
                            content = event.finalText,
                            createdAt = System.currentTimeMillis(),
                            tokenCount = 0
                        )
                    )
                    // Save all tool calls
                    toolCallsList.forEach { toolCall ->
                        db.toolCalls().upsert(toolCall)
                    }
                }
                is AgentRunEvent.Stopped -> {
                    finalResult = responseText.toString().ifBlank { "Stopped: ${event.reason}" }
                    // Update assistant message with partial text / stopped reason
                    db.messages().insert(
                        MessageEntity(
                            id = assistantMessageId,
                            conversationId = conversationId,
                            role = "assistant",
                            content = finalResult,
                            createdAt = System.currentTimeMillis(),
                            tokenCount = 0
                        )
                    )
                    // Save all tool calls
                    toolCallsList.forEach { toolCall ->
                        db.toolCalls().upsert(toolCall)
                    }
                }
                else -> {}
            }
        }
        return finalResult
    }
}
