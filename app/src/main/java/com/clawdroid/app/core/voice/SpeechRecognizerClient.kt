package com.clawdroid.app.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerClient(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _userVoiceAmplitude = MutableStateFlow(0f)
    val userVoiceAmplitude: StateFlow<Float> = _userVoiceAmplitude.asStateFlow()

    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun startListening(
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
    ) {
        stopListening()

        this.onResult = onResult
        this.onError = onError

        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                _isListening.value = true
            }

            override fun onResults(results: Bundle) {
                _isListening.value = false
                _partialResult.value = ""
                _userVoiceAmplitude.value = 0f
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                this@SpeechRecognizerClient.onResult?.invoke(text)
                recognizer?.destroy()
                recognizer = null
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList("results_recognition")?.firstOrNull() ?: ""
                _partialResult.value = text
            }

            override fun onError(error: Int) {
                _isListening.value = false
                _partialResult.value = ""
                _userVoiceAmplitude.value = 0f
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    else -> "Recognition error ($error)"
                }
                this@SpeechRecognizerClient.onError?.invoke(msg)
                this@SpeechRecognizerClient.onResult?.invoke("")
                recognizer?.destroy()
                recognizer = null
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0.0f, 1.0f)
                _userVoiceAmplitude.value = normalized
            }
        })

        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancelListening() {
        onResult = null
        onError = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _isListening.value = false
        _partialResult.value = ""
        _userVoiceAmplitude.value = 0f
    }

    fun destroy() {
        cancelListening()
    }
}
