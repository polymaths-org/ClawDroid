package com.clawdroid.app.core.voice

import com.clawdroid.app.core.config.AppConfigManager
import java.util.ArrayDeque

class StreamingTtsController(private val voiceManager: VoiceManager) {
    private val buffer = StringBuilder()
    private val queue = ArrayDeque<String>()
    private var speaking = false
    private var paused = false
    private var generationActive = false
    private var onIdle: (() -> Unit)? = null

    val isSpeaking: Boolean get() = speaking || queue.isNotEmpty()

    fun begin(onIdle: (() -> Unit)? = null) {
        stop(clearAudio = false)
        generationActive = true
        this.onIdle = onIdle
    }

    fun onToken(token: String) {
        if (!AppConfigManager.overlayTtsStreamingEnabled || !AppConfigManager.overlayTtsAutoplay) return
        if (token.isBlank() || paused) return
        generationActive = true
        buffer.append(token)
        drainCompleteSentences()
    }

    fun complete() {
        val remaining = buffer.toString().trim()
        buffer.clear()
        if (remaining.isNotEmpty()) {
            enqueue(remaining)
        }
        generationActive = false
        maybeIdle()
    }

    fun pause() {
        paused = true
        voiceManager.stop()
        speaking = false
    }

    fun resume() {
        paused = false
        speakNext()
    }

    fun stop(clearAudio: Boolean = true) {
        buffer.clear()
        queue.clear()
        speaking = false
        paused = false
        generationActive = false
        if (clearAudio) voiceManager.stop()
    }

    private fun drainCompleteSentences() {
        while (true) {
            val text = buffer.toString()
            val index = text.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '\n' }
            if (index < 0) return
            val sentence = text.substring(0, index + 1).trim()
            buffer.delete(0, index + 1)
            if (sentence.length >= 2) {
                enqueue(sentence)
            }
        }
    }

    private fun enqueue(chunk: String) {
        val cleaned = chunk.trim()
        if (cleaned.isBlank()) return
        queue.add(cleaned)
        speakNext()
    }

    private fun speakNext() {
        if (paused || speaking) return
        val next = queue.poll() ?: run {
            maybeIdle()
            return
        }
        speaking = true
        voiceManager.speakWithNaturalBreaks(next) {
            speaking = false
            speakNext()
        }
    }

    private fun maybeIdle() {
        if (!generationActive && !speaking && queue.isEmpty()) {
            onIdle?.invoke()
            onIdle = null
        }
    }
}
