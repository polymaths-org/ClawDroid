package com.clawdroid.app.core.assistant.voice

import android.content.Context
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.assistant.AssistantMode
import com.clawdroid.app.core.assistant.AssistantInvocationRouter
import com.clawdroid.app.core.voice.SpeechRecognizerClient
import com.clawdroid.app.core.voice.VoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

class ChainedVoicePipeline(
    private val context: Context,
    private val voiceManager: VoiceManager = VoiceManager(context),
    private val recognizerClient: SpeechRecognizerClient = SpeechRecognizerClient(context)
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startSession() {
        voiceManager.speak("How can I help you?") {
            recognizerClient.startListening(
                onResult = { text ->
                    if (text.isNotBlank()) {
                        val invocation = AssistantInvocation(
                            id = UUID.randomUUID().toString(),
                            source = AssistantInvocationSource.VOICE_CALL,
                            mode = AssistantMode.VOICE_CHAT,
                            userText = text,
                            contextSnapshot = null,
                            mediaPath = null,
                            mediaMimeType = null,
                            projectId = null,
                            conversationId = null,
                            createdAt = System.currentTimeMillis()
                        )
                        AssistantInvocationRouter.invoke(context, invocation)
                    }
                },
                onError = { _ ->
                    voiceManager.speak("Sorry, I didn't catch that.")
                }
            )
        }
    }

    fun stopSession() {
        recognizerClient.stopListening()
        voiceManager.stop()
    }
}
