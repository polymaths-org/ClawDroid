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
import java.nio.charset.StandardCharsets

object NotionAuthManager {
    private const val TAG = "NotionAuthManager"

    val isConnected: Boolean
        get() = AppConfigManager.notionToken.isNotBlank()

    suspend fun exchangeAuthCode(authCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Exchanging Notion auth code...")
            val clientID = com.clawdroid.app.BuildConfig.NOTION_OAUTH_CLIENT_ID
            val clientSecret = com.clawdroid.app.BuildConfig.NOTION_OAUTH_CLIENT_SECRET

            val credentials = "$clientID:$clientSecret"
            val basicAuth = "Basic " + Base64.encodeToString(
                credentials.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )

            val payload = JSONObject().apply {
                put("grant_type", "authorization_code")
                put("code", authCode)
                put("redirect_uri", "clawdroid://notion-auth")
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.notion.com/v1/oauth/token")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Authorization", basicAuth)
                    setRequestProperty("Content-Type", "application/json")
                }

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val code = connection.responseCode
                if (code in 200..299) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val accessToken = json.optString("access_token")
                    if (!accessToken.isNullOrBlank()) {
                        AppConfigManager.notionToken = accessToken
                        return@withContext true
                    }
                } else {
                    val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.e(TAG, "Notion exchange error $code: $errorText")
                }
            } finally {
                connection?.disconnect()
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging Notion code", e)
            return@withContext false
        }
    }

    suspend fun fetchWorkspaceName(): String? = withContext(Dispatchers.IO) {
        val token = AppConfigManager.notionToken
        if (token.isBlank()) return@withContext null
        try {
            val url = URL("https://api.notion.com/v1/users/me")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Notion-Version", "2022-06-28")
            }
            if (connection.responseCode == 200) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                // In Notion public integrations, user object contains workspace name or the bot owner details.
                obj.optString("name") ?: obj.optJSONObject("bot")?.optString("workspace_name")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun disconnect() {
        AppConfigManager.notionToken = ""
    }
}
