package com.clawdroid.app.core.voice

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object VoiceActivityGate {
    private val agentSpeaking = AtomicBoolean(false)
    private val suppressUntilMs = AtomicLong(0L)

    fun markAgentSpeechStarted() {
        agentSpeaking.set(true)
        suppressFor(900L)
    }

    fun markAgentSpeechEnded() {
        agentSpeaking.set(false)
        suppressFor(1_400L)
    }

    fun markAgentAudioPlayback() {
        suppressFor(650L)
    }

    fun shouldSuppressRecognition(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        return agentSpeaking.get() || nowMs < suppressUntilMs.get()
    }

    private fun suppressFor(durationMs: Long) {
        val until = SystemClock.elapsedRealtime() + durationMs
        while (true) {
            val current = suppressUntilMs.get()
            if (until <= current || suppressUntilMs.compareAndSet(current, until)) return
        }
    }
}
