package com.clawdroid.app.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.security.MessageDigest

data class ProviderModel(
    val id: String,
    val label: String = id,
    val owner: String? = null,
    val description: String? = null,
)

class ModelDiscoveryClient(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("clawdroid_model_cache", Context.MODE_PRIVATE)

    suspend fun discover(
        provider: AiProviderPreset,
        baseUrl: String,
        apiKey: String,
    ): Result<List<ProviderModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
            val models = when (provider.dialect) {
                ProviderDialect.GEMINI_NATIVE -> fetchGeminiModels(normalizedBaseUrl, apiKey)
                ProviderDialect.ANTHROPIC_NATIVE -> fetchAnthropicModels(normalizedBaseUrl, apiKey)
                ProviderDialect.OPENAI_COMPATIBLE -> fetchOpenAiCompatibleModels(normalizedBaseUrl, apiKey)
            }
            models.sortedBy { it.id.lowercase() }.also {
                cacheModels(provider.id, normalizedBaseUrl, apiKey, it)
            }
        }
    }

    fun cachedModels(providerId: String, baseUrl: String, apiKey: String): List<ProviderModel> {
        val raw = prefs.getString(cacheKey(providerId, baseUrl.trim().trimEnd('/'), apiKey), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        ProviderModel(
                            id = id,
                            label = obj.optString("label", id),
                            owner = obj.optString("owner").takeIf { it.isNotBlank() },
                            description = obj.optString("description").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchOpenAiCompatibleModels(baseUrl: String, apiKey: String): List<ProviderModel> {
        val json = requestJson(
            url = "$baseUrl/models",
            headers = bearerHeaders(apiKey),
        )
        val data = json.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    ProviderModel(
                        id = id,
                        owner = obj.optString("owned_by").takeIf { it.isNotBlank() },
                        description = obj.optString("description").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun fetchGeminiModels(baseUrl: String, apiKey: String): List<ProviderModel> {
        val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
        val json = requestJson(
            url = "$baseUrl/models?key=$encodedKey",
            headers = emptyMap(),
        )
        val models = json.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (i in 0 until models.length()) {
                val obj = models.optJSONObject(i) ?: continue
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                val methods = obj.optJSONArray("supportedGenerationMethods")
                if (methods != null && !methods.containsString("generateContent")) continue
                val id = name.removePrefix("models/")
                add(
                    ProviderModel(
                        id = id,
                        label = obj.optString("displayName").takeIf { it.isNotBlank() } ?: id,
                        owner = "google",
                        description = obj.optString("description").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private fun fetchAnthropicModels(baseUrl: String, apiKey: String): List<ProviderModel> {
        val json = requestJson(
            url = "$baseUrl/models",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            ),
        )
        val data = json.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val obj = data.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    ProviderModel(
                        id = id,
                        label = obj.optString("display_name").takeIf { it.isNotBlank() } ?: id,
                        owner = "anthropic",
                    ),
                )
            }
        }
    }

    private fun requestJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (code !in 200..299) {
            error("HTTP $code: ${body.take(500)}")
        }
        return JSONObject(body)
    }

    private fun bearerHeaders(apiKey: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "HTTP-Referer" to "https://clawdroid.local",
        "X-Title" to "ClawDroid",
    )

    private fun cacheModels(providerId: String, baseUrl: String, apiKey: String, models: List<ProviderModel>) {
        val array = JSONArray()
        models.forEach { model ->
            array.put(
                JSONObject()
                    .put("id", model.id)
                    .put("label", model.label)
                    .put("owner", model.owner ?: "")
                    .put("description", model.description ?: ""),
            )
        }
        prefs.edit().putString(cacheKey(providerId, baseUrl, apiKey), array.toString()).apply()
    }

    private fun cacheKey(providerId: String, baseUrl: String, apiKey: String): String {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "models_${providerId}_${baseUrl.hashCode()}_$fingerprint"
    }

    private fun JSONArray.containsString(value: String): Boolean {
        for (i in 0 until length()) {
            if (optString(i) == value) return true
        }
        return false
    }
}
