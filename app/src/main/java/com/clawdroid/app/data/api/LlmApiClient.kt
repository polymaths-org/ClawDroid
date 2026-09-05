package com.clawdroid.app.data.api

import android.util.Log
import com.clawdroid.app.BuildConfig
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.config.OpenCodeZen
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
    private val provider: String = AppConfigManager.provider,
) {
    fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
        openCodeSessionId: String? = null,
    ): Flow<StreamEvent> {
        return if (isAnthropicProvider()) {
            streamAnthropicChat(messages, tools, forcedToolName)
        } else if (OpenCodeZen.isZen(provider, baseUrl) && OpenCodeZen.isResponsesNativeModel(model)) {
            streamResponsesChat(messages, tools, openCodeSessionId)
        } else {
            streamOpenAiChat(messages, tools, forcedToolName, openCodeSessionId)
        }
    }

    private fun streamOpenAiChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
        openCodeSessionId: String? = null,
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

        val mediaMessageCount = messages.count { it.mediaPath != null && it.mediaMimeType != null }
        val body = payload.toString()
        if (Log.isLoggable("LlmApiClient", Log.DEBUG)) {
            Log.d(
                "LlmApiClient",
                "Request payload prepared: chars=${body.length}, messages=${messages.size}, " +
                    "mediaMessages=$mediaMessageCount, tools=${tools?.length() ?: 0}, forcedTool=$forcedToolName"
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
            if (OpenCodeZen.isZen(provider, baseUrl)) {
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("User-Agent", OpenCodeZen.STOCK_USER_AGENT)
                setRequestProperty("x-opencode-client", OpenCodeZen.STOCK_CLIENT)
                if (!openCodeSessionId.isNullOrBlank()) {
                    setRequestProperty("x-opencode-session", openCodeSessionId)
                    setRequestProperty("x-opencode-request", java.util.UUID.randomUUID().toString())
                }
            } else {
                setRequestProperty("HTTP-Referer", "https://clawdroid.local")
                setRequestProperty("X-Title", "ClawDroid")
            }
        }

        if (Log.isLoggable("LlmApiClient", Log.DEBUG)) {
            Log.d("LlmApiClient", "HTTP connection opened to $baseUrl/chat/completions")
        }

        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }

        if (Log.isLoggable("LlmApiClient", Log.DEBUG)) {
            Log.d("LlmApiClient", "Request written, fetching responseCode...")
        }
        val code = connection.responseCode
        Log.i("LlmApiClient", "Response code: $code")

        if (code !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.e("LlmApiClient", "HTTP error $code: $errorText")
            emit(StreamEvent.Error("HTTP $code: $errorText"))
            return@flow
        }

        if (Log.isLoggable("LlmApiClient", Log.DEBUG)) {
            Log.d("LlmApiClient", "Response body stream read start...")
        }
        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        var lastUsage: TokenUsage? = null
        var isDoneEmitted = false
        try {
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
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

    // Meta muse-spark models are served ONLY on Zen /v1/responses.
    // Chat Completions translation 500s upstream (see opencode-cc routing.go).
    private fun streamResponsesChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        openCodeSessionId: String? = null,
    ): Flow<StreamEvent> = flow {
        Log.i("LlmApiClient", "streamResponsesChat started. baseUrl=$baseUrl, model=$model, messages=${messages.size}")
        check(baseUrl.isNotBlank()) { "Missing LLM base URL" }
        check(apiKey.isNotBlank()) { "Missing LLM API key" }
        check(model.isNotBlank()) { "Missing LLM model" }

        val payload = JSONObject()
            .put("model", model)
            .put("stream", true)
        val systemText = messages
            .filter { it.role == "system" && !it.content.isNullOrBlank() }
            .joinToString("\n\n") { it.content.orEmpty() }
        if (systemText.isNotBlank()) {
            payload.put("instructions", systemText)
        }
        payload.put("input", messages.toResponsesInput())
        val responsesTools = tools?.toResponsesTools()
        if (responsesTools != null && responsesTools.length() > 0) {
            payload.put("tools", responsesTools)
        }
        payload.put("tool_choice", "auto")
        val body = payload.toString()

        val sessionId = openCodeSessionId?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()
        val connection = (URL("${baseUrl.trimEnd('/')}/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", OpenCodeZen.STOCK_USER_AGENT)
            setRequestProperty("x-opencode-client", OpenCodeZen.STOCK_CLIENT)
            setRequestProperty("x-opencode-session", sessionId)
            setRequestProperty("x-opencode-request", java.util.UUID.randomUUID().toString())
        }
        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        Log.i("LlmApiClient", "Responses response code: $code")
        if (code !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.e("LlmApiClient", "Responses HTTP error $code: $errorText")
            emit(StreamEvent.Error("HTTP $code: $errorText"))
            return@flow
        }

        val callsByIndex = mutableMapOf<Int, ResponsesFunctionCallBuilder>()
        val completedIds = mutableSetOf<String>()
        var lastUsage: TokenUsage? = null
        var isDoneEmitted = false
        // Fallback accumulation for response.completed output array.
        val completedText = StringBuilder()
        try {
            connection.inputStream.bufferedReader().useLines { lines ->
                var pendingEvent = ""
                lines.forEach { rawLine ->
                    val line = rawLine.trimEnd('\r')
                    if (line.isBlank()) {
                        pendingEvent = ""
                        return@forEach
                    }
                    if (line.startsWith("event:")) {
                        pendingEvent = line.removePrefix("event:").trim()
                        return@forEach
                    }
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") return@forEach
                    val event = runCatching { JSONObject(data) }.getOrNull()
                        ?: return@forEach
                    val type = event.optString("type")
                    if (type == "ping") {
                        pendingEvent = ""
                        return@forEach
                    }
                    when (type) {
                        "response.output_text.delta" -> {
                            val delta = event.optString("delta")
                            if (delta.isNotEmpty()) emit(StreamEvent.TextDelta(delta))
                        }
                        "response.output_item.added" -> {
                            val item = event.optJSONObject("item") ?: return@forEach
                            if (item.optString("type") == "function_call") {
                                val index = event.optInt("output_index", -1)
                                if (index >= 0) {
                                    val builder = callsByIndex.getOrPut(index) { ResponsesFunctionCallBuilder() }
                                    builder.itemId = item.optString("item_id").ifBlank { item.optString("id") }
                                        .takeIf { it.isNotBlank() } ?: builder.itemId
                                    val callId = item.optString("call_id").takeIf { it.isNotBlank() }
                                    if (callId != null) builder.callId = callId
                                    val name = item.optString("name").takeIf { it.isNotBlank() }
                                    if (name != null) builder.name = name
                                    val args = item.optString("arguments")
                                    if (args.isNotEmpty()) builder.setArguments(args)
                                }
                            }
                        }
                        "response.function_call_arguments.delta" -> {
                            val index = event.optInt("output_index", -1)
                            val delta = event.optString("delta")
                            val itemId = event.optString("item_id")
                            if (index >= 0 && delta.isNotEmpty()) {
                                val builder = callsByIndex.getOrPut(index) { ResponsesFunctionCallBuilder() }
                                if (itemId.isNotBlank() && builder.itemId.isNullOrBlank()) builder.itemId = itemId
                                builder.appendArguments(delta)
                                emit(StreamEvent.ToolCallDeltaReceived(
                                    index = index,
                                    id = builder.callId.orEmpty(),
                                    name = builder.name.orEmpty(),
                                    arguments = builder.arguments.toString()
                                ))
                            }
                        }
                        "response.function_call_arguments.done" -> {
                            val index = event.optInt("output_index", -1)
                            val args = event.optString("arguments")
                            val name = event.optString("name").takeIf { it.isNotBlank() }
                            if (index >= 0) {
                                val builder = callsByIndex.getOrPut(index) { ResponsesFunctionCallBuilder() }
                                if (args.isNotEmpty()) builder.setArguments(args)
                                if (name != null) builder.name = name
                                val itemId = event.optString("item_id").takeIf { it.isNotBlank() }
                                if (itemId != null && builder.itemId.isNullOrBlank()) builder.itemId = itemId
                            }
                        }
                        "response.output_item.done" -> {
                            val item = event.optJSONObject("item") ?: return@forEach
                            if (item.optString("type") == "function_call") {
                                val callId = item.optString("call_id").takeIf { it.isNotBlank() } ?: return@forEach
                                val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@forEach
                                val args = item.optString("arguments")
                                val key = "call:$callId"
                                if (completedIds.add(key)) {
                                    // Prefer accumulated streaming args when present.
                                    val streamed = callsByIndex.values.firstOrNull { it.callId == callId }
                                    val finalArgs = if (streamed != null && streamed.arguments.isNotEmpty()) {
                                        streamed.arguments.toString()
                                    } else {
                                        args
                                    }
                                    if (streamed != null) {
                                        streamed.callId = callId
                                        streamed.name = name
                                        if (streamed.arguments.isEmpty() && finalArgs.isNotEmpty()) {
                                            streamed.appendArguments(finalArgs)
                                        }
                                    }
                                    emit(StreamEvent.ToolCallComplete(CompletedToolCall(callId, name, finalArgs)))
                                }
                            } else if (item.optString("type") == "message") {
                                val content = item.optJSONArray("content") ?: return@forEach
                                for (i in 0 until content.length()) {
                                    val text = content.optJSONObject(i)?.optString("text").orEmpty()
                                    if (text.isNotEmpty()) completedText.append(text)
                                }
                            }
                        }
                        "response.completed" -> {
                            val response = event.optJSONObject("response") ?: return@forEach
                            val usage = response.optJSONObject("usage")
                            if (usage != null) {
                                lastUsage = TokenUsage(
                                    promptTokens = usage.optInt("input_tokens", 0),
                                    completionTokens = usage.optInt("output_tokens", 0),
                                    cachedTokens = usage.optJSONObject("input_tokens_details")
                                        ?.optInt("cached_tokens", 0) ?: 0
                                )
                            }
                            val output = response.optJSONArray("output")
                            if (output != null) {
                                for (i in 0 until output.length()) {
                                    val item = output.optJSONObject(i) ?: continue
                                    if (item.optString("type") == "function_call") {
                                        val callId = item.optString("call_id").takeIf { it.isNotBlank() } ?: continue
                                        val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                                        if (completedIds.add("call:$callId")) {
                                            emit(StreamEvent.ToolCallComplete(
                                                CompletedToolCall(callId, name, item.optString("arguments"))
                                            ))
                                        }
                                    }
                                }
                            }
                            callsByIndex.values
                                .mapNotNull { it.buildOrNull() }
                                .filter { completedIds.add("call:${it.id}") }
                                .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                            callsByIndex.clear()
                            lastUsage?.let { emit(StreamEvent.Usage(it)) }
                            emit(StreamEvent.Done)
                            isDoneEmitted = true
                            return@useLines
                        }
                    }
                    pendingEvent = ""
                }
            }
        } finally {
            if (!isDoneEmitted) {
                callsByIndex.values
                    .mapNotNull { it.buildOrNull() }
                    .filter { completedIds.add("call:${it.id}") }
                    .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                lastUsage?.let { emit(StreamEvent.Usage(it)) }
                emit(StreamEvent.Done)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamAnthropicChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
    ): Flow<StreamEvent> = flow {
        Log.i("LlmApiClient", "streamAnthropicChat started. baseUrl=$baseUrl, model=$model, messages=${messages.size}")
        check(apiKey.isNotBlank()) { "Missing Anthropic API key" }
        check(model.isNotBlank()) { "Missing Anthropic model" }

        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("stream", true)
            .put("messages", messages.toAnthropicMessages())

        val system = messages
            .filter { it.role == "system" && !it.content.isNullOrBlank() }
            .joinToString("\n\n") { it.content.orEmpty() }
        if (system.isNotBlank()) {
            payload.put("system", system)
        }

        val anthropicTools = tools?.toAnthropicTools()
        if (anthropicTools != null && anthropicTools.length() > 0) {
            payload.put("tools", anthropicTools)
            if (!forcedToolName.isNullOrBlank()) {
                payload.put(
                    "tool_choice",
                    JSONObject()
                        .put("type", "tool")
                        .put("name", forcedToolName),
                )
            }
        }

        val endpoint = "${baseUrl.trimEnd('/')}/messages"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }

        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        Log.i("LlmApiClient", "Anthropic response code: $code")
        if (code !in 200..299) {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Log.e("LlmApiClient", "Anthropic HTTP error $code: $errorText")
            emit(StreamEvent.Error("HTTP $code: $errorText"))
            return@flow
        }

        val toolCalls = mutableMapOf<Int, ToolCallBuilder>()
        var inputTokens = 0
        var outputTokens = 0
        var isDoneEmitted = false

        try {
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) return@forEach

                    val event = runCatching { JSONObject(data) }.getOrNull()
                        ?: return@forEach
                    when (event.optString("type")) {
                        "message_start" -> {
                            val usage = event.optJSONObject("message")?.optJSONObject("usage")
                            inputTokens = usage?.optInt("input_tokens", 0) ?: inputTokens
                            outputTokens = usage?.optInt("output_tokens", 0) ?: outputTokens
                        }
                        "content_block_start" -> {
                            val index = event.optInt("index", -1)
                            val block = event.optJSONObject("content_block")
                            if (index >= 0 && block?.optString("type") == "tool_use") {
                                val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
                                builder.append(
                                    ToolCallDelta(
                                        index = index,
                                        id = block.optString("id"),
                                        name = block.optString("name"),
                                        argumentsDelta = "",
                                    ),
                                )
                            }
                        }
                        "content_block_delta" -> {
                            val index = event.optInt("index", -1)
                            val delta = event.optJSONObject("delta") ?: return@forEach
                            when (delta.optString("type")) {
                                "text_delta" -> {
                                    val text = delta.optString("text")
                                    if (text.isNotEmpty()) emit(StreamEvent.TextDelta(text))
                                }
                                "input_json_delta" -> {
                                    val builder = toolCalls.getOrPut(index) { ToolCallBuilder() }
                                    builder.append(
                                        ToolCallDelta(
                                            index = index,
                                            id = null,
                                            name = null,
                                            argumentsDelta = delta.optString("partial_json"),
                                        ),
                                    )
                                    emit(
                                        StreamEvent.ToolCallDeltaReceived(
                                            index = index,
                                            id = builder.getId().orEmpty(),
                                            name = builder.getName().orEmpty(),
                                            arguments = builder.getArguments(),
                                        ),
                                    )
                                }
                            }
                        }
                        "content_block_stop" -> {
                            val index = event.optInt("index", -1)
                            toolCalls.remove(index)?.buildOrNull()?.let { call ->
                                emit(StreamEvent.ToolCallComplete(call))
                            }
                        }
                        "message_delta" -> {
                            val usage = event.optJSONObject("usage")
                            outputTokens = usage?.optInt("output_tokens", outputTokens) ?: outputTokens
                        }
                        "error" -> {
                            val error = event.optJSONObject("error")
                            emit(StreamEvent.Error(error?.optString("message") ?: "Anthropic stream error"))
                            isDoneEmitted = true
                            return@useLines
                        }
                        "message_stop" -> {
                            emit(StreamEvent.Usage(TokenUsage(inputTokens, outputTokens, 0)))
                            emit(StreamEvent.Done)
                            isDoneEmitted = true
                            return@useLines
                        }
                    }
                }
            }
        } finally {
            if (!isDoneEmitted) {
                toolCalls.values
                    .mapNotNull { it.buildOrNull() }
                    .forEach { emit(StreamEvent.ToolCallComplete(it)) }
                emit(StreamEvent.Usage(TokenUsage(inputTokens, outputTokens, 0)))
                emit(StreamEvent.Done)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun isAnthropicProvider(): Boolean {
        return provider.equals("anthropic", ignoreCase = true) ||
            baseUrl.contains("api.anthropic.com", ignoreCase = true)
    }

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

    private fun List<ChatMessage>.toAnthropicMessages(): JSONArray {
        val array = JSONArray()
        forEach { message ->
            if (message.role == "system") return@forEach
            val role = if (message.role == "assistant") "assistant" else "user"
            val content = JSONArray()

            if (message.role == "tool") {
                content.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", message.toolCallId.orEmpty())
                        .put("content", message.content.orEmpty()),
                )
            } else {
                message.content?.takeIf { it.isNotBlank() }?.let { text ->
                    content.put(JSONObject().put("type", "text").put("text", text))
                }

                if (message.mediaPath != null && message.mediaMimeType != null && role == "user") {
                    val file = java.io.File(message.mediaPath)
                    if (file.exists() && file.isFile && message.mediaMimeType.startsWith("image/")) {
                        val base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                        content.put(
                            JSONObject()
                                .put("type", "image")
                                .put(
                                    "source",
                                    JSONObject()
                                        .put("type", "base64")
                                        .put("media_type", message.mediaMimeType)
                                        .put("data", base64),
                                ),
                        )
                    }
                }

                message.toolCalls.forEach { call ->
                    content.put(
                        JSONObject()
                            .put("type", "tool_use")
                            .put("id", call.id)
                            .put("name", call.name)
                            .put(
                                "input",
                                runCatching { JSONObject(call.arguments) }
                                    .getOrElse { JSONObject().put("raw", call.arguments) },
                            ),
                    )
                }
            }

            if (content.length() > 0) {
                array.appendAnthropicMessage(role, content)
            }
        }
        return array
    }

    private fun JSONArray.appendAnthropicMessage(role: String, content: JSONArray) {
        val last = if (length() > 0) optJSONObject(length() - 1) else null
        if (last != null && last.optString("role") == role) {
            val existing = last.optJSONArray("content") ?: JSONArray()
            for (i in 0 until content.length()) {
                existing.put(content.get(i))
            }
            last.put("content", existing)
        } else {
            put(JSONObject().put("role", role).put("content", content))
        }
    }

    private fun JSONArray.toAnthropicTools(): JSONArray {
        val result = JSONArray()
        for (i in 0 until length()) {
            val function = optJSONObject(i)?.optJSONObject("function") ?: continue
            result.put(
                JSONObject()
                    .put("name", function.optString("name"))
                    .put("description", function.optString("description"))
                    .put("input_schema", function.optJSONObject("parameters") ?: JSONObject().put("type", "object")),
            )
        }
        return result
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

    private fun JSONArray.toResponsesTools(): JSONArray {
        val result = JSONArray()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val function = obj.optJSONObject("function") ?: continue
            val name = function.optString("name").takeIf { it.isNotBlank() } ?: continue
            result.put(
                JSONObject()
                    .put("type", "function")
                    .put("name", name)
                    .put("description", function.optString("description"))
                    .put("parameters", function.optJSONObject("parameters") ?: JSONObject().put("type", "object"))
            )
        }
        return result
    }

    private fun List<ChatMessage>.toResponsesInput(): JSONArray {
        val array = JSONArray()
        forEach { message ->
            when (message.role) {
                "system" -> {
                    val text = message.content.orEmpty()
                    if (text.isNotBlank()) {
                        array.put(JSONObject()
                            .put("type", "message")
                            .put("role", "system")
                            .put("content", JSONArray().put(
                                JSONObject().put("type", "input_text").put("text", text)
                            )))
                    }
                }
                "tool" -> {
                    val callId = message.toolCallId.orEmpty()
                    if (callId.isNotBlank()) {
                        array.put(JSONObject()
                            .put("type", "function_call_output")
                            .put("call_id", callId)
                            .put("output", message.content.orEmpty()))
                    }
                }
                "assistant" -> {
                    val text = message.content.orEmpty()
                    if (text.isNotBlank()) {
                        array.put(JSONObject()
                            .put("type", "message")
                            .put("role", "assistant")
                            .put("content", JSONArray().put(
                                JSONObject().put("type", "output_text").put("text", text)
                            )))
                    }
                    message.toolCalls.forEach { call ->
                        array.put(JSONObject()
                            .put("type", "function_call")
                            .put("call_id", call.id)
                            .put("name", call.name)
                            .put("arguments", call.arguments.ifBlank { "{}" }))
                    }
                }
                else -> {
                    val parts = JSONArray()
                    val text = message.content.orEmpty()
                    if (text.isNotBlank()) {
                        parts.put(JSONObject().put("type", "input_text").put("text", text))
                    }
                    if (message.mediaPath != null && message.mediaMimeType != null &&
                        message.mediaMimeType.startsWith("image/")
                    ) {
                        val file = java.io.File(message.mediaPath)
                        if (file.exists() && file.isFile) {
                            val base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                            parts.put(JSONObject()
                                .put("type", "input_image")
                                .put("image_url", "data:${message.mediaMimeType};base64,$base64"))
                        }
                    }
                    if (parts.length() > 0) {
                        array.put(JSONObject()
                            .put("type", "message")
                            .put("role", "user")
                            .put("content", parts))
                    }
                }
            }
        }
        return array.dedupeResponsesInput()
    }

    private fun JSONArray.dedupeResponsesInput(): JSONArray {
        // Mirror opencode-cc sanitize: first function_call per call_id wins,
        // last function_call_output per call_id wins.
        val result = JSONArray()
        val outputIndexByCallId = mutableMapOf<String, Int>()
        val seenCalls = mutableSetOf<String>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "function_call" -> {
                    val callId = item.optString("call_id").ifBlank { item.optString("id") }
                    if (callId.isBlank() || seenCalls.add(callId)) {
                        result.put(item)
                    }
                }
                "function_call_output" -> {
                    val callId = item.optString("call_id").ifBlank { item.optString("id") }
                    if (callId.isBlank()) {
                        result.put(item)
                    } else {
                        val existing = outputIndexByCallId[callId]
                        if (existing == null) {
                            outputIndexByCallId[callId] = result.length()
                            result.put(item)
                        } else {
                            result.put(existing, item)
                        }
                    }
                }
                else -> result.put(item)
            }
        }
        return result
    }

    private class ResponsesFunctionCallBuilder {
        var itemId: String? = null
        var callId: String? = null
        var name: String? = null
        val arguments = StringBuilder()

        fun appendArguments(delta: String) {
            arguments.append(delta)
        }

        fun setArguments(value: String) {
            arguments.clear()
            arguments.append(value)
        }

        fun buildOrNull(): CompletedToolCall? {
            val finalId = callId?.takeIf { it.isNotBlank() } ?: return null
            val finalName = name?.takeIf { it.isNotBlank() } ?: return null
            return CompletedToolCall(finalId, finalName, arguments.toString())
        }
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
