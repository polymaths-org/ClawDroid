package com.clawdroid.app.core.tools

import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NotionTools {
    private const val TAG = "NotionTools"

    private fun getHeaders(): Map<String, String>? {
        val token = AppConfigManager.notionToken
        if (token.isBlank()) return null
        return mapOf(
            "Authorization" to "Bearer $token",
            "Notion-Version" to "2022-06-28",
            "Content-Type" to "application/json"
        )
    }

    suspend fun createPage(parentPageId: String, title: String, content: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Notion is not connected.")
        try {
            val parent = JSONObject().put("page_id", parentPageId)
            
            val titleText = JSONObject().put("text", JSONObject().put("content", title))
            val titleProperty = JSONObject().put("title", JSONArray().put(titleText))
            val properties = JSONObject().put("title", titleProperty)

            val paragraphText = JSONObject().put("text", JSONObject().put("content", content))
            val paragraphObj = JSONObject().put("rich_text", JSONArray().put(paragraphText))
            val blockObj = JSONObject().apply {
                put("object", "block")
                put("type", "paragraph")
                put("paragraph", paragraphObj)
            }
            val children = JSONArray().put(blockObj)

            val payload = JSONObject().apply {
                put("parent", parent)
                put("properties", properties)
                put("children", children)
            }

            val response = postRequest("https://api.notion.com/v1/pages", payload, headers)
            response ?: errorJson("Failed to create Notion page.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Notion page", e)
            errorJson(e.message ?: "Failed to create page")
        }
    }

    suspend fun appendBlock(pageId: String, content: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Notion is not connected.")
        try {
            val paragraphText = JSONObject().put("text", JSONObject().put("content", content))
            val paragraphObj = JSONObject().put("rich_text", JSONArray().put(paragraphText))
            val blockObj = JSONObject().apply {
                put("object", "block")
                put("type", "paragraph")
                put("paragraph", paragraphObj)
            }
            val children = JSONArray().put(blockObj)
            val payload = JSONObject().put("children", children)

            val response = patchRequest("https://api.notion.com/v1/blocks/$pageId/children", payload, headers)
            response ?: errorJson("Failed to append Notion block.")
        } catch (e: Exception) {
            Log.e(TAG, "Error appending Notion block", e)
            errorJson(e.message ?: "Failed to append block")
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

    private fun patchRequest(urlStr: String, payload: JSONObject, headers: Map<String, String>): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
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
                Log.e(TAG, "PATCH Error $code: $err")
                JSONObject().put("error", err).toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on PATCH $urlStr", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorJson(msg: String): String {
        return JSONObject().put("error", msg).toString(2)
    }
}
