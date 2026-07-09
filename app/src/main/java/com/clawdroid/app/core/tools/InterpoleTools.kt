package com.clawdroid.app.core.tools

import android.util.Base64
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Agent-facing client for the paired INTERPOLE desktop bridge.
 *
 * Security:
 *  - Host, port and the HMAC device token come only from Settings > INTERPOLE.
 *  - The device token is never logged and never placed in tool results.
 *  - Every RPC is signed: HMAC-SHA256 over "timestamp\nnonce\nsha256(body)".
 *
 * Context + speed:
 *  - Results are returned exactly as the daemon summarized them (head+tail
 *    command output, file line-ranges, batched results), so very little text
 *    reaches the model.
 *  - [batch] collapses several desktop operations into a single signed
 *    round-trip and a single activity step.
 */
object InterpoleTools {
    private const val TAG = "InterpoleTools"
    private const val HEX = "0123456789abcdef"
    private val random = SecureRandom()

    private data class Conn(val baseUrl: String, val deviceId: String, val token: String)

    /** True only when INTERPOLE is enabled and fully paired. */
    fun isReady(): Boolean =
        AppConfigManager.interpoleEnabled &&
            AppConfigManager.interpoleHost.isNotBlank() &&
            AppConfigManager.interpoleDeviceId.isNotBlank() &&
            AppConfigManager.interpoleDeviceToken.isNotBlank()

    private fun connection(): Conn? {
        if (!isReady()) return null
        val host = AppConfigManager.interpoleHost.trim()
        val port = AppConfigManager.interpolePort
        return Conn(
            baseUrl = "http://$host:$port",
            deviceId = AppConfigManager.interpoleDeviceId,
            token = AppConfigManager.interpoleDeviceToken,
        )
    }

    suspend fun status(): String = rpc("status", JSONObject(), readTimeoutMs = 8_000)

    suspend fun listDir(path: String): String =
        rpc("list_dir", JSONObject().put("path", path), readTimeoutMs = 15_000)

    suspend fun readFile(path: String, startLine: Int?, endLine: Int?, maxBytes: Int?): String {
        val params = JSONObject().put("path", path)
        if (startLine != null) params.put("start_line", startLine)
        if (endLine != null) params.put("end_line", endLine)
        if (maxBytes != null) params.put("max_bytes", maxBytes)
        return rpc("read_file", params, readTimeoutMs = 20_000)
    }

    suspend fun writeFile(path: String, content: String, approvalId: String?): String {
        val params = JSONObject().put("path", path).put("content", content)
        if (!approvalId.isNullOrBlank()) params.put("approval_id", approvalId)
        return rpc("write_file", params, readTimeoutMs = 20_000)
    }

    suspend fun execute(
        command: String,
        cwd: String?,
        timeoutSeconds: Int,
        maxOutputLines: Int?,
        approvalId: String?,
    ): String {
        val safeTimeout = timeoutSeconds.coerceIn(1, 3600)
        val params = JSONObject().put("command", command).put("timeout_seconds", safeTimeout)
        if (!cwd.isNullOrBlank()) params.put("cwd", cwd)
        if (maxOutputLines != null) params.put("max_output_lines", maxOutputLines)
        if (!approvalId.isNullOrBlank()) params.put("approval_id", approvalId)
        return rpc("execute", params, readTimeoutMs = (safeTimeout + 20) * 1_000)
    }

    suspend fun notify(title: String, body: String): String =
        rpc("notify", JSONObject().put("title", title).put("body", body), readTimeoutMs = 10_000)

    suspend fun batch(actions: JSONArray): String {
        // Read timeout must cover the longest execute inside the batch.
        var readTimeout = 30_000
        for (i in 0 until actions.length()) {
            val item = actions.optJSONObject(i) ?: continue
            if (item.optString("action") == "execute") {
                val t = item.optJSONObject("params")?.optInt("timeout_seconds", 60) ?: 60
                readTimeout = maxOf(readTimeout, (t.coerceIn(1, 3600) + 20) * 1_000)
            }
        }
        return rpc(
            "batch",
            JSONObject().put("actions", actions),
            readTimeoutMs = readTimeout.coerceAtMost(600_000),
        )
    }

    private suspend fun rpc(action: String, params: JSONObject, readTimeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val conn = connection()
                ?: return@withContext fail(
                    "unpaired",
                    "INTERPOLE is not paired. Ask the user to connect in Settings > INTERPOLE.",
                ).toString()

            val payload = JSONObject()
                .put("id", "rpc_" + randomToken(6))
                .put("action", action)
                .put("params", params)
            val bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)
            val timestamp = (System.currentTimeMillis() / 1000L).toString()
            val nonce = randomToken(18)
            val signature = sign(conn.token, timestamp, nonce, bodyBytes)
                ?: return@withContext fail("client_error", "Could not sign the INTERPOLE request.").toString()

