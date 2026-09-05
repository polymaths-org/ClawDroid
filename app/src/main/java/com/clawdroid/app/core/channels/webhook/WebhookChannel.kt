package com.clawdroid.app.core.channels.webhook

import android.util.Log
import com.clawdroid.app.core.channels.AuthenticatedChannel
import com.clawdroid.app.core.channels.AuthState
import com.clawdroid.app.core.channels.ChannelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class WebhookChannel : AuthenticatedChannel {

    override val type: String = "webhook"
    override var isConnected: Boolean = false
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var webhookUrl: String = ""
    private var secret: String = ""
    private val receivedMessages = mutableListOf<ChannelMessage>()
    private var lastPollTimestamp: Long = 0L

    companion object {
        private const val TAG = "WebhookChannel"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            webhookUrl = config["url"] ?: ""
            secret = config["secret"] ?: ""
            if (webhookUrl.isBlank()) throw IllegalArgumentException("Webhook URL is required")
            isConnected = true
            _authState.value = AuthState.Connected()
            Log.i(TAG, "Webhook channel configured for $webhookUrl")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun authenticate(credentials: Map<String, String>): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            val url = credentials["url"] ?: throw IllegalArgumentException("Webhook URL required")
            val sec = credentials["secret"] ?: ""
            webhookUrl = url; secret = sec
            isConnected = true
            _authState.value = AuthState.Connected()
            AuthState.Connected()
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val testPayload = JSONObject().apply {
                put("type", "test")
                put("timestamp", System.currentTimeMillis())
            }
            val response = webhookPost(testPayload)
            if (response != null && response.optString("status") == "error") {
                throw IllegalStateException("Webhook test failed: ${response.optString("message")}")
            }
            System.currentTimeMillis() - start
        }
    }

    override suspend fun revoke() {
        webhookUrl = ""; secret = ""
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("target", target)
                put("text", text)
                put("type", "message")
                put("source", "clawdroid")
            }
            val response = webhookPost(payload)
            if (response != null && response.optString("status") == "error") {
                throw IllegalStateException("Webhook send failed: ${response.optString("message")}")
            }
            Log.i(TAG, "Sent webhook message to $target")
        }.map { }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected) return emptyList()
        synchronized(receivedMessages) {
            val newMessages = receivedMessages.filter { it.timestamp > lastPollTimestamp }
            if (newMessages.isNotEmpty()) lastPollTimestamp = newMessages.maxOf { it.timestamp }
            return newMessages.toList()
        }
    }

    fun ingestWebhook(sender: String, text: String): ChannelMessage {
        val msg = ChannelMessage(
            id = "wh_${System.currentTimeMillis()}_${sender.hashCode().toUInt()}",
            sender = sender,
            text = text,
            channelType = type,
        )
        synchronized(receivedMessages) {
            receivedMessages.add(msg)
        }
        return msg
    }

    private fun webhookPost(payload: JSONObject): JSONObject? {
        val url = URL(webhookUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (secret.isNotBlank()) {
            conn.setRequestProperty("X-Webhook-Secret", secret)
        }
        conn.doOutput = true
        conn.connectTimeout = 10_000
        return try {
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val text = reader.readText()
            if (text.isNotBlank()) JSONObject(text) else null
        } finally { conn.disconnect() }
    }
}
