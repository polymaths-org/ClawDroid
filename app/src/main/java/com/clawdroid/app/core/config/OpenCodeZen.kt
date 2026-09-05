package com.clawdroid.app.core.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object OpenCodeZen {
    const val PROVIDER_ID = "opencode_zen"
    const val BASE_URL = "https://opencode.ai/zen/v1"
    const val DEFAULT_MODEL = "big-pickle"

    // Stock OpenCode CLI identity. Never send ClawDroid here: free-tier
    // access and prompt-cache routing depend on this exact shape.
    const val STOCK_USER_AGENT = "opencode/latest/1.18.29/cli"
    const val STOCK_CLIENT = "cli"

    fun isZen(provider: String, baseUrl: String): Boolean {
        if (provider.equals(PROVIDER_ID, ignoreCase = true)) return true
        if (provider.equals("opencode", ignoreCase = true)) return true
        return baseUrl.contains("opencode.ai/zen", ignoreCase = true)
    }

    // Stable per conversation. The gateway uses this for sticky routing.
    fun sessionIdForConversation(conversationId: String): String {
        if (conversationId.isNotBlank()) return conversationId
        return UUID.randomUUID().toString()
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val endpoint = baseUrl.trimEnd('/') + "/models"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("x-api-key", apiKey)
                }
                setRequestProperty("User-Agent", STOCK_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $code: $errorText")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            val ids = (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            ids.sortedWith(compareBy({ !isFree(it) }, { it }))
        }

    fun isFree(id: String): Boolean =
        id == "big-pickle" || id.endsWith("-free", ignoreCase = true)

    // Meta muse-spark models are served ONLY on Zen /v1/responses.
    // Any translation to /v1/chat/completions 500s upstream.
    // Mirrors opencode-cc internal/proxy/routing.go IsResponsesNativeModel.
    fun isResponsesNativeModel(model: String): Boolean {
        var id = model.trim().lowercase()
        val slash = id.indexOf('/')
        if (slash >= 0) id = id.substring(slash + 1)
        return id.startsWith("muse-spark")
    }

    data class ChatProbe(val ok: Boolean, val reply: String?, val error: String?)

    suspend fun testChat(baseUrl: String, apiKey: String, model: String): ChatProbe =
        withContext(Dispatchers.IO) {
            try {
                if (isResponsesNativeModel(model)) {
                    return@withContext testResponses(baseUrl, apiKey, model)
                }
                val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
                val sessionId = UUID.randomUUID().toString()
                val payload = JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("max_tokens", 16)
                    .put(
                        "messages",
                        org.json.JSONArray().put(
                            JSONObject().put("role", "user").put("content", "Reply with OK")
                        ),
                    )
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                        setRequestProperty("x-api-key", apiKey)
                    }
                    setRequestProperty("User-Agent", STOCK_USER_AGENT)
                    setRequestProperty("x-opencode-client", STOCK_CLIENT)
                    setRequestProperty("x-opencode-session", sessionId)
                    setRequestProperty("x-opencode-request", UUID.randomUUID().toString())
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    return@withContext ChatProbe(false, null, "HTTP $code: ${errorText.take(200)}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val reply = JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                if (reply == null) ChatProbe(false, null, "Empty reply: ${body.take(200)}")
                else ChatProbe(true, reply.trim().take(200), null)
            } catch (t: Throwable) {
                ChatProbe(false, null, t.message ?: t::class.java.simpleName)
            }
        }

    private fun testResponses(baseUrl: String, apiKey: String, model: String): ChatProbe {
        try {
            val endpoint = baseUrl.trimEnd('/') + "/responses"
            val sessionId = UUID.randomUUID().toString()
            val payload = JSONObject()
                .put("model", model)
                .put("stream", false)
                .put("input", "Reply with OK")
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("x-api-key", apiKey)
                }
                setRequestProperty("User-Agent", STOCK_USER_AGENT)
                setRequestProperty("x-opencode-client", STOCK_CLIENT)
                setRequestProperty("x-opencode-session", sessionId)
                setRequestProperty("x-opencode-request", UUID.randomUUID().toString())
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return ChatProbe(false, null, "HTTP $code: ${errorText.take(200)}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val reply = extractResponsesText(JSONObject(body))?.takeIf { it.isNotBlank() }
            return if (reply == null) ChatProbe(false, null, "Empty reply: ${body.take(200)}")
            else ChatProbe(true, reply.trim().take(200), null)
        } catch (t: Throwable) {
            return ChatProbe(false, null, t.message ?: t::class.java.simpleName)
        }
    }

    fun extractResponsesText(body: JSONObject): String? {
        val output = body.optJSONArray("output") ?: return null
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val partText = part.optString("text")
                if (partText.isNotBlank()) {
                    if (text.isNotEmpty()) text.append("\n")
                    text.append(partText)
                }
            }
        }
        return text.toString().takeIf { it.isNotBlank() }
    }
}
