package com.clawdroid.app.core.channels.slack

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

class SlackChannel : AuthenticatedChannel {

    override val type: String = "slack"
    override var isConnected: Boolean = false
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var botToken: String = ""
    private var signingSecret: String = ""
    private var lastTs: String = ""

    companion object {
        private const val TAG = "SlackChannel"
        private const val API_BASE = "https://slack.com/api"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            botToken = config["bot_token"] ?: ""
            signingSecret = config["signing_secret"] ?: ""
            if (botToken.isBlank()) throw IllegalArgumentException("Bot token is required")
            isConnected = true
            _authState.value = AuthState.Connected()
            Log.i(TAG, "Slack channel configured")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun authenticate(credentials: Map<String, String>): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            val token = credentials["bot_token"] ?: throw IllegalArgumentException("Bot token required")
            val secret = credentials["signing_secret"] ?: ""
            val response = slackGet("auth.test", token)
            val ok = response.optBoolean("ok", false)
            if (!ok) throw IllegalArgumentException("Invalid Slack token")
            botToken = token; signingSecret = secret
            isConnected = true
            _authState.value = AuthState.Connected()
            AuthState.Connected()
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = slackGet("auth.test")
            if (!response.optBoolean("ok", false)) throw IllegalStateException("Slack API error")
            System.currentTimeMillis() - start
        }
    }

    override suspend fun revoke() {
        botToken = ""; signingSecret = ""
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("channel", target)
                put("text", text)
                put("mrkdwn", true)
            }
            val response = slackPost("chat.postMessage", body)
            if (!response.optBoolean("ok", false)) {
                throw IllegalStateException("Slack send failed: ${response.optString("error")}")
            }
            Log.i(TAG, "Sent message to $target")
        }.map { }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected || botToken.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val channelsResp = slackGet("conversations.list?types=public_channel,im&limit=20")
                if (!channelsResp.optBoolean("ok", false)) return@withContext emptyList()

                val channels = channelsResp.optJSONArray("channels") ?: return@withContext emptyList()
                val allMessages = mutableListOf<ChannelMessage>()

                for (i in 0 until channels.length()) {
                    val ch = channels.getJSONObject(i)
                    val chId = ch.optString("id", "")
                    val chName = ch.optString("name", "") ?: chId

                    val historyResp = slackGet("conversations.history?channel=$chId&limit=5&oldest=$lastTs")
                    if (!historyResp.optBoolean("ok", false)) continue
                    val msgs = historyResp.optJSONArray("messages") ?: continue

                    for (j in 0 until msgs.length()) {
                        val msg = msgs.getJSONObject(j)
                        val ts = msg.optString("ts", "0")
                        val text = msg.optString("text", "")
                        val user = msg.optString("user", "unknown")
                        if (text.isBlank() || msg.optBoolean("bot_id", false)) continue
                        if (ts > lastTs) lastTs = ts
                        allMessages.add(ChannelMessage(
                            id = ts,
                            sender = user,
                            text = text,
                            channelType = type,
                            timestamp = (ts.toDoubleOrNull() ?: 0.0).toLong() * 1000L,
                        ))
                    }
                }
                allMessages
            } catch (e: Exception) {
                Log.w(TAG, "Poll failed", e)
                emptyList()
            }
        }
    }

    private fun slackGet(endpoint: String, overrideToken: String? = null): JSONObject {
        val token = overrideToken ?: botToken
        val url = URL("$API_BASE/$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 10_000
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            JSONObject(reader.readText())
        } finally { conn.disconnect() }
    }

    private fun slackPost(endpoint: String, body: JSONObject): JSONObject {
        val url = URL("$API_BASE/$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $botToken")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 10_000
        return try {
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            JSONObject(reader.readText())
        } finally { conn.disconnect() }
    }
}
