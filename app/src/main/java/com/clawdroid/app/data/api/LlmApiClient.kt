package com.clawdroid.app.data.api

import android.util.Log
import com.clawdroid.app.BuildConfig
import com.clawdroid.app.core.config.AppConfigManager
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
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
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

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ToolCallDeltaReceived(val index: Int, val id: String, val name: String, val arguments: String) : StreamEvent
    data class ToolCallComplete(val call: CompletedToolCall) : StreamEvent
    data class Usage(val usage: TokenUsage) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data object Done : StreamEvent
}

class LlmApiClient(
    private val baseUrl: String = AppConfigManager.baseUrl,
    private val apiKey: String = AppConfigManager.apiKey,
    private val model: String = AppConfigManager.model,
) {
    fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
    ): Flow<StreamEvent> = flow {
        Log.i("LlmApiClient", "streamChat started. baseUrl=$baseUrl, model=$model, messages=${messages.size}")
        check(baseUrl.isNotBlank()) { "Missing LLM base URL" }
        check(apiKey.isNotBlank()) { "Missing LLM API key" }
        check(model.isNotBlank()) { "Missing LLM model" }

        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))
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

        Log.d("LlmApiClient", "Request payload: $payload")

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

        Log.d("LlmApiClient", "HTTP connection opened to $baseUrl/chat/completions")

        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        Log.d("LlmApiClient", "Request written, fetching responseCode...")
        val code = connection.responseCode
        Log.i("LlmApiClient", "Response code: $code")

        if (code !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.e("LlmApiClient", "HTTP error $code: $errorText")
            emit(StreamEvent.Error("HTTP $code: $errorText"))
            return@flow
        }

        Log.d("LlmApiClient", "Response body stream read start...")
        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        var lastUsage: TokenUsage? = null
        var isDoneEmitted = false
        try {
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    Log.d("LlmApiClient", "Received line: $line")
                    if (data == "[DONE]") {
                        toolCalls.values
                            .mapNotNull { it.buildOrNull() }
                            .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                        toolCalls.clear()
                        lastUsage?.let { emit(StreamEvent.Usage(it)) }
                        emit(StreamEvent.Done)
                        isDoneEmitted = true
                        return@useLines
                    }

                    val event = runCatching { JSONObject(data) }.getOrNull()
                        ?: return@forEach

                    val usageObj = event.optJSONObject("usage")
                    if (usageObj != null) {
                        val prompt = usageObj.optInt("prompt_tokens", 0)
                        val completion = usageObj.optInt("completion_tokens", 0)
                        val cached = usageObj.optJSONObject("prompt_tokens_details")?.optInt("cached_tokens", 0) ?: 0
                        lastUsage = TokenUsage(prompt, completion, cached)
                    }

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
                            emit(StreamEvent.ToolCallDeltaReceived(
                                index = parsed.index,
                                id = builder.getId().orEmpty(),
                                name = builder.getName().orEmpty(),
                                arguments = builder.getArguments()
                            ))
                        }
                    }
                }
            }
        } finally {
            if (!isDoneEmitted) {
                toolCalls.values
                    .mapNotNull { it.buildOrNull() }
                    .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                lastUsage?.let { emit(StreamEvent.Usage(it)) }
                emit(StreamEvent.Done)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun List<ChatMessage>.toJson(): JSONArray {
        val array = JSONArray()
        forEach { message ->
            val json = JSONObject().put("role", message.role)

            if (message.mediaPath != null && message.mediaMimeType != null) {
                val contentArray = JSONArray()
                if (message.content != null) {
                    contentArray.put(
                        JSONObject()
                            .put("type", "text")
                            .put("text", message.content)
                    )
                }
                val file = java.io.File(message.mediaPath)
                if (file.exists() && file.isFile) {
                    val bytes = file.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val urlValue = "data:${message.mediaMimeType};base64,$base64"
                    contentArray.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", urlValue))
                    )
                }
                json.put("content", contentArray)
            } else {
                if (message.content != null) {
                    json.put("content", message.content)
                }
            }

            if (message.toolCallId != null) {
                json.put("tool_call_id", message.toolCallId)
            }
            if (message.toolCalls.isNotEmpty()) {
                json.put("tool_calls", message.toolCalls.toToolCallsJson())
                if (message.content == null && message.mediaPath == null) {
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

        fun getId(): String? = id
        fun getName(): String? = name
        fun getArguments(): String = arguments.toString()

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
