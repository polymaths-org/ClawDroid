package com.clawdroid.app.core.channels.email

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.clawdroid.app.core.channels.AuthenticatedChannel
import com.clawdroid.app.core.channels.AuthState
import com.clawdroid.app.core.channels.ChannelMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class EmailChannel : AuthenticatedChannel {

    override val type: String = "email"
    override var isConnected: Boolean = false
        private set

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var context: Context? = null
    private var user: String = ""
    private var pass: String = ""

    companion object {
        private const val TAG = "EmailChannel"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            user = config["user"] ?: ""
            pass = config["pass"] ?: ""
            if (user.isBlank()) throw IllegalArgumentException("Email user is required")
            isConnected = true
            _authState.value = AuthState.Connected()
            Log.i(TAG, "Email channel configured for $user")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun authenticate(credentials: Map<String, String>): Result<AuthState> = withContext(Dispatchers.IO) {
        runCatching {
            user = credentials["user"] ?: throw IllegalArgumentException("Email user required")
            pass = credentials["pass"] ?: throw IllegalArgumentException("Email password required")
            isConnected = true
            _authState.value = AuthState.Connected()
            AuthState.Connected()
        }
    }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (user.isBlank()) throw IllegalStateException("Email user not configured")
            42L
        }
    }

    override suspend fun revoke() {
        user = ""; pass = ""
        isConnected = false
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = context ?: throw IllegalStateException("Context not available — use setContext()")
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$target")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(target))
                putExtra(Intent.EXTRA_SUBJECT, "ClawDroid Agent")
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
            Log.i(TAG, "Opened email intent for $target")
        }.map { }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        return emptyList()
    }

    fun setContext(ctx: Context) { context = ctx }
}
