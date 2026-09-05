package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.MessageBuilder
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.data.api.ToolSchemaRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AgentRunEvent {
    data class TextDelta(val text: String) : AgentRunEvent
    data class ToolCallRequested(val call: CompletedToolCall) : AgentRunEvent
    data class ToolResultReceived(val result: ToolExecutionResult) : AgentRunEvent
    data class SteeringApplied(val message: String) : AgentRunEvent
    data class LoopWarning(val message: String) : AgentRunEvent
    data class Completed(val finalText: String) : AgentRunEvent
    data class Stopped(val reason: String) : AgentRunEvent
}

class AgentEngine(
    private val context: Context,
    private val client: LlmApiClient = LlmApiClient(),
    private val steeringQueue: SteeringQueue = SteeringQueue(),
    private val toolExecutor: ToolExecutor = ToolExecutor,
    private val loopDetector: LoopDetector = LoopDetector(),
) {
    private val stopRequested = AtomicBoolean(false)

    fun steer(message: String) {
        steeringQueue.offer(message)
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun run(prompt: String, maxTurns: Int = 12): Flow<AgentRunEvent> = flow {
        stopRequested.set(false)
        var messages = MessageBuilder.forUserPrompt(prompt)
        val finalText = StringBuilder()

        repeat(maxTurns) {
            if (stopRequested.get()) {
                emit(AgentRunEvent.Stopped("Stop requested"))
                return@flow
            }

            val turnText = StringBuilder()
            val toolCalls = mutableListOf<CompletedToolCall>()
            client.streamChat(
                messages = messages,
                tools = ToolSchemaRegistry.allTools(),
            ).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta -> {
                        turnText.append(event.text)
                        finalText.append(event.text)
                        emit(AgentRunEvent.TextDelta(event.text))
                    }

                    is StreamEvent.ToolCallComplete -> toolCalls += event.call
                    is StreamEvent.Error -> error(event.message)
                    StreamEvent.Done -> Unit
                }
            }

            if (toolCalls.isEmpty()) {
                emit(AgentRunEvent.Completed(finalText.toString().trim()))
                return@flow
            }

            val assistantMessage = ChatMessage(
                role = "assistant",
                content = turnText.toString().takeIf { it.isNotBlank() },
                toolCalls = toolCalls,
            )
            val toolResultMessages = mutableListOf<ChatMessage>()
            messages = messages + assistantMessage

            for (call in toolCalls) {
                emit(AgentRunEvent.ToolCallRequested(call))
                when (val loopCheck = loopDetector.record(call)) {
                    LoopCheckResult.Ok -> Unit
                    is LoopCheckResult.Warn -> emit(AgentRunEvent.LoopWarning(loopCheck.message))
                    is LoopCheckResult.Stop -> {
                        emit(AgentRunEvent.Stopped(loopCheck.message))
                        return@flow
                    }
                }

                if (stopRequested.get()) {
                    emit(AgentRunEvent.Stopped("Stop requested"))
                    return@flow
                }

                val result = toolExecutor.execute(context, call)
                emit(AgentRunEvent.ToolResultReceived(result))
                toolResultMessages += ChatMessage(
                    role = "tool",
                    content = result.content,
                    toolCallId = result.callId,
                )
            }

            messages = messages + toolResultMessages

            for (steeringMessage in steeringQueue.drain()) {
                messages = messages + ChatMessage(role = "user", content = steeringMessage)
                emit(AgentRunEvent.SteeringApplied(steeringMessage))
            }
        }

        emit(AgentRunEvent.Stopped("Reached max agent turns ($maxTurns)"))
    }
}
