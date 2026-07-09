package com.clawdroid.app.core.assistant.voice

import android.util.Base64
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder

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

        val model = AppConfigManager.realtimeVoiceModel.ifBlank { "gpt-realtime-2" }
        val encodedModel = URLEncoder.encode(model, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$WSS_URL?model=$encodedModel")
            .addHeader("Authorization", "Bearer $clientSecret")
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
                .put("content", org.json.JSONArray()
                    .put(JSONObject()
                        .put("type", "input_text")
                        .put("text", text)
                    )
                )
            )
        webSocket?.send(event.toString())
        
        val responseCreate = JSONObject().put("type", "response.create")
        webSocket?.send(responseCreate.toString())
    }

    override suspend fun interrupt() {
        webSocket?.send(JSONObject().put("type", "response.cancel").toString())
        webSocket?.send(JSONObject().put("type", "input_audio_buffer.clear").toString())
    }

    override suspend fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _events.tryEmit(RealtimeVoiceEvent.Disconnected)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "WebSocket connected successfully")
        sendSessionUpdate(webSocket)
        _events.tryEmit(RealtimeVoiceEvent.Connected)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        runCatching {
            val event = JSONObject(text)
            val type = event.optString("type")
            
            when (type) {
                "input_audio_buffer.speech_started" -> {
                    _events.tryEmit(RealtimeVoiceEvent.UserSpeechStarted)
                }
                "input_audio_buffer.speech_stopped" -> {
                    _events.tryEmit(RealtimeVoiceEvent.UserSpeechStopped)
                }
                "conversation.item.input_audio_transcription.delta" -> {
                    val delta = event.optString("delta")
                    _events.tryEmit(RealtimeVoiceEvent.UserTranscriptDelta(delta))
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = event.optString("transcript")
                    _events.tryEmit(RealtimeVoiceEvent.UserTranscriptCompleted(transcript))
                }
                "response.created" -> {
                    _events.tryEmit(RealtimeVoiceEvent.ResponseStarted)
                }
                "response.done" -> {
                    _events.tryEmit(RealtimeVoiceEvent.ResponseCompleted)
                }
                "response.output_text.delta",
                "response.output_audio_transcript.delta",
                "response.audio_transcript.delta" -> {
                    val delta = event.optString("delta")
                    _events.tryEmit(RealtimeVoiceEvent.ResponseTranscriptDelta(delta))
                    _events.tryEmit(RealtimeVoiceEvent.TranscriptReceived(delta, isFinal = false))
                }
                "response.output_audio_transcript.done",
                "response.audio_transcript.done" -> {
                    val transcript = event.optString("transcript")
                    _events.tryEmit(RealtimeVoiceEvent.ResponseTranscriptCompleted(transcript))
                    _events.tryEmit(RealtimeVoiceEvent.TranscriptReceived("", isFinal = true))
                }
                "response.output_audio.delta",
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

    private fun sendSessionUpdate(webSocket: WebSocket) {
        val voice = AppConfigManager.realtimeVoiceVoice.ifBlank { "marin" }
        val language = AppConfigManager.speechRecognitionLanguage
            .takeUnless { it == "auto" }
            ?.substringBefore('-')
            ?.takeIf { it.isNotBlank() }

        val transcription = JSONObject()
            .put("model", "gpt-realtime-whisper")
            .put("delay", "low")
        if (language != null) transcription.put("language", language)

        val session = JSONObject()
            .put("type", "realtime")
            .put("instructions", realtimeInstructions())
            .put(
                "audio",
                JSONObject()
                    .put(
                        "input",
                        JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                            .put("transcription", transcription)
                            .put(
                                "turn_detection",
                                JSONObject()
                                    .put("type", "server_vad")
                                    .put("threshold", 0.5)
                                    .put("prefix_padding_ms", 300)
                                    .put("silence_duration_ms", 550)
                            )
                    )
                    .put(
                        "output",
                        JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000))
                            .put("voice", voice)
                    )
            )

        webSocket.send(
            JSONObject()
                .put("type", "session.update")
                .put("session", session)
                .toString()
        )
    }

    private fun realtimeInstructions(): String {
        val name = AppConfigManager.agentName.ifBlank { "ClawDroid" }
        val purpose = AppConfigManager.agentPurpose.ifBlank { "help the user on Android" }
        return "You are $name, ClawDroid's live voice assistant. Keep replies brief, natural, and useful. " +
            "Speak conversationally, handle interruptions gracefully, and focus on this purpose: $purpose."
    }
}
