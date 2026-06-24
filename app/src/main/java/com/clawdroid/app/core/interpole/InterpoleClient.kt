package com.clawdroid.app.core.interpole

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class InterpoleClient(context: Context) {
    private val appContext = context.applicationContext
    private val configRepository = InterpoleConfigRepository(appContext)
    private val random = SecureRandom()

    suspend fun rpc(action: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig()
        val deviceToken = configRepository.getDeviceToken()
        if (!config.enabled || config.deviceId.isBlank() || deviceToken.isBlank()) {
            error("INTERPOLE is not paired or enabled. Configure Settings > INTERPOLE first.")
        }

        val requestId = "rpc_${System.currentTimeMillis()}_${nonce(4)}"
        val body = JSONObject()
            .put("id", requestId)
            .put("action", action)
            .put("params", params)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = nonce(16)
        val signature = hmacSha256Hex(
            key = deviceToken,
            value = "$timestamp\n$nonce\n${sha256Hex(body)}",
        )

        val connection = (URL("${config.rpcBaseUrl}/v1/rpc").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = (config.commandTimeout.coerceAtLeast(10) + 15) * 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Interpole-Device", config.deviceId)
            setRequestProperty("X-Interpole-Timestamp", timestamp)
            setRequestProperty("X-Interpole-Nonce", nonce)
            setRequestProperty("X-Interpole-Signature", signature)
        }

        connection.outputStream.use { it.write(body) }
        val responseText = runCatching {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        if (!response.optBoolean("ok", false)) {
            val message = buildString {
                append(response.optString("error", "interpole_error"))
                response.optString("message").takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                response.optString("approval_id").takeIf { it.isNotBlank() }?.let { append(" approval_id=").append(it) }
            }
            error(message)
        }
        response.optJSONObject("result") ?: JSONObject()
    }

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig()
        val connection = (URL("${config.rpcBaseUrl}/health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(text)
    }

    private fun nonce(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun hmacSha256Hex(key: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
