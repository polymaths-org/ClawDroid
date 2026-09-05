package com.clawdroid.app.core.assistant.voice

import kotlinx.coroutines.flow.Flow

sealed class RealtimeVoiceEvent {
    object Connected : RealtimeVoiceEvent()
    object Disconnected : RealtimeVoiceEvent()
    data class TranscriptReceived(val text: String, val isFinal: Boolean) : RealtimeVoiceEvent()
    data class AudioReceived(val chunk: ByteArray) : RealtimeVoiceEvent()
    data class Error(val message: String) : RealtimeVoiceEvent()
}

interface VoiceTransport {
    val events: Flow<RealtimeVoiceEvent>
    suspend fun connect(clientSecret: String)
    suspend fun sendAudio(frame: ByteArray)
    suspend fun sendText(text: String)
    suspend fun interrupt()
    suspend fun disconnect()
}
