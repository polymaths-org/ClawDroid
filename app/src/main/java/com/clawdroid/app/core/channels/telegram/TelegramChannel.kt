package com.clawdroid.app.core.channels.telegram

import android.util.Log
import com.clawdroid.app.core.channels.AuthenticatedChannel
import com.clawdroid.app.core.channels.AuthState
import com.clawdroid.app.core.channels.ChannelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TelegramChannel : AuthenticatedChannel {

    override val type: String = "telegram"
    override var isConnected: Boolean = false
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var botToken: String = ""
    private var lastUpdateId: Long = 0L
    private val allowedChats = mutableSetOf<String>()

    companion object {
        private const val TAG = "TelegramChannel"
        private const val POLL_TIMEOUT = 30
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            botToken = config["bot_token"] ?: ""
            val chats = config["allowed_chats"] ?: ""
            allowedChats.clear()
            if (chats.isNotBlank()) {
                allowedChats.addAll(chats.split(",").map { it.trim() })
            }
            if (botToken.isBlank()) throw IllegalArgumentException("Bot token is required")
            isConnected = true
            _authState.value = AuthState.Connected()
            Log.i(TAG, "Telegram channel configured")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        _authState.value = AuthState.Unauthenticated
        Log.i(TAG, "Telegram channel disconnected")
    }

    override suspend fun authenticate(credentials: Map<String, String>): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            val token = credentials["bot_token"] ?: throw IllegalArgumentException("Bot token required")
            val response = apiGet("getMe", token)
            val ok = response.optBoolean("ok", false)
            if (!ok) throw IllegalArgumentException("Invalid bot token")
            botToken = token
            _authState.value = AuthState.Connected()
            isConnected = true
            AuthState.Connected()
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = apiGet("getMe")
            val ok = response.optBoolean("ok", false)
            if (!ok) throw IllegalStateException("Telegram API returned not ok")
            System.currentTimeMillis() - start
        }
    }

    override suspend fun revoke() {
        botToken = ""
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val chatId = URLEncoder.encode(target, "UTF-8")
            val textEncoded = URLEncoder.encode(text, "UTF-8")
            val response = apiGet("sendMessage?chat_id=$chatId&text=$textEncoded&parse_mode=Markdown")
            val ok = response.optBoolean("ok", false)
            if (!ok) {
                val desc = response.optString("description", "unknown error")
                throw IllegalStateException("Telegram send failed: $desc")
            }
            Log.i(TAG, "Sent message to $target")
        }.map { }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected || botToken.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val params = "offset=${lastUpdateId + 1}&timeout=$POLL_TIMEOUT&allowed_updates=[\"message\"]"
                val response = apiGet("getUpdates?$params")
                val ok = response.optBoolean("ok", false)
                if (!ok) return@withContext emptyList()

                val results = response.optJSONArray("result") ?: return@withContext emptyList()
                val messages = mutableListOf<ChannelMessage>()

                for (i in 0 until results.length()) {
                    val update = results.getJSONObject(i)
                    val updateId = update.optLong("update_id", 0L)
                    if (updateId > lastUpdateId) lastUpdateId = updateId

                    val msg = update.optJSONObject("message") ?: continue
                    val chat = msg.optJSONObject("chat") ?: continue
                    val chatId = chat.optLong("id", 0L).toString()
                    val chatUsername = chat.optString("username", "")
                    val text = msg.optString("text", "")
                    val msgId = msg.optLong("message_id", 0L).toString()

                    if (text.isBlank()) continue
                    if (allowedChats.isNotEmpty() && chatId !in allowedChats && chatUsername !in allowedChats) continue

                    messages.add(ChannelMessage(
                        id = msgId,
                        sender = chatUsername.ifBlank { chatId },
                        text = text,
                        channelType = type,
                        timestamp = msg.optLong("date", 0L) * 1000L,
                    ))
                }
                messages
            } catch (e: Exception) {
                Log.w(TAG, "Poll failed", e)
                emptyList()
            }
        }
    }

    private fun apiGet(endpoint: String, overrideToken: String? = null): JSONObject {
        val token = overrideToken ?: botToken
        val url = URL("https://api.telegram.org/bot$token/$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val text = reader.readText()
            JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
