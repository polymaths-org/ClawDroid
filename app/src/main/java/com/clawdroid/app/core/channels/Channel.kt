package com.clawdroid.app.core.channels

import kotlinx.coroutines.flow.StateFlow

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

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Authenticating : AuthState()
    data object AwaitingUserAction : AuthState()
    data class Connected(
        val account: String = "",
        val expiresAt: Long? = null,
    ) : AuthState()
    data class Failed(val error: String) : AuthState()
}

interface AuthenticatedChannel : Channel {
    val authState: StateFlow<AuthState>

    suspend fun authenticate(credentials: Map<String, String>): Result<AuthState>

    suspend fun testConnection(): Result<Long>

    suspend fun revoke()
}