            var http: HttpURLConnection? = null
            try {
                val url = URL(conn.baseUrl.trimEnd('/') + "/v1/rpc")
                http = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4_000
                    readTimeout = readTimeoutMs
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Connection", "close")
                    setRequestProperty("X-Interpole-Device", conn.deviceId)
                    setRequestProperty("X-Interpole-Timestamp", timestamp)
                    setRequestProperty("X-Interpole-Nonce", nonce)
                    setRequestProperty("X-Interpole-Signature", signature)
                    setFixedLengthStreamingMode(bodyBytes.size)
                }
                http.outputStream.use { it.write(bodyBytes); it.flush() }
                val code = http.responseCode
                val stream = if (code in 200..299) http.inputStream else http.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                interpret(text).toString()
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "INTERPOLE $action timed out")
                fail(
                    "offline",
                    "INTERPOLE desktop did not respond in time. Use Android sandbox tools if the task allows, and tell the user the desktop is slow or offline.",
                ).toString()
            } catch (e: java.io.IOException) {
                Log.w(TAG, "INTERPOLE $action unreachable: ${e.message}")
                fail(
                    "offline",
                    "INTERPOLE desktop is unreachable. Use Android sandbox tools if the task allows, and tell the user the desktop is offline.",
                ).toString()
            } finally {
                http?.disconnect()
            }
        }

    /** Branch on the daemon's JSON `ok` field, not the HTTP code (202 = approval). */
    private fun interpret(text: String): JSONObject {
        val json = try {
            JSONObject(text.ifBlank { "{}" })
        } catch (e: Exception) {
            return fail("bad_response", "INTERPOLE returned an unreadable response.")
        }
        if (json.optBoolean("ok", false)) {
            val result = json.optJSONObject("result") ?: JSONObject()
            result.put("ok", true)
            result.put("environment", "desktop")
            return result
        }
        val error = json.optString("error", "error")
        val out = fail(error, adviceFor(error))
        if (json.has("approval_id")) out.put("approval_id", json.optString("approval_id"))
        if (json.has("retry_after")) out.put("retry_after", json.optInt("retry_after"))
        val message = json.optString("message", "")
        if (message.isNotBlank() && message != error) out.put("message", message)
        return out
    }

    private fun adviceFor(error: String): String = when (error) {
        "approval_required" ->
            "Desktop approval needed. Tell the user the approval_id and ask them to approve it on the desktop (interpole approve <id>) or in the app, then retry the SAME call with approval_id set to that value."
        "untrusted_path" ->
            "Path is outside the desktop trusted folders. Ask the user to add it on desktop (interpole trust-folder add <path>). Do not retry other paths blindly."
        "execute_disabled" ->
            "Desktop command execution is disabled. Ask the user to enable it (interpole allow-execute on). Do not work around it with Android networking."
        "unauthorized" ->
            "The desktop rejected the signed request. Ask the user to re-pair in Settings > INTERPOLE."
        "too_many_attempts" ->
            "Pairing is temporarily locked after failed PIN attempts. Ask the user to wait, then re-pair."
        "forbidden_remote" ->
            "The desktop refused this network source. Make sure phone and PC share the same LAN or Tailscale tailnet."
        "timeout" ->
            "The desktop command exceeded its timeout. Raise timeout_seconds or simplify the command."
        "not_found" -> "The desktop path or resource was not found."
        "batch_too_large" -> "Too many actions in one batch. Split the work into smaller batches."
        "unpaired" -> "INTERPOLE is not paired. Ask the user to connect in Settings > INTERPOLE."
        "offline" -> "INTERPOLE desktop is offline. Use Android sandbox tools when possible and tell the user."
        else -> "INTERPOLE request failed."
    }

    private fun fail(error: String, advice: String): JSONObject =
        JSONObject().put("ok", false).put("environment", "desktop").put("error", error).put("advice", advice)

    private fun sign(token: String, timestamp: String, nonce: String, body: ByteArray): String? = try {
        val bodyHash = sha256Hex(body)
        val signingPayload = "$timestamp\n$nonce\n$bodyHash".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        hex(mac.doFinal(signingPayload))
    } catch (e: Exception) {
        Log.e(TAG, "Signing failed")
        null
    }

    private fun sha256Hex(data: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(data))

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    private fun randomToken(numBytes: Int): String {
        val buf = ByteArray(numBytes)
        random.nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
