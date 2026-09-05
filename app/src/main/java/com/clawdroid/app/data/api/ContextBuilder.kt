package com.clawdroid.app.data.api

import com.clawdroid.app.data.db.ConversationDao
import com.clawdroid.app.data.db.MessageDao
import com.clawdroid.app.data.db.MessageEntity
import com.clawdroid.app.data.db.ToolCallDao
import com.clawdroid.app.data.db.ToolCallEntity
import java.util.UUID

/**
 * Builds the list of [ChatMessage] to send to the LLM for a given conversation.
 *
 * Context structure:
 * ```
 * [System prompt]
 * [Compaction summary as "user" message]  ← if conversation.summaryMessageId is set
 * [Messages after summary point]          ← from Room DB
 * ```
 */
import android.content.Context

const val INTERNAL_USER_PROMPT_PREFIX = "[[CLAWDROID_INTERNAL_USER_PROMPT]]\n"

fun internalUserPrompt(content: String): String = INTERNAL_USER_PROMPT_PREFIX + content

private fun String.stripInternalUserPromptMarker(): String {
    return if (startsWith(INTERNAL_USER_PROMPT_PREFIX)) {
        removePrefix(INTERNAL_USER_PROMPT_PREFIX).trimStart()
    } else {
        this
    }
}

class ContextBuilder(
    private val context: Context,
    private val projectId: String?,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val toolCallDao: ToolCallDao,
) {
    /**
     * Build the full message list for an LLM call.
     *
     * @param conversationId the conversation to build context for
     * @param includeSystemPrompt whether to prepend the system prompt (true for normal calls, false for compaction)
     */
    suspend fun buildContext(
        conversationId: String,
        includeSystemPrompt: Boolean = true,
    ): List<ChatMessage> {
        val conversation = conversationDao.getById(conversationId)
            ?: error("Conversation $conversationId not found")

        val allMessages = messageDao.getAll(conversationId)

        val result = mutableListOf<ChatMessage>()

        // 1. System prompt
        if (includeSystemPrompt) {
            result += ChatMessage(role = "system", content = buildSystemPrompt())
        }

        // 2. Determine start index based on compaction
        val startIndex = if (conversation.summaryMessageId != null) {
            val summaryIdx = allMessages.indexOfFirst { it.id == conversation.summaryMessageId }
            if (summaryIdx >= 0) {
                // Inject summary as a "user" message (like OpenCode does)
                val summaryMsg = allMessages[summaryIdx]
                result += ChatMessage(
                    role = "user",
                    content = "Previous conversation summary:\n${summaryMsg.content}",
                )
                summaryIdx + 1 // Skip the summary message itself
            } else {
                0 // Summary message not found, use all messages
            }
        } else {
            0
        }

        // 3. Convert remaining messages to ChatMessage format
        for (i in startIndex until allMessages.size) {
            val msg = allMessages[i]
            val toolCalls = if (msg.role == "assistant") {
                toolCallDao.getForMessage(msg.id).map { entity ->
                    CompletedToolCall(
                        id = entity.id,
                        name = entity.toolName,
                        arguments = entity.arguments
                    )
                }
            } else {
                emptyList()
            }
            result += msg.toChatMessage(toolCalls)
        }

        return result
    }

    /**
     * Save a user message to the database and return its ID.
     */
    suspend fun saveUserMessage(
        conversationId: String,
        content: String,
        mediaPath: String? = null,
        mediaMimeType: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        messageDao.insert(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = "user",
                content = content,
                createdAt = System.currentTimeMillis(),
                tokenCount = (content.length / 4).coerceAtLeast(1),
                mediaPath = mediaPath,
                mediaMimeType = mediaMimeType,
            )
        )
        return id
    }

    /**
     * Save an assistant message (possibly with tool calls) to the database.
     */
    suspend fun saveAssistantMessage(
        conversationId: String,
        content: String,
        toolCalls: List<CompletedToolCall> = emptyList(),
    ): String {
        val id = UUID.randomUUID().toString()
        messageDao.insert(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = "assistant",
                content = content,
                createdAt = System.currentTimeMillis(),
                tokenCount = (content.length / 4).coerceAtLeast(1),
            )
        )
        // Save tool calls to the separate table
        for (tc in toolCalls) {
            toolCallDao.upsert(
                ToolCallEntity(
                    id = tc.id,
                    messageId = id,
                    toolName = tc.name,
                    arguments = tc.arguments,
                    result = null,
                    status = "pending",
                    durationMs = 0,
                )
            )
        }
        return id
    }

    /**
     * Save a tool result message to the database.
     */
    suspend fun saveToolResultMessage(
        conversationId: String,
        toolCallId: String,
        content: String,
        isError: Boolean = false,
    ): String {
        val id = UUID.randomUUID().toString()
        messageDao.insert(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = "tool",
                content = content,
                createdAt = System.currentTimeMillis(),
                tokenCount = (content.length / 4).coerceAtLeast(1),
                toolCallId = toolCallId,
            )
        )
        // Update the tool call record with the result
        toolCallDao.complete(
            id = toolCallId,
            result = content,
            status = if (isError) "error" else "completed",
            durationMs = 0,
        )
        return id
    }

    fun buildSystemPrompt(): String {
        return MessageBuilder.buildSystemPrompt(context, projectId)
    }
}

/**
 * Convert a [MessageEntity] to a [ChatMessage] for the LLM API.
 */
fun MessageEntity.toChatMessage(toolCalls: List<CompletedToolCall> = emptyList()): ChatMessage = ChatMessage(
    role = if (role == "user" && content.startsWith(INTERNAL_USER_PROMPT_PREFIX)) "user" else role,
    content = content.stripInternalUserPromptMarker().takeIf { it.isNotBlank() },
    toolCallId = toolCallId,
    toolCalls = toolCalls,
    mediaPath = mediaPath,
    mediaMimeType = mediaMimeType,
)
