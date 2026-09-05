package com.clawdroid.app.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AndroidTtsEngine(private val context: Context) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Idle)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private val speakFlag = AtomicBoolean(false)

    init {
        _state.value = TtsEngineState.Initializing
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureVoice()
                _state.value = TtsEngineState.Ready
            } else {
                _state.value = TtsEngineState.Unavailable
            }
        }
    }

    private fun configureVoice() {
        val voiceOption = AppConfigManager.ttsVoice.takeIf { it.isNotBlank() }
            ?: AppConfigManager.agentVoiceProfile.takeIf { it.isNotBlank() }
            ?: "female"
        setVoiceProfile(voiceOption)
    }

    fun setVoiceProfile(voiceOption: String) {
        tts?.let { engine ->
            engine.language = Locale.US

            val pitch = when (voiceOption) {
                "female" -> 1.15f
                "female_high" -> 1.40f
                "male" -> 0.85f
                "male_deep" -> 0.65f
                "synth" -> 0.55f
                else -> 1.0f
            }
            val rate = when (voiceOption) {
                "female" -> 1.05f
                "female_high" -> 1.0f
                "male" -> 0.95f
                "male_deep" -> 0.90f
                "synth" -> 0.85f
                else -> 1.0f
            }

            engine.setPitch(pitch)
            engine.setSpeechRate(rate)

            runCatching {
                val isMale = voiceOption.contains("male")
                val isFemale = voiceOption.contains("female")

                if (isMale) {
                    val neuralMale = engine.voices?.firstOrNull { v ->
                        v.locale.language == "en" &&
                            v.name.contains("male", ignoreCase = true) &&
                            (v.name.contains("neural", ignoreCase = true) ||
                                v.name.contains("high", ignoreCase = true) ||
                                v.name.contains("enhanced", ignoreCase = true))
                    }
                    if (neuralMale != null) {
                        engine.voice = neuralMale
                        return@runCatching
                    }
                    val maleVoice = engine.voices?.firstOrNull { v ->
                        v.locale.language == "en" && v.name.contains("male", ignoreCase = true)
                    }
                    if (maleVoice != null) {
                        engine.voice = maleVoice
                        return@runCatching
                    }
                } else if (isFemale) {
                    val neuralFemale = engine.voices?.firstOrNull { v ->
                        v.locale.language == "en" &&
                            v.name.contains("female", ignoreCase = true) &&
                            (v.name.contains("neural", ignoreCase = true) ||
                                v.name.contains("high", ignoreCase = true) ||
                                v.name.contains("enhanced", ignoreCase = true))
                    }
                    if (neuralFemale != null) {
                        engine.voice = neuralFemale
                        return@runCatching
                    }
                    val femaleVoice = engine.voices?.firstOrNull { v ->
                        v.locale.language == "en" && v.name.contains("female", ignoreCase = true)
                    }
                    if (femaleVoice != null) {
                        engine.voice = femaleVoice
                        return@runCatching
                    }
                }

                // Fallback: best offline English voice available
                val hqVoice = engine.voices?.firstOrNull { v ->
                    v.locale.language == "en" && !v.isNetworkConnectionRequired
                }
                if (hqVoice != null) {
                    engine.voice = hqVoice
                }
            }
        }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (_state.value != TtsEngineState.Ready) {
            onDone?.invoke()
            return
        }
        doSpeak(text, onDone)
    }

    override fun stop() {
        speakFlag.set(false)
        _isSpeaking.value = false
        tts?.stop()
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    override fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        _state.value = TtsEngineState.Idle
    }

    private fun doSpeak(text: String, onDone: (() -> Unit)?) {
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uttId: String) {
                speakFlag.set(true)
                _isSpeaking.value = true
            }
            override fun onDone(uttId: String) {
                speakFlag.set(false)
                _isSpeaking.value = false
                if (uttId == utteranceId) onDone?.invoke()
            }
            @Suppress("DEPRECATION")
            override fun onError(uttId: String) {
                speakFlag.set(false)
                _isSpeaking.value = false
                if (uttId == utteranceId) onDone?.invoke()
            }
            override fun onError(uttId: String?, errorCode: Int) {
                speakFlag.set(false)
                _isSpeaking.value = false
                if (uttId == utteranceId) onDone?.invoke()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
}
