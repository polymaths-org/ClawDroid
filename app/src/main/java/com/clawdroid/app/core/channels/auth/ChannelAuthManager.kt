package com.clawdroid.app.core.channels.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.clawdroid.app.core.channels.AuthState
import com.clawdroid.app.core.channels.AuthenticatedChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChannelAuthManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "channel_auth_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun storeCredentials(channelType: String, credentials: Map<String, String>) {
        prefs.edit().apply {
            credentials.forEach { (key, value) ->
                putString("${channelType}_${key}", value)
            }
            putBoolean("${channelType}_configured", true)
            apply()
        }
    }

    fun loadCredentials(channelType: String, vararg keys: String): Map<String, String> {
        if (!prefs.getBoolean("${channelType}_configured", false)) return emptyMap()
        return keys.associateWith { key ->
            prefs.getString("${channelType}_${key}", "") ?: ""
        }
    }

    fun hasCredentials(channelType: String): Boolean =
        prefs.getBoolean("${channelType}_configured", false)

    fun clearCredentials(channelType: String) {
        val all = prefs.getAll() ?: return
        val keysToRemove = all.keys.filter { it.startsWith("${channelType}_") }
        prefs.edit().apply {
            keysToRemove.forEach { remove(it) }
            apply()
        }
    }

    fun getAllConfiguredChannels(): List<String> {
        val all = prefs.getAll() ?: return emptyList()
        return all.keys.filter { it.endsWith("_configured") && all[it] == true }
            .map { it.removeSuffix("_configured") }
    }
}

class AuthStateMachine(
    private val channel: AuthenticatedChannel,
    initial: AuthState = AuthState.Unauthenticated,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isConnected: Boolean get() = _state.value is AuthState.Connected
    val isAuthenticating: Boolean get() = _state.value is AuthState.Authenticating
    val error: String? get() = (_state.value as? AuthState.Failed)?.error

    suspend fun authenticate(credentials: Map<String, String>) {
        _state.value = AuthState.Authenticating
        val result = channel.authenticate(credentials)
        _state.value = result.getOrElse { AuthState.Failed(it.message ?: "Unknown error") }
    }

    suspend fun testConnection(): Result<Long> {
        val result = channel.testConnection()
        if (result.isSuccess) {
            _state.value = AuthState.Connected(expiresAt = null)
        } else {
            _state.value = AuthState.Failed(result.exceptionOrNull()?.message ?: "Connection test failed")
        }
        return result
    }

    fun setAwaitingUserAction() {
        _state.value = AuthState.AwaitingUserAction
    }

    fun reset() {
        _state.value = AuthState.Unauthenticated
    }

    suspend fun revoke() {
        channel.revoke()
        _state.value = AuthState.Unauthenticated
    }
}
