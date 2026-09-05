package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.data.api.ToolSchemaRegistry
import kotlinx.coroutines.flow.collect

data class AgentSmokeResult(
    val toolCall: CompletedToolCall,
    val commandOutput: String,
    val finalResponse: String,
)

object AgentSmokeTester {
    suspend fun run(context: Context): AgentSmokeResult {
        BootstrapManager.ensureBootstrapped(context) { }

        val client = LlmApiClient()
        val initialMessages = listOf(
            ChatMessage(
                role = "system",
                content = "You are testing ClawDroid tool calling. You must call execute_command exactly once, then summarize the tool result after it is provided.",
            ),
            ChatMessage(
                role = "user",
                content = """
                    Call execute_command with command "echo CLAWDROID_TOOL_OK" and cwd "${context.filesDir}/home".
                    Do not answer in text before calling the tool.
                """.trimIndent(),
            ),
        )

        val text = StringBuilder()
        val calls = mutableListOf<CompletedToolCall>()
        client.streamChat(
            messages = initialMessages,
            tools = ToolSchemaRegistry.allTools(),
            forcedToolName = "execute_command",
        ).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> text.append(event.text)
                is StreamEvent.ToolCallDeltaReceived -> Unit
                is StreamEvent.ToolCallComplete -> calls += event.call
                is StreamEvent.Usage -> Unit
                is StreamEvent.Error -> error(event.message)
                StreamEvent.Done -> Unit
            }
        }

        val call = calls.firstOrNull()
            ?: error("Model did not call a tool. Text response: ${text.toString().trim()}")
        check(call.name == "execute_command") {
            "Model called unsupported tool ${call.name}"
        }

        val toolResult = ToolExecutor.execute(context, call)
        check(!toolResult.isError) {
            "Tool execution failed: ${toolResult.content}"
        }

        val finalText = StringBuilder()
        client.streamChat(
            messages = initialMessages + listOf(
                ChatMessage(role = "assistant", toolCalls = listOf(call)),
                ChatMessage(
                    role = "tool",
                    content = toolResult.content,
                    toolCallId = toolResult.callId,
                ),
                ChatMessage(
                    role = "user",
                    content = "Reply with exactly: ClawDroid tool loop ok.",
                ),
            ),
        ).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> finalText.append(event.text)
                is StreamEvent.ToolCallDeltaReceived -> Unit
                is StreamEvent.ToolCallComplete -> Unit
                is StreamEvent.Usage -> Unit
                is StreamEvent.Error -> error(event.message)
                StreamEvent.Done -> Unit
            }
        }

        val finalResponse = finalText.toString().trim()
        check(finalResponse.isNotBlank()) { "Model returned no final answer after tool result" }

        return AgentSmokeResult(
            toolCall = call,
            commandOutput = toolResult.content,
            finalResponse = finalResponse,
        )
    }
}
