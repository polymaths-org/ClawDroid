package com.clawdroid.app.core.engine

import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.ContextBuilder
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.data.db.ConversationDao
import com.clawdroid.app.data.db.MessageDao
import com.clawdroid.app.data.db.MessageEntity
import kotlinx.coroutines.flow.collect
import java.util.UUID

data class CompactionDecision(
    val shouldCompact: Boolean,
    val estimatedTokens: Int,
    val limitTokens: Int,
)

class CompactionManager(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val llmClient: LlmApiClient,
    private val limitTokens: Int = 128_000,
    private val headroomRatio: Double = 0.20,
) {
    fun shouldCompact(messages: List<ChatMessage>): CompactionDecision {
        val estimated = TokenEstimator.estimateMessages(messages)
        val threshold = (limitTokens * (1.0 - headroomRatio)).toInt()
        return CompactionDecision(
            shouldCompact = estimated >= threshold,
            estimatedTokens = estimated,
            limitTokens = limitTokens,
        )
    }

    /**
     * Run LLM-based summarization on all messages of the conversation, save the summary message to the DB,
     * and update the conversation summaryMessageId.
     */
    suspend fun compact(conversationId: String) {
        val conversation = conversationDao.getById(conversationId) ?: return

        // 1. Fetch all existing messages to build the transcript
        val allMessages = messageDao.getAll(conversationId)
        if (allMessages.isEmpty()) return

        // Format the entire transcript for the summarizer
        val transcript = allMessages.joinToString("\n") { message ->
            val roleLabel = when (message.role) {
                "system" -> "System Instructions"
                "user" -> "User"
                "assistant" -> "Agent"
                "tool" -> "Tool Result"
                else -> message.role.replaceFirstChar { it.uppercase() }
            }
            "$roleLabel: ${message.content.orEmpty()}"
        }

        // 2. Prepare summarization messages for the LLM
        val summarizerSystemPrompt = """
            You are a highly efficient text summarization agent.
            Your task is to summarize the following conversation transcript between a user and ClawDroid (an AI assistant).
            Retain all key decisions made, tasks completed, file paths, tool usage results, and user preferences.
            Keep the summary concise but informative. Do not lose critical factual information.
        """.trimIndent()

        val summarizerUserPrompt = """
            Please summarize the conversation history below:

            $transcript
        """.trimIndent()

        val summarizerMessages = listOf(
            ChatMessage(role = "system", content = summarizerSystemPrompt),
            ChatMessage(role = "user", content = summarizerUserPrompt)
        )

        // 3. Call LLM to generate summary
        val summaryBuilder = StringBuilder()
        llmClient.streamChat(summarizerMessages).collect { event ->
            if (event is StreamEvent.TextDelta) {
                summaryBuilder.append(event.text)
            }
        }

        val summaryText = summaryBuilder.toString().trim()
        if (summaryText.isBlank()) return

        // 4. Save summary as a special message
        val summaryMessageId = UUID.randomUUID().toString()
        val summaryMessage = MessageEntity(
            id = summaryMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "[Compacted Summary]\n$summaryText",
            createdAt = System.currentTimeMillis(),
            tokenCount = TokenEstimator.estimate(summaryText),
            toolCallId = null
        )
        messageDao.insert(summaryMessage)

        // 5. Update conversation with the new summary pointer
        conversationDao.setSummaryMessageId(conversationId, summaryMessageId)
    }
}
