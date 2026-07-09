package com.clawdroid.app.core.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService

class ClawVoiceInteractionService : VoiceInteractionService() {
    override fun onCreate() {
        super.onCreate()
        // Entry point for system assistant initialization
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        showSession(Bundle.EMPTY, 0)
    }
}
