package com.clawdroid.app.core.assistant.voice

import android.content.Intent
import android.speech.RecognitionService

class ClawRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // No-op for skeleton implementation; could delegate to native SpeechRecognizer or local APIs in future
    }

    override fun onCancel(listener: Callback?) {
        // No-op
    }

    override fun onStopListening(listener: Callback?) {
        // No-op
    }
}
