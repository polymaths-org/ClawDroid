package com.clawdroid.app.core.tools

import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GithubTools {
    private const val TAG = "GithubTools"

    private fun getHeaders(): Map<String, String>? {
        val token = AppConfigManager.githubToken
        if (token.isBlank()) return null
        return mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to "application/vnd.github.v3+json",
            "User-Agent" to "ClawDroid-App",
            "Content-Type" to "application/json"
        )
    }

    suspend fun listRepos(): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("GitHub is not connected. Connect via MCP Settings.")
        try {
            val response = getRequest("https://api.github.com/user/repos?sort=updated&per_page=20", headers)
            response ?: errorJson("Failed to list GitHub repositories.")
        } catch (e: Exception) {
            Log.e(TAG, "Error listing repos", e)
            errorJson(e.message ?: "Failed to list repos")
        }
    }

    suspend fun createIssue(repo: String, title: String, body: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("GitHub is not connected.")
        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("body", body)
            }
            val response = postRequest("https://api.github.com/repos/$repo/issues", payload, headers)
            response ?: errorJson("Failed to create issue.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating issue", e)
            errorJson(e.message ?: "Failed to create issue")
        }
    }

    suspend fun createPullRequest(repo: String, title: String, head: String, base: String, body: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("GitHub is not connected.")
        try {
            val payload = JSONObject().apply {
                put("title", title)
                put("head", head)
                put("base", base)
                put("body", body)
            }
            val response = postRequest("https://api.github.com/repos/$repo/pulls", payload, headers)
            response ?: errorJson("Failed to create pull request.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pull request", e)
            errorJson(e.message ?: "Failed to create pull request")
        }
    }

    private fun getRequest(urlStr: String, headers: Map<String, String>): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "GET Error $code: $err")
                JSONObject().put("error", err).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on GET $urlStr", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun postRequest(urlStr: String, payload: JSONObject, headers: Map<String, String>): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(connection.outputStream, "UTF-8").use { w ->
                w.write(payload.toString())
                w.flush()
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "POST Error $code: $err")
                JSONObject().put("error", err).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on POST $urlStr", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorJson(msg: String): String {
        return JSONObject().put("error", msg).toString(2)
    }
}
