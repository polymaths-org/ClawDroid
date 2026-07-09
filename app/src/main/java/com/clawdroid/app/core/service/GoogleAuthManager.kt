package com.clawdroid.app.core.service

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleAuthManager {
    private const val TAG = "GoogleAuthManager"

    // In-memory cache for the current session's access token
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAt: Long = 0
    @Volatile
    var lastError: String = ""
        private set

    val isGoogleConnected: Boolean
        get() = AppConfigManager.googleRefreshToken.isNotBlank()

    /**
     * Exchanges the authorization code received from Google Sign-In for access & refresh tokens.
     */
    suspend fun exchangeAuthCode(authCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Exchanging auth code for tokens...")
            val clientID = AppConfigManager.googleClientId
            val clientSecret = AppConfigManager.googleClientSecret
            if (clientID.isBlank() || clientSecret.isBlank()) {
                setError("Google OAuth client ID and client secret are required.")
                return@withContext false
            }

            val postData = buildParams(
                "code" to authCode,
                "client_id" to clientID,
                "client_secret" to clientSecret,
                "grant_type" to "authorization_code"
            )

            val json = postRequest(postData) ?: return@withContext false

            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600)
            val existingRefreshToken = AppConfigManager.googleRefreshToken

            if (accessToken.isNullOrBlank()) {
                setError("Google did not return an access token.")
                Log.e(TAG, "Failed to retrieve access token: $json")
                return@withContext false
            }
            if (refreshToken.isNullOrBlank() && existingRefreshToken.isBlank()) {
                setError("Google did not return a refresh token. Disconnect Google, sign in again, and approve offline access.")
                Log.e(TAG, "Failed to retrieve refresh token: $json")
                return@withContext false
            }

            cachedAccessToken = accessToken
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)

            // Save Refresh Token persistently
            if (!refreshToken.isNullOrBlank()) {
                AppConfigManager.googleRefreshToken = refreshToken
            }
            clearError()
            
            Log.i(TAG, "Successfully exchanged auth code. Token expires in $expiresIn seconds.")
            return@withContext true
        } catch (e: Exception) {
            setError(e.localizedMessage ?: "Google OAuth exchange failed.")
            Log.e(TAG, "Error exchanging auth code", e)
            return@withContext false
        }
    }

    /**
     * Fetches a valid Access Token. Refreshes if expired or close to expiration (within 5 minutes).
     */
    suspend fun getOrRefreshAccessToken(): String? {
        val cached = cachedAccessToken
        val now = System.currentTimeMillis()
        
        // Return cached token if valid for at least 5 more minutes
        if (cached != null && tokenExpiresAt > now + 300_000) {
            return cached
        }

        val refreshToken = AppConfigManager.googleRefreshToken
        if (refreshToken.isBlank()) {
            setError("Google is not connected. Sign in again from Connections.")
            Log.w(TAG, "No refresh token available, user is not logged in.")
            return null
        }

        return withContext(Dispatchers.IO) {
            synchronized(this) {
                // Double check pattern
                val cachedDouble = cachedAccessToken
                if (cachedDouble != null && tokenExpiresAt > System.currentTimeMillis() + 300_000) {
                    return@synchronized cachedDouble
                }

                try {
                    Log.i(TAG, "Refreshing access token...")
                    val clientID = AppConfigManager.googleClientId
                    val clientSecret = AppConfigManager.googleClientSecret
                    if (clientID.isBlank() || clientSecret.isBlank()) {
                        setError("Google OAuth client ID and client secret are required.")
                        return@synchronized null
                    }

                    val postData = buildParams(
                        "client_id" to clientID,
                        "client_secret" to clientSecret,
                        "refresh_token" to refreshToken,
                        "grant_type" to "refresh_token"
                    )

                    val json = postRequest(postData) ?: return@synchronized null
                    val accessToken = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600)

                    if (accessToken.isNullOrBlank()) {
                        setError("Google did not return a refreshed access token.")
                        Log.e(TAG, "Failed to refresh access token: $json")
                        return@synchronized null
                    }

                    cachedAccessToken = accessToken
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                    clearError()
                    Log.i(TAG, "Access token refreshed successfully.")
                    return@synchronized accessToken
                } catch (e: Exception) {
                    setError(e.localizedMessage ?: "Google token refresh failed.")
                    Log.e(TAG, "Error refreshing access token", e)
                    return@synchronized null
                }
            }
        }
    }

    suspend fun validateConnection(): Boolean {
        return getOrRefreshAccessToken() != null
    }

    fun disconnect() {
        cachedAccessToken = null
        tokenExpiresAt = 0
        AppConfigManager.googleRefreshToken = ""
        AppConfigManager.googleAccountEmail = ""
        clearError()
        Log.i(TAG, "Google account disconnected.")
    }

    private fun setError(message: String) {
        lastError = message
    }

    private fun clearError() {
        lastError = ""
    }

    private fun buildParams(vararg params: Pair<String, String>): String {
        return params.filter { it.second.isNotBlank() }.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
    }

    private fun postRequest(postData: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://oauth2.googleapis.com/token")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(postData)
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                clearError()
                JSONObject(responseText)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                setError(parseOAuthError(code, errorText))
                Log.e(TAG, "OAuth HTTP Error $code: $errorText")
                null
            }
        } catch (e: Exception) {
            setError(e.localizedMessage ?: "Network error during Google OAuth request.")
            Log.e(TAG, "Network error during OAuth token request", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOAuthError(code: Int, errorText: String): String {
        val parsed = runCatching { JSONObject(errorText) }.getOrNull()
        val error = parsed?.optString("error").orEmpty()
        val description = parsed?.optString("error_description").orEmpty()
        return buildString {
            append("Google OAuth HTTP ")
            append(code)
            if (error.isNotBlank()) {
                append(": ")
                append(error)
            }
            if (description.isNotBlank()) {
                append(" - ")
                append(description)
            } else if (errorText.isNotBlank() && error.isBlank()) {
                append(": ")
                append(errorText.take(240))
            }
        }
    }
}
