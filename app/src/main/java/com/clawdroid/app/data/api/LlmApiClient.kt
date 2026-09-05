package com.clawdroid.app.data.api

import com.clawdroid.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<CompletedToolCall> = emptyList(),
)

data class ToolCallDelta(
    val index: Int,
    val id: String?,
    val name: String?,
    val argumentsDelta: String,
)

data class CompletedToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ToolCallComplete(val call: CompletedToolCall) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data object Done : StreamEvent
}

class LlmApiClient(
    private val baseUrl: String = BuildConfig.OPENROUTER_BASE_URL.trimEnd('/'),
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val model: String = BuildConfig.OPENROUTER_MODEL,
) {
    fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
    ): Flow<StreamEvent> = flow {
        check(baseUrl.isNotBlank()) { "Missing OpenRouter base URL" }
        check(apiKey.isNotBlank()) { "Missing OpenRouter API key" }
        check(model.isNotBlank()) { "Missing OpenRouter model" }

        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", messages.toJson())

        if (tools != null) {
            payload.put("tools", tools)
            payload.put(
                "tool_choice",
                forcedToolName?.let { toolName ->
                    JSONObject()
                        .put("type", "function")
                        .put("function", JSONObject().put("name", toolName))
                } ?: "auto"
            )
        }

        val connection = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("HTTP-Referer", "https://clawdroid.local")
            setRequestProperty("X-Title", "ClawDroid")
        }

        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        if (connection.responseCode !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            emit(StreamEvent.Error("HTTP ${connection.responseCode}: $errorText"))
            return@flow
        }

        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        connection.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith("data:")) return@forEach
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") {
                    toolCalls.values
                        .mapNotNull { it.buildOrNull() }
                        .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                    emit(StreamEvent.Done)
                    return@useLines
                }

                val event = runCatching { JSONObject(data) }.getOrNull()
                    ?: return@forEach
                val choice = event.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?: return@forEach
                val delta = choice.optJSONObject("delta") ?: return@forEach

                val text = delta.optNullableString("content")
                if (!text.isNullOrEmpty()) emit(StreamEvent.TextDelta(text))

                val deltaToolCalls = delta.optJSONArray("tool_calls")
                if (deltaToolCalls != null) {
                    for (i in 0 until deltaToolCalls.length()) {
                        val raw = deltaToolCalls.optJSONObject(i) ?: continue
                        val parsed = raw.toToolCallDelta() ?: continue
                        val builder = toolCalls.getOrPut(parsed.index) { ToolCallBuilder() }
                        builder.append(parsed)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun List<ChatMessage>.toJson(): JSONArray {
        val array = JSONArray()
        forEach { message ->
            val json = JSONObject().put("role", message.role)

            if (message.content != null) {
                json.put("content", message.content)
            }
            if (message.toolCallId != null) {
                json.put("tool_call_id", message.toolCallId)
            }
            if (message.toolCalls.isNotEmpty()) {
                json.put("tool_calls", message.toolCalls.toToolCallsJson())
                if (message.content == null) {
                    json.put("content", JSONObject.NULL)
                }
            }

            array.put(json)
        }
        return array
    }

    private fun List<CompletedToolCall>.toToolCallsJson(): JSONArray {
        val array = JSONArray()
        forEach { call ->
            array.put(
                JSONObject()
                    .put("id", call.id)
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", call.name)
                            .put("arguments", call.arguments)
                    )
            )
        }
        return array
    }

    private fun JSONObject.toToolCallDelta(): ToolCallDelta? {
        val index = optInt("index", -1)
        if (index < 0) return null

        val function = optJSONObject("function")
        return ToolCallDelta(
            index = index,
            id = optNullableString("id")?.takeIf { it.isNotEmpty() },
            name = function?.optNullableString("name")?.takeIf { it.isNotEmpty() },
            argumentsDelta = function?.optNullableString("arguments").orEmpty(),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    private class ToolCallBuilder {
        private var id: String? = null
        private var name: String? = null
        private val arguments = StringBuilder()

        fun append(delta: ToolCallDelta) {
            if (delta.id != null) id = delta.id
            if (delta.name != null) name = delta.name
            arguments.append(delta.argumentsDelta)
        }

        fun buildOrNull(): CompletedToolCall? {
            val finalId = id ?: return null
            val finalName = name ?: return null
            return CompletedToolCall(
                id = finalId,
                name = finalName,
                arguments = arguments.toString(),
            )
        }
    }
}
