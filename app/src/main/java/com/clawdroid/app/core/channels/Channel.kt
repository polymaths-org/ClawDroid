package com.clawdroid.app.core.channels

/**
 * A communication channel that the agent can use to send/receive messages.
 * Examples: WhatsApp, Telegram, SMS, Email, Slack.
 */
interface Channel {
    val type: String
    val isConnected: Boolean

    /** Initialize the channel (connect, authenticate). */
    suspend fun connect(config: Map<String, String>): Result<Unit>

    /** Disconnect and clean up. */
    suspend fun disconnect()

    /** Send a text message through this channel. */
    suspend fun sendMessage(target: String, text: String): Result<Unit>

    /**
     * Poll for new messages. Returns list of (sender, text) pairs.
     * The agent processes these as new user inputs.
     */
    suspend fun pollMessages(): List<ChannelMessage>
}

data class ChannelMessage(
    val id: String,
    val sender: String,
    val text: String,
    val channelType: String,
    val timestamp: Long = System.currentTimeMillis(),
)
