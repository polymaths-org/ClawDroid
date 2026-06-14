package com.clawdroid.app.core.service

import android.util.Base64
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object SpotifyAuthManager {
    private const val TAG = "SpotifyAuthManager"

    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var tokenExpiresAt: Long = 0

    val isConnected: Boolean
        get() = AppConfigManager.spotifyRefreshToken.isNotBlank()

    suspend fun exchangeAuthCode(authCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Exchanging Spotify auth code...")
            val clientID = com.clawdroid.app.BuildConfig.SPOTIFY_OAUTH_CLIENT_ID
            val clientSecret = com.clawdroid.app.BuildConfig.SPOTIFY_OAUTH_CLIENT_SECRET

            val credentials = "$clientID:$clientSecret"
            val basicAuth = "Basic " + Base64.encodeToString(
                credentials.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(authCode, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode("clawdroid://spotify-auth", "UTF-8")

            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://accounts.spotify.com/api/token")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Authorization", basicAuth)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val code = connection.responseCode
                if (code in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val accessToken = json.optString("access_token")
                    val refreshToken = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600)

                    if (!accessToken.isNullOrBlank()) {
                        cachedAccessToken = accessToken
                        tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                        if (!refreshToken.isNullOrBlank()) {
                            AppConfigManager.spotifyRefreshToken = refreshToken
                        }
                        return@withContext true
                    }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e(TAG, "Spotify exchange error $code: $errorText")
                }
            } finally {
                connection?.disconnect()
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging Spotify code", e)
            return@withContext false
        }
    }

    suspend fun getOrRefreshAccessToken(): String? {
        val cached = cachedAccessToken
        val now = System.currentTimeMillis()

        if (cached != null && tokenExpiresAt > now + 300_000) {
            return cached
        }

        val refreshToken = AppConfigManager.spotifyRefreshToken
        if (refreshToken.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            synchronized(this) {
                val cachedDouble = cachedAccessToken
                if (cachedDouble != null && tokenExpiresAt > System.currentTimeMillis() + 300_000) {
                    return@synchronized cachedDouble
                }

                try {
                    Log.i(TAG, "Refreshing Spotify access token...")
                    val clientID = com.clawdroid.app.BuildConfig.SPOTIFY_OAUTH_CLIENT_ID
                    val clientSecret = com.clawdroid.app.BuildConfig.SPOTIFY_OAUTH_CLIENT_SECRET

                    val credentials = "$clientID:$clientSecret"
                    val basicAuth = "Basic " + Base64.encodeToString(
                        credentials.toByteArray(StandardCharsets.UTF_8),
                        Base64.NO_WRAP
                    )

                    val postData = "grant_type=refresh_token" +
                            "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")

                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL("https://accounts.spotify.com/api/token")
                        connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 10_000
                            readTimeout = 15_000
                            doOutput = true
                            setRequestProperty("Authorization", basicAuth)
                            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        }

                        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                            writer.write(postData)
                            writer.flush()
                        }

                        val code = connection.responseCode
                        if (code in 200..299) {
                            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(responseText)
                            val accessToken = json.optString("access_token")
                            val newRefreshToken = json.optString("refresh_token")
                            val expiresIn = json.optLong("expires_in", 3600)

                            if (!accessToken.isNullOrBlank()) {
                                cachedAccessToken = accessToken
                                tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                                if (!newRefreshToken.isNullOrBlank()) {
                                    AppConfigManager.spotifyRefreshToken = newRefreshToken
                                }
                                return@synchronized accessToken
                            }
                        } else {
                            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                            Log.e(TAG, "Spotify refresh error $code: $errorText")
                        }
                    } finally {
                        connection?.disconnect()
                    }
                    return@synchronized null
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing Spotify token", e)
                    return@synchronized null
                }
            }
        }
    }

    suspend fun fetchDisplayName(): String? = withContext(Dispatchers.IO) {
        val token = getOrRefreshAccessToken()
        if (token.isNullOrBlank()) return@withContext null
        try {
            val url = URL("https://api.spotify.com/v1/me")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).optString("display_name")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun disconnect() {
        cachedAccessToken = null
        tokenExpiresAt = 0
        AppConfigManager.spotifyRefreshToken = ""
    }
}
