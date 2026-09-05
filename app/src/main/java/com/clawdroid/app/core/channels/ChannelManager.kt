package com.clawdroid.app.core.channels

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.agent.AgentConfigLoader
import com.clawdroid.app.core.channels.whatsapp.WhatsAppChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages all communication channels for the agent.
 * Handles connection lifecycle and polls for incoming messages.
 */
class ChannelManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channels = mutableMapOf<String, Channel>()
    private var pollJob: Job? = null

    private val _incomingMessages = MutableStateFlow<List<ChannelMessage>>(emptyList())
    val incomingMessages: StateFlow<List<ChannelMessage>> = _incomingMessages.asStateFlow()

    private val _connectionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connectionStatus: StateFlow<Map<String, Boolean>> = _connectionStatus.asStateFlow()

    companion object {
        private const val TAG = "ChannelManager"
        private const val POLL_INTERVAL_MS = 5000L
    }

    /**
     * Connect all enabled channels from agent config.
     */
    suspend fun connectAll(): Map<String, Result<Unit>> {
        val config = AgentConfigLoader.load(context)
        val results = mutableMapOf<String, Result<Unit>>()

        config.channels.filter { it.enabled }.forEach { chConfig ->
            val channel = createChannel(chConfig.type)
            if (channel != null) {
                val result = channel.connect(chConfig.config)
                channels[chConfig.type] = channel
                results[chConfig.type] = result
                Log.i(TAG, "Channel '${chConfig.type}': ${if (result.isSuccess) "connected" else "failed"}")
            } else {
                results[chConfig.type] = Result.failure(Exception("No handler for channel type: ${chConfig.type}"))
            }
        }

        _connectionStatus.value = channels.mapValues { it.value.isConnected }
        startPolling()
        return results
    }

    fun disconnectAll() {
        pollJob?.cancel()
        pollJob = null
        scope.launch {
            channels.values.forEach { it.disconnect() }
            channels.clear()
            _connectionStatus.value = emptyMap()
        }
    }

    /**
     * Send a message through a specific channel.
     */
    suspend fun sendMessage(channelType: String, target: String, text: String): Result<Unit> {
        val channel = channels[channelType] ?: return Result.failure(Exception("Channel not connected: $channelType"))
        return channel.sendMessage(target, text)
    }

    /**
     * Broadcast a message to all connected channels.
     */
    suspend fun broadcast(target: String, text: String) {
        channels.values.forEach { channel ->
            try {
                channel.sendMessage(target, text)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send to ${channel.type}", e)
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val allMessages = mutableListOf<ChannelMessage>()
                channels.values.forEach { channel ->
                    try {
                        val msgs = channel.pollMessages()
                        allMessages.addAll(msgs)
                    } catch (e: Exception) {
                        Log.w(TAG, "Poll failed for ${channel.type}", e)
                    }
                }
                if (allMessages.isNotEmpty()) {
                    _incomingMessages.value = allMessages
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun createChannel(type: String): Channel? = when (type) {
        "whatsapp" -> WhatsAppChannel(context)
        "sms" -> SmsChannel(context)
        else -> null
    }
}

/**
 * Android SMS channel — reads SMS inbox.
 */
class SmsChannel(private val context: Context) : Channel {
    override val type: String = "sms"
    override var isConnected: Boolean = false
        private set

    private var lastReadId: String = ""

    override suspend fun connect(config: Map<String, String>): Result<Unit> = runCatching {
        isConnected = true
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = runCatching {
        val smsUri = android.net.Uri.parse("smsto:$target")
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", text)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected) return emptyList()
        return try {
            val uri = android.provider.Telephony.Sms.Inbox.CONTENT_URI
            val cursor = context.contentResolver.query(uri, null, null, null, "${android.provider.Telephony.Sms.Inbox.DATE} DESC LIMIT 5")
            val messages = mutableListOf<ChannelMessage>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox._ID))
                    if (id == lastReadId) break
                    val address = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.ADDRESS))
                    val body = it.getString(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.BODY))
                    val date = it.getLong(it.getColumnIndexOrThrow(android.provider.Telephony.Sms.Inbox.DATE))
                    messages.add(ChannelMessage(id, address, body, "sms", date))
                }
                if (messages.isNotEmpty()) lastReadId = messages.first().id
            }
            messages.reversed()
        } catch (_: Exception) { emptyList() }
    }
}
