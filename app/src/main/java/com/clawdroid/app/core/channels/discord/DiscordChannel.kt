package com.clawdroid.app.core.channels.discord

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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DiscordChannel : AuthenticatedChannel {

    override val type: String = "discord"
    override var isConnected: Boolean = false
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var botToken: String = ""
    private var lastMessageId: String = ""

    companion object {
        private const val TAG = "DiscordChannel"
        private const val API_BASE = "https://discord.com/api/v10"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            botToken = config["bot_token"] ?: ""
            if (botToken.isBlank()) throw IllegalArgumentException("Bot token is required")
            isConnected = true
            _authState.value = AuthState.Connected()
            Log.i(TAG, "Discord channel configured")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun authenticate(credentials: Map<String, String>): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            val token = credentials["bot_token"] ?: throw IllegalArgumentException("Bot token required")
            val response = discordGet("/users/@me", token)
            val json = response as? JSONObject
            if (json == null || !json.has("id")) throw IllegalArgumentException("Invalid Discord bot token")
            botToken = token
            isConnected = true
            _authState.value = AuthState.Connected()
            AuthState.Connected()
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val response = discordGet("/users/@me")
            val json = response as? JSONObject
            if (json == null || !json.has("id")) throw IllegalStateException("Discord API error")
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
            val body = JSONObject().apply { put("content", text) }
            val response = discordPost("/channels/$target/messages", body)
            val json = response as? JSONObject
            if (json == null || !json.has("id")) {
                throw IllegalStateException("Discord send failed: ${json?.optString("message") ?: "unknown error"}")
            }
            Log.i(TAG, "Sent message to channel $target")
        }.map { }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected || botToken.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val guildsResp = discordGet("/users/@me/guilds")
                val guilds = guildsResp as? JSONArray ?: return@withContext emptyList()
                val allMessages = mutableListOf<ChannelMessage>()

                for (i in 0 until guilds.length()) {
                    val guild = guilds.getJSONObject(i)
                    val guildId = guild.optString("id", "")

                    val channelsResp = discordGet("/guilds/$guildId/channels")
                    val channels = channelsResp as? JSONArray ?: continue
                    for (j in 0 until channels.length()) {
                        val ch = channels.getJSONObject(j)
                        if (ch.optInt("type", -1) != 0) continue
                        val chId = ch.optString("id", "")

                        val after = if (lastMessageId.isNotBlank()) "&after=$lastMessageId" else ""
                        val msgsResp = discordGet("/channels/$chId/messages?limit=5$after")
                        val msgs = msgsResp as? JSONArray ?: continue

                        for (k in 0 until msgs.length()) {
                            val msg = msgs.getJSONObject(k)
                            val msgId = msg.optString("id", "")
                            val content = msg.optString("content", "")
                            val author = msg.optJSONObject("author")
                            val username = author?.optString("username", "unknown") ?: "unknown"
                            val isBot = author?.optBoolean("bot", false) ?: false
                            val ts = msg.optString("timestamp", "")

                            if (content.isBlank() || isBot) continue
                            if (msgId > lastMessageId) lastMessageId = msgId

                            allMessages.add(ChannelMessage(
                                id = msgId,
                                sender = username,
                                text = content,
                                channelType = type,
                                timestamp = parseDiscordTimestamp(ts),
                            ))
                        }
                    }
                }
                allMessages
            } catch (e: Exception) {
                Log.w(TAG, "Poll failed", e)
                emptyList()
            }
        }
    }

    private fun parseDiscordTimestamp(ts: String): Long = try {
        java.time.Instant.parse(ts).toEpochMilli()
    } catch (_: Exception) { System.currentTimeMillis() }

    private fun discordGet(endpoint: String, overrideToken: String? = null): Any {
        val token = overrideToken ?: botToken
        val url = URL("$API_BASE$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bot $token")
        conn.connectTimeout = 10_000
        return try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val text = reader.readText().trim()
            if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        } finally { conn.disconnect() }
    }

    private fun discordPost(endpoint: String, body: JSONObject): Any {
        val url = URL("$API_BASE$endpoint")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bot $botToken")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 10_000
        return try {
            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(body.toString())
            writer.flush()
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val text = reader.readText().trim()
            if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
        } finally { conn.disconnect() }
    }
}
