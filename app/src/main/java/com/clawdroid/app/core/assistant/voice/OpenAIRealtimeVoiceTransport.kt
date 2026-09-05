package com.clawdroid.app.core.assistant.voice

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject

class OpenAIRealtimeVoiceTransport(private val client: OkHttpClient = OkHttpClient()) : VoiceTransport, WebSocketListener() {

    companion object {
        private const val TAG = "OpenAIRealtimeVoice"
        private const val WSS_URL = "wss://api.openai.com/v1/realtime"
    }

    private var webSocket: WebSocket? = null

    private val _events = MutableSharedFlow<RealtimeVoiceEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<RealtimeVoiceEvent> = _events.asSharedFlow()

    override suspend fun connect(clientSecret: String) {
        disconnect()

        val request = Request.Builder()
            .url("$WSS_URL?model=gpt-4o-realtime-preview-2024-10-01")
            .addHeader("Authorization", "Bearer $clientSecret")
            .addHeader("OpenAI-Beta", "realtime=2024-10-01")
            .build()

        webSocket = client.newWebSocket(request, this)
    }

    override suspend fun sendAudio(frame: ByteArray) {
        val base64Audio = Base64.encodeToString(frame, Base64.NO_WRAP)
        val event = JSONObject()
            .put("type", "input_audio_buffer.append")
            .put("audio", base64Audio)
        
        webSocket?.send(event.toString())
    }

    override suspend fun sendText(text: String) {
        val event = JSONObject()
            .put("type", "conversation.item.create")
            .put("item", JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", JSONObject()
                    .put("type", "input_text")
                    .put("text", text)
                )
            )
        webSocket?.send(event.toString())
        
        val responseCreate = JSONObject().put("type", "response.create")
        webSocket?.send(responseCreate.toString())
    }

    override suspend fun interrupt() {
        val event = JSONObject().put("type", "response.cancel")
        webSocket?.send(event.toString())
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _events.tryEmit(RealtimeVoiceEvent.Disconnected)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "WebSocket connected successfully")
        _events.tryEmit(RealtimeVoiceEvent.Connected)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val event = JSONObject(text)
            val type = event.optString("type")
            
            when (type) {
                "response.audio_transcript.delta" -> {
                    val delta = event.optString("delta")
                    _events.tryEmit(RealtimeVoiceEvent.TranscriptReceived(delta, isFinal = false))
                }
                "response.audio_transcript.done" -> {
                    _events.tryEmit(RealtimeVoiceEvent.TranscriptReceived("", isFinal = true))
                }
                "response.audio.delta" -> {
                    val base64Delta = event.optString("delta")
                    val audioBytes = Base64.decode(base64Delta, Base64.NO_WRAP)
                    _events.tryEmit(RealtimeVoiceEvent.AudioReceived(audioBytes))
                }
                "error" -> {
                    val err = event.optJSONObject("error")
                    val message = err?.optString("message") ?: "Unknown OpenAI Realtime error"
                    _events.tryEmit(RealtimeVoiceEvent.Error(message))
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to parse incoming WS text event", e)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "WebSocket failure", t)
        _events.tryEmit(RealtimeVoiceEvent.Error(t.message ?: "WebSocket Connection Failure"))
        _events.tryEmit(RealtimeVoiceEvent.Disconnected)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "WebSocket closing: $reason")
        _events.tryEmit(RealtimeVoiceEvent.Disconnected)
    }
}
