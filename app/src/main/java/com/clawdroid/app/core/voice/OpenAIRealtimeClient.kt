package com.clawdroid.app.core.voice

import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RealtimeClientSecret(
    val value: String,
    val expiresAt: Long?,
    val sessionId: String?,
)

class OpenAIRealtimeClient {

    private val apiKey: String
        get() = AppConfigManager.openaiRealtimeApiKey
            .takeIf { it.isNotBlank() }
            ?: AppConfigManager.openaiTtsApiKey
            .takeIf { it.isNotBlank() }
            ?: AppConfigManager.apiKey

    companion object {
        private const val TAG = "OpenAIRealtimeClient"
        private const val BASE_URL = "https://api.openai.com/v1"
    }

    suspend fun createClientSecret(
        model: String = AppConfigManager.realtimeVoiceModel,
        voice: String = AppConfigManager.realtimeVoiceVoice,
        instructions: String = defaultInstructions(),
    ): Result<RealtimeClientSecret> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "OpenAI API key is required for Realtime voice" }

            val payload = JSONObject()
                .put(
                    "expires_after",
                    JSONObject()
                        .put("anchor", "created_at")
                        .put("seconds", 600)
                )
                .put(
                    "session",
                    JSONObject()
                        .put("type", "realtime")
                        .put("model", model)
                        .put("instructions", instructions)
                        .put(
                            "audio",
                            JSONObject()
                                .put(
                                    "output",
                                    JSONObject().put("voice", voice)
                                )
                        )
                )

            val connection = (URL("$BASE_URL/realtime/client_secrets").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }

            try {
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                    output.flush()
                }

                val body = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("OpenAI Realtime HTTP ${connection.responseCode}: $errorBody")
                }

                parseClientSecret(JSONObject(body))
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            Log.w(TAG, "Failed to create Realtime client secret", it)
        }
    }

    private fun parseClientSecret(json: JSONObject): RealtimeClientSecret {
        val secret = json.optJSONObject("client_secret") ?: json
        val value = secret.optString("value")
            .takeIf { it.isNotBlank() }
            ?: secret.optString("secret").takeIf { it.isNotBlank() }
            ?: error("OpenAI Realtime client secret response did not include a secret value")

        return RealtimeClientSecret(
            value = value,
            expiresAt = secret.optLong("expires_at").takeIf { it > 0L },
            sessionId = json.optJSONObject("session")?.optString("id")?.takeIf { it.isNotBlank() },
        )
    }
}

private fun defaultInstructions(): String {
    val name = AppConfigManager.agentName.ifBlank { "ClawDroid" }
    val purpose = AppConfigManager.agentPurpose.ifBlank { "help the user on Android" }
    return "You are $name, ClawDroid's realtime voice agent. Keep replies brief, natural, and useful. Your purpose is to $purpose."
}
