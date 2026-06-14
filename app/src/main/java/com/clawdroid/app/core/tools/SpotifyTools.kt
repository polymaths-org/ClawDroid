package com.clawdroid.app.core.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import com.clawdroid.app.core.service.SpotifyAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SpotifyTools {
    private const val TAG = "SpotifyTools"

    private suspend fun getHeaders(): Map<String, String>? {
        val token = SpotifyAuthManager.getOrRefreshAccessToken() ?: return null
        return mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    suspend fun controlPlayback(context: Context, action: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders()
        var webApiSuccess = false

        if (headers != null) {
            val endpoint = when (action.uppercase()) {
                "PLAY" -> "https://api.spotify.com/v1/me/player/play"
                "PAUSE" -> "https://api.spotify.com/v1/me/player/pause"
                "NEXT" -> "https://api.spotify.com/v1/me/player/next"
                "PREV" -> "https://api.spotify.com/v1/me/player/previous"
                else -> null
            }

            if (endpoint != null) {
                try {
                    val method = if (action.uppercase() in listOf("PLAY", "PAUSE")) "PUT" else "POST"
                    val code = sendEmptyBodyRequest(endpoint, method, headers)
                    if (code in 200..299) {
                        webApiSuccess = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Web API playback control failed, falling back to local intents", e)
                }
            }
        }

        if (!webApiSuccess) {
            val keycode = when (action.uppercase()) {
                "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "PREV" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> -1
            }

            if (keycode != -1) {
                sendLocalMediaButton(context, keycode)
                return@withContext JSONObject().put("status", "success").put("method", "local_intent").toString(2)
            }
        }

        if (webApiSuccess) {
            JSONObject().put("status", "success").put("method", "web_api").toString(2)
        } else {
            errorJson("Unsupported action or failed to execute playback control.")
        }
    }

    suspend fun getCurrentTrack(): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Spotify is not connected.")
        try {
            val responseText = getRequest("https://api.spotify.com/v1/me/player/currently-playing", headers)
            if (responseText.isNullOrBlank()) {
                return@withContext JSONObject().put("playing", false).put("message", "No track is currently playing or Spotify is inactive.").toString(2)
            }
            responseText
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current track", e)
            errorJson(e.message ?: "Failed to get current track")
        }
    }

    suspend fun searchAndPlay(context: Context, query: String): String = withContext(Dispatchers.IO) {
        val headers = getHeaders() ?: return@withContext errorJson("Spotify is not connected.")
        try {
            val searchUrl = "https://api.spotify.com/v1/search?q=" + URLEncoder.encode(query, "UTF-8") + "&type=track&limit=1"
            val searchResponse = getRequest(searchUrl, headers) ?: return@withContext errorJson("Failed to search tracks.")
            
            val json = JSONObject(searchResponse)
            val tracks = json.optJSONObject("tracks")?.optJSONArray("items")
            if (tracks == null || tracks.length() == 0) {
                return@withContext errorJson("No tracks found matching query '$query'.")
            }

            val track = tracks.getJSONObject(0)
            val trackUri = track.getString("uri")
            val trackName = track.getString("name")
            val artistName = track.getJSONArray("artists").getJSONObject(0).getString("name")

            var startedViaWeb = false
            try {
                val playUrl = "https://api.spotify.com/v1/me/player/play"
                val playPayload = JSONObject().put("uris", org.json.JSONArray().put(trackUri))
                val responseCode = sendJsonRequest(playUrl, "PUT", playPayload, headers)
                if (responseCode in 200..299) {
                    startedViaWeb = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start play via Web API, trying local intent fallback", e)
            }

            if (!startedViaWeb) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trackUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    `package` = "com.spotify.music"
                }
                context.startActivity(intent)
            }

            JSONObject().apply {
                put("status", "success")
                put("track", trackName)
                put("artist", artistName)
                put("uri", trackUri)
                put("started_via", if (startedViaWeb) "web_api" else "local_app_intent")
            }.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchAndPlay", e)
            errorJson(e.message ?: "Failed to search and play track")
        }
    }

    private fun sendLocalMediaButton(context: Context, keycode: Int) {
        val eventTime = System.currentTimeMillis()
        val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0))
            `package` = "com.spotify.music"
        }
        context.sendOrderedBroadcast(downIntent, null)

        val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keycode, 0))
            `package` = "com.spotify.music"
        }
        context.sendOrderedBroadcast(upIntent, null)
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
            if (code == 204) return "" 
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

    private fun sendEmptyBodyRequest(urlStr: String, method: String, headers: Map<String, String>): Int {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            connection.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "Network error on empty body $method $urlStr", e)
            -1
        } finally {
            connection?.disconnect()
        }
    }

    private fun sendJsonRequest(urlStr: String, method: String, payload: JSONObject, headers: Map<String, String>): Int {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            OutputStreamWriter(connection.outputStream, "UTF-8").use { w ->
                w.write(payload.toString())
                w.flush()
            }
            connection.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "Network error on JSON $method $urlStr", e)
            -1
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorJson(msg: String): String {
        return JSONObject().put("error", msg).toString(2)
    }
}
