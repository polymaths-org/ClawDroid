package com.clawdroid.app.core.tools

import android.util.Log
import com.clawdroid.app.core.service.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GoogleDriveTools {
    private const val TAG = "GoogleDriveTools"

    private suspend fun getHeaders(): Map<String, String>? {
        val token = GoogleAuthManager.getOrRefreshAccessToken() ?: return null
        return mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    suspend fun createDriveFile(name: String, mimeType: String, content: String): String = withContext(Dispatchers.IO) {
        val token = GoogleAuthManager.getOrRefreshAccessToken() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=foo")
            }

            val metadata = JSONObject().apply {
                put("name", name)
                put("mimeType", mimeType)
            }

            val body = buildString {
                append("--foo\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata.toString())
                append("\r\n--foo\r\n")
                append("Content-Type: $mimeType\r\n\r\n")
                append(content)
                append("\r\n--foo--\r\n")
            }

            connection.outputStream.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).toString(2)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "createDriveFile error $code: $err")
                errorJson("Failed to create file: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in createDriveFile", e)
            errorJson(e.message ?: "Failed to create drive file")
        }
    }

    suspend fun searchDriveFiles(query: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val q = "name contains '" + query.replace("'", "\\'") + "' and trashed = false"
            val urlStr = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(q, "UTF-8") + "&fields=files(id,name,mimeType)"
            
            val responseText = getRequest(urlStr, headers)
            responseText ?: errorJson("Failed to search Drive files.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchDriveFiles", e)
            errorJson(e.message ?: "Failed to search Drive files")
        }
    }

    suspend fun writeGoogleDoc(title: String, body: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val createPayload = JSONObject().put("title", title)
            val createResponseStr = postRequest("https://docs.googleapis.com/v1/documents", createPayload, headers)
            if (createResponseStr == null) {
                return@withContext errorJson("Failed to create Google Doc.")
            }
            val createResponse = JSONObject(createResponseStr)
            if (createResponse.has("error")) {
                return@withContext createResponseStr
            }

            val documentId = createResponse.getString("documentId")

            val insertText = JSONObject().apply {
                put("text", body)
                put("location", JSONObject().put("index", 1))
            }
            val requestItem = JSONObject().put("insertText", insertText)
            val batchPayload = JSONObject().put("requests", JSONArray().put(requestItem))

            val updateUrl = "https://docs.googleapis.com/v1/documents/$documentId:batchUpdate"
            val updateResponse = postRequest(updateUrl, batchPayload, headers)
            
            updateResponse ?: errorJson("Failed to update Google Doc body.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in writeGoogleDoc", e)
            errorJson(e.message ?: "Failed to write Google Doc")
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
