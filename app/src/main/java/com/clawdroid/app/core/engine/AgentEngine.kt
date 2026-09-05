package com.clawdroid.app.core.engine

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.core.memory.MemoryManager
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.ContextBuilder
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.MessageBuilder
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.data.api.TokenUsage
import com.clawdroid.app.data.api.ToolSchemaRegistry
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.channelFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AgentRunEvent {
    data class TextDelta(val text: String) : AgentRunEvent
    data class ToolCallRequested(val call: CompletedToolCall) : AgentRunEvent
    data class ToolCallStreaming(val callId: String, val name: String, val arguments: String) : AgentRunEvent
    data class ToolOutputUpdated(val callId: String, val output: String) : AgentRunEvent
    data class ToolResultReceived(val result: ToolExecutionResult) : AgentRunEvent
    data class SteeringApplied(val message: String) : AgentRunEvent
    data class LoopWarning(val message: String) : AgentRunEvent
    data class Completed(val finalText: String) : AgentRunEvent
    data class Stopped(val reason: String) : AgentRunEvent
    data class RunError(val message: String) : AgentRunEvent
}

class AgentEngine(
    private val context: Context,
    private val projectId: String? = null,
    private val client: LlmApiClient = LlmApiClient(),
    private val steeringQueue: SteeringQueue = SteeringQueue(),
    private val toolExecutor: ToolExecutor = ToolExecutor,
    private val loopDetector: LoopDetector = LoopDetector(),
    private val memoryManager: MemoryManager = MemoryManager(context),
) {
    private val stopRequested = AtomicBoolean(false)

    init {
        // Load persistent memory into the message builder on engine creation
        val memory = memoryManager.readMemory()
        MessageBuilder.setMemoryContext(memory)
    }

    fun steer(message: String) {
        steeringQueue.offer(message)
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun run(
        prompt: String,
        targetConversationId: String? = null,
        maxTurns: Int = 200,
        mediaPath: String? = null,
        mediaMimeType: String? = null,
    ): Flow<AgentRunEvent> = channelFlow {
        stopRequested.set(false)
        Log.i("AgentEngine", "run() started. prompt: $prompt, targetConversationId: $targetConversationId")
        val result = BootstrapManager.ensureBootstrapped(context) { }
        Log.i("AgentEngine", "ensureBootstrapped completed. Result: $result")

        val db = ClawDroidDatabase.get(context)
        val conversationDao = db.conversations()
        val messageDao = db.messages()
        val toolCallDao = db.toolCalls()

        // 1. Fetch or create active conversation associated with this project/id
        var conversation = if (targetConversationId != null) {
            conversationDao.getById(targetConversationId)
        } else {
            conversationDao.getMostRecent()
        }

        if (conversation == null || (targetConversationId == null && conversation.projectId != projectId)) {
            val newId = targetConversationId ?: UUID.randomUUID().toString()
            conversation = ConversationEntity(
                id = newId,
                projectId = projectId,
                title = if (targetConversationId != null && targetConversationId.startsWith("whatsapp_chat_")) "WhatsApp Chat" else if (targetConversationId != null && targetConversationId.startsWith("sms_chat_")) "SMS Chat" else "New Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = "active",
                costUsd = 0.0,
            )
            conversationDao.upsert(conversation)
        }
        val conversationId = conversation.id

        val contextBuilder = ContextBuilder(context, projectId, conversationDao, messageDao, toolCallDao)
        val compactionManager = CompactionManager(conversationDao, messageDao, client)
        val costTracker = CostTracker()

        // 2. Save current user prompt to DB if not already present as the last message
        val existingMessages = messageDao.getAll(conversationId)
        val lastMsg = existingMessages.lastOrNull()
        if (lastMsg == null || lastMsg.role != "user" || lastMsg.content != prompt) {
            contextBuilder.saveUserMessage(conversationId, prompt, mediaPath, mediaMimeType)
        } else if (lastMsg.mediaPath != mediaPath || lastMsg.mediaMimeType != mediaMimeType) {
            messageDao.update(lastMsg.copy(mediaPath = mediaPath, mediaMimeType = mediaMimeType))
        }

        val finalText = StringBuilder()

        repeat(maxTurns) {
            if (stopRequested.get()) {
                send(AgentRunEvent.Stopped("Stop requested"))
                saveSummary(finalText.toString())
                return@channelFlow
            }

            // 3. Build current conversation context from DB
            var messages = contextBuilder.buildContext(conversationId)

            // 4. Handle steering messages before calling LLM
            val steering = steeringQueue.drain()
            if (steering.isNotEmpty()) {
                for (msg in steering) {
                    contextBuilder.saveUserMessage(conversationId, msg)
                    send(AgentRunEvent.SteeringApplied(msg))
                }
                messages = contextBuilder.buildContext(conversationId)
            }

            val turnText = StringBuilder()
            val toolCalls = mutableListOf<CompletedToolCall>()
            var tokenUsage: TokenUsage? = null

            // 5. Query the LLM
            client.streamChat(
                messages = messages,
                tools = ToolSchemaRegistry.allTools(),
            ).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        turnText.append(event.text)
                        finalText.append(event.text)
                        send(AgentRunEvent.TextDelta(event.text))
                    }
                    is StreamEvent.ToolCallDeltaReceived -> {
                        if (event.id.isNotEmpty() && event.name.isNotEmpty()) {
                            send(AgentRunEvent.ToolCallStreaming(
                                callId = event.id,
                                name = event.name,
                                arguments = event.arguments
                            ))
                        }
                    }
                    is StreamEvent.ToolCallComplete -> toolCalls += event.call
                    is StreamEvent.Usage -> tokenUsage = event.usage
                    is StreamEvent.Error -> error(event.message)
                    StreamEvent.Done -> Unit
                }
            }

            // 6. Save assistant message and track usage/costs
            contextBuilder.saveAssistantMessage(conversationId, turnText.toString(), toolCalls)

            val usage = tokenUsage ?: TokenUsage(
                promptTokens = TokenEstimator.estimateMessages(messages),
                completionTokens = TokenEstimator.estimate(turnText.toString())
            )
            costTracker.record(usage.promptTokens, usage.completionTokens, usage.cachedTokens)

            // Persist cost update in database
            val costDelta = (usage.promptTokens / 1_000_000.0 * 0.15) +
                    (usage.completionTokens / 1_000_000.0 * 0.60) +
                    (usage.cachedTokens / 1_000_000.0 * 0.03)

            conversationDao.recordUsage(
                id = conversationId,
                lastPromptTokens = usage.promptTokens,
                promptTokens = usage.promptTokens.toLong(),
                completionTokens = usage.completionTokens.toLong(),
                cachedTokens = usage.cachedTokens.toLong(),
                costDelta = costDelta
            )

            // 7. Check if compaction is needed
            val postTurnMessages = contextBuilder.buildContext(conversationId)
            val decision = compactionManager.shouldCompact(postTurnMessages)
            if (decision.shouldCompact) {
                compactionManager.compact(conversationId)
            }

            // 8. Exit loop if no tool calls were generated
            if (toolCalls.isEmpty()) {
                val finalAnswer = finalText.toString().trim()
                send(AgentRunEvent.Completed(finalAnswer))
                saveSummary(finalAnswer)
                return@channelFlow
            }

            // 9. Execute tools
            for (call in toolCalls) {
                send(AgentRunEvent.ToolCallRequested(call))
                when (val loopCheck = loopDetector.record(call)) {
                    LoopCheckResult.Ok -> Unit
                    is LoopCheckResult.Warn -> send(AgentRunEvent.LoopWarning(loopCheck.message))
                    is LoopCheckResult.Stop -> {
                        send(AgentRunEvent.Stopped(loopCheck.message))
                        saveSummary(finalText.toString())
                        return@channelFlow
                    }
                }

                if (stopRequested.get()) {
                    send(AgentRunEvent.Stopped("Stop requested"))
                    saveSummary(finalText.toString())
                    return@channelFlow
                }

                val result = toolExecutor.execute(context, call) { progress ->
                    send(AgentRunEvent.ToolOutputUpdated(call.id, progress))
                }
                send(AgentRunEvent.ToolResultReceived(result))

                // Save tool result to DB
                contextBuilder.saveToolResultMessage(
                    conversationId = conversationId,
                    toolCallId = result.callId,
                    content = result.content,
                    isError = result.isError
                )
            }

            // 10. Process steering messages received during tool runs
            val postToolSteering = steeringQueue.drain()
            if (postToolSteering.isNotEmpty()) {
                for (msg in postToolSteering) {
                    contextBuilder.saveUserMessage(conversationId, msg)
                    send(AgentRunEvent.SteeringApplied(msg))
                }
            }
        }

        val final = finalText.toString().trim()
        send(AgentRunEvent.Stopped("Reached max agent turns ($maxTurns)"))
        saveSummary(final)
    }

    private fun saveSummary(text: String) {
        if (text.isBlank()) return
        val preview = text.take(500).replace("\n", " ").trim()
        val summary = "Completed task. Summary: $preview"
        memoryManager.appendSessionSummary(summary)
        // Reload memory context for next run
        MessageBuilder.setMemoryContext(memoryManager.readMemory())
    }
}
