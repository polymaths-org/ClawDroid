package com.clawdroid.app.core.channels.whatsapp

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.channels.Channel
import com.clawdroid.app.core.channels.ChannelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WhatsApp Web channel.
 *
 * The agent connects via WhatsApp Web (web.whatsapp.com) using the app's
 * WebView / browser tool. The channel itself manages the connection state
 * and provides a polling mechanism for new messages.
 *
 * For the MVP, this provides the scaffolding. Actual WhatsApp message
 * sending/receiving is handled by the agent's browser tool navigating
 * web.whatsapp.com.
 */
class WhatsAppChannel(private val context: Context) : Channel {

    override val type: String = "whatsapp"
    override var isConnected: Boolean = false
        private set

    private var phoneNumber: String = ""
    private var lastMessageTimestamp: Long = 0L

    companion object {
        private const val TAG = "WhatsAppChannel"
        private const val WHATSAPP_WEB_URL = "https://web.whatsapp.com"
        private const val SEND_URL_FORMAT = "https://api.whatsapp.com/send?phone=%s&text=%s"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            phoneNumber = config["phone"] ?: ""
            isConnected = true
        }.onSuccess {
            Log.i(TAG, "WhatsApp channel configured for phone: $phoneNumber")
        }
    }

    override suspend fun disconnect() {
        isConnected = false
        Log.i(TAG, "WhatsApp channel disconnected")
    }

    /**
     * Sends a WhatsApp message via the WhatsApp API URL or WhatsApp Web.
     * The agent should use the browser tool to actually send the message.
     * This provides a direct API-based fallback for simple messages.
     */
    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val phone = target.replace(Regex("[^\\d]"), "")
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            val url = SEND_URL_FORMAT.format(phone, encoded)

            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }.onSuccess {
            Log.i(TAG, "Opened WhatsApp send intent for ${target.replace(Regex("[^\\d]"), "")}")
        }
    }

    /**
     * Polling is not directly supported without the WhatsApp Web session.
     * Returns empty — the agent uses browse_web to interact with WhatsApp Web
     * and reads messages from the page content.
     */
    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected) return emptyList()
        // In a full implementation, the browser tool's extracted content from
        // web.whatsapp.com would be parsed here. For now, polling is passive
        // and the agent drives the interaction.
        return emptyList()
    }
}
