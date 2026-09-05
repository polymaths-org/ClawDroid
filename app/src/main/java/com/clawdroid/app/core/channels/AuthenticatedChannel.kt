package com.clawdroid.app.core.channels

import kotlinx.coroutines.flow.StateFlow

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Authenticating : AuthState()
    data object AwaitingUserAction : AuthState()
    data class Connected(val expiresAt: Long? = null) : AuthState()
    data class Failed(val error: String) : AuthState()
}

interface AuthenticatedChannel : Channel {
    val authState: StateFlow<AuthState>

    suspend fun authenticate(credentials: Map<String, String>): Result<AuthState>

    suspend fun testConnection(): Result<Long>

    suspend fun revoke()
}
