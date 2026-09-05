package com.clawdroid.app.core.voice

import kotlinx.coroutines.flow.StateFlow

enum class TtsEngineState { Idle, Initializing, Ready, Unavailable }

interface TtsEngine {
    val state: StateFlow<TtsEngineState>
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun destroy()
}
