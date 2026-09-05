package com.clawdroid.app.core.tools

import android.util.Base64
import android.util.Log
import com.clawdroid.app.core.service.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object GoogleTools {
    private const val TAG = "GoogleTools"

    private suspend fun getHeaders(): Map<String, String>? {
        val token = GoogleAuthManager.getOrRefreshAccessToken() ?: return null
        return mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    suspend fun listEmails(query: String?, maxResults: Int): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected. Prompt the user to log in.")
        try {
            var urlStr = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=$maxResults"
            if (!query.isNullOrBlank()) {
                urlStr += "&q=" + URLEncoder.encode(query, "UTF-8")
            }

            val json = getRequest(urlStr, headers) ?: return@withContext errorJson("Failed to fetch messages list from Google APIs.")
            val messages = json.optJSONArray("messages") ?: return@withContext JSONObject().put("messages", JSONArray()).toString(2)

            // Fetch detail for each message in parallel to provide a useful summary to the agent
            val deferreds = (0 until messages.length()).map { idx ->
                val msgObj = messages.getJSONObject(idx)
                val id = msgObj.getString("id")
                async {
                    runCatching { fetchEmailSummary(id, headers) }.getOrNull()
                }
            }

            val summaries = deferreds.awaitAll().filterNotNull()
            JSONObject().put("messages", JSONArray(summaries)).toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing emails", e)
            errorJson(e.message ?: "Failed to list emails")
        }
    }

    suspend fun getEmail(id: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val urlStr = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=full"
            val json = getRequest(urlStr, headers) ?: return@withContext errorJson("Failed to fetch email details.")
            
            val payload = json.optJSONObject("payload")
            val headersArray = payload?.optJSONArray("headers") ?: JSONArray()
            
            var subject = ""
            var from = ""
            var date = ""
            for (i in 0 until headersArray.length()) {
                val header = headersArray.getJSONObject(i)
                val name = header.optString("name").lowercase()
                if (name == "subject") subject = header.optString("value")
                if (name == "from") from = header.optString("value")
                if (name == "date") date = header.optString("value")
            }

            val snippet = json.optString("snippet")
            val body = extractEmailBody(payload) ?: snippet

            JSONObject()
                .put("id", id)
                .put("from", from)
                .put("subject", subject)
                .put("date", date)
                .put("snippet", snippet)
                .put("body", body)
                .toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting email details", e)
            errorJson(e.message ?: "Failed to retrieve email details")
        }
    }

    suspend fun sendEmail(to: String, subject: String, body: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val normalizedBody = body.replace("\r\n", "\n").replace("\n", "\r\n")
            val rawEmail = "To: $to\r\n" +
                    "Subject: $subject\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n\r\n" +
                    normalizedBody

            val base64UrlSafe = Base64.encodeToString(
                rawEmail.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val payload = JSONObject().put("raw", base64UrlSafe)
            val response = postRequest("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", payload, headers)
            response?.toString(2) ?: errorJson("Failed to send email via Google APIs.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email", e)
            errorJson(e.message ?: "Failed to send email")
        }
    }

    suspend fun createDraft(to: String, subject: String, body: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val normalizedBody = body.replace("\r\n", "\n").replace("\n", "\r\n")
            val rawEmail = "To: $to\r\n" +
                    "Subject: $subject\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n\r\n" +
                    normalizedBody

            val base64UrlSafe = Base64.encodeToString(
                rawEmail.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val messagePayload = JSONObject().put("raw", base64UrlSafe)
            val draftPayload = JSONObject().put("message", messagePayload)
            
            val response = postRequest("https://gmail.googleapis.com/gmail/v1/users/me/drafts", draftPayload, headers)
            response?.toString(2) ?: errorJson("Failed to create draft.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating draft", e)
            errorJson(e.message ?: "Failed to create draft")
        }
    }

    suspend fun listCalendarEvents(timeMin: String?, timeMax: String?, maxResults: Int): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            var urlStr = "https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=$maxResults&singleEvents=true&orderBy=startTime"
            if (!timeMin.isNullOrBlank()) urlStr += "&timeMin=" + URLEncoder.encode(timeMin, "UTF-8")
            if (!timeMax.isNullOrBlank()) urlStr += "&timeMax=" + URLEncoder.encode(timeMax, "UTF-8")

            val json = getRequest(urlStr, headers) ?: return@withContext errorJson("Failed to retrieve calendar events.")
            val items = json.optJSONArray("items") ?: JSONArray()
            val simplified = mutableListOf<JSONObject>()
            
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val start = item.optJSONObject("start")?.optString("dateTime") ?: item.optJSONObject("start")?.optString("date") ?: ""
                val end = item.optJSONObject("end")?.optString("dateTime") ?: item.optJSONObject("end")?.optString("date") ?: ""
                simplified.add(
                    JSONObject()
                        .put("id", item.optString("id"))
                        .put("summary", item.optString("summary"))
                        .put("start", start)
                        .put("end", end)
                        .put("status", item.optString("status"))
                )
            }

            JSONObject().put("events", JSONArray(simplified)).toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing calendar events", e)
            errorJson(e.message ?: "Failed to list calendar events")
        }
    }

    suspend fun createCalendarEvent(summary: String, description: String?, startTime: String, endTime: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Google account is not connected.")
        try {
            val payload = JSONObject()
                .put("summary", summary)
                .put("description", description ?: "")
                .put("start", JSONObject().put("dateTime", startTime))
                .put("end", JSONObject().put("dateTime", endTime))

            val response = postRequest("https://www.googleapis.com/calendar/v3/calendars/primary/events", payload, headers)
            response?.toString(2) ?: errorJson("Failed to create calendar event.")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar event", e)
            errorJson(e.message ?: "Failed to create calendar event")
        }
    }

    private fun fetchEmailSummary(id: String, headers: Map<String, String>): JSONObject? {
        val urlStr = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=subject&metadataHeaders=from&metadataHeaders=date"
        val json = getRequest(urlStr, headers) ?: return null
        
        val headersArray = json.optJSONObject("payload")?.optJSONArray("headers") ?: JSONArray()
        var subject = "(No Subject)"
        var from = "(Unknown)"
        var date = ""
        for (i in 0 until headersArray.length()) {
            val h = headersArray.getJSONObject(i)
            val name = h.optString("name").lowercase()
            if (name == "subject") subject = h.optString("value")
            if (name == "from") from = h.optString("value")
            if (name == "date") date = h.optString("value")
        }

        return JSONObject()
            .put("id", id)
            .put("from", from)
            .put("subject", subject)
            .put("date", date)
            .put("snippet", json.optString("snippet"))
    }

    private fun extractEmailBody(payload: JSONObject?): String? {
        if (payload == null) return null
        val bodyData = payload.optJSONObject("body")?.optString("data")
        if (!bodyData.isNullOrBlank()) {
            return String(Base64.decode(bodyData, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        }
        val parts = payload.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val mimeType = part.optString("mimeType")
                if (mimeType == "text/plain") {
                    val subBody = part.optJSONObject("body")?.optString("data")
                    if (!subBody.isNullOrBlank()) {
                        return String(Base64.decode(subBody, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
                    }
                }
            }
            // fallback: check nested parts
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val nestedBody = extractEmailBody(part)
                if (nestedBody != null) return nestedBody
            }
        }
        return null
    }

    private fun getRequest(urlStr: String, headers: Map<String, String>): JSONObject? {
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
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "GET Error $code: $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on GET $urlStr", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun postRequest(urlStr: String, payload: JSONObject, headers: Map<String, String>): JSONObject? {
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
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.e(TAG, "POST Error $code: $err")
                null
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
