package com.clawdroid.app.core.service

import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GithubAuthManager {
    private const val TAG = "GithubAuthManager"

    val isConnected: Boolean
        get() = AppConfigManager.githubToken.isNotBlank()

    suspend fun exchangeAuthCode(authCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Exchanging GitHub auth code...")
            val clientID = com.clawdroid.app.BuildConfig.GITHUB_OAUTH_CLIENT_ID
            val clientSecret = com.clawdroid.app.BuildConfig.GITHUB_OAUTH_CLIENT_SECRET
            var postData = "client_id=" + URLEncoder.encode(clientID, "UTF-8") +
                    "&code=" + URLEncoder.encode(authCode, "UTF-8")
            if (clientSecret.isNotBlank()) {
                postData += "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8")
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://github.com/login/oauth/access_token")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
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
                    if (!accessToken.isNullOrBlank()) {
                        AppConfigManager.githubToken = accessToken
                        return@withContext true
                    } else {
                        Log.e(TAG, "GitHub exchange response lacks access_token: $responseText")
                    }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e(TAG, "GitHub exchange error $code: $errorText")
                }
            } finally {
                connection?.disconnect()
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging GitHub code", e)
            return@withContext false
        }
    }

    suspend fun fetchUsername(): String? = withContext(Dispatchers.IO) {
        val token = AppConfigManager.githubToken
        if (token.isBlank()) return@withContext null
        try {
            val url = URL("https://api.github.com/user")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ClawDroid-App")
            }
            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).optString("login")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun disconnect() {
        AppConfigManager.githubToken = ""
    }
}
