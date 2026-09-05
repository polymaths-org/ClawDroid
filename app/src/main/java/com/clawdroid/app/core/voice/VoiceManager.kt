package com.clawdroid.app.core.voice

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class VoiceManager(private val context: Context) {

    enum class State { Idle, InitializingPiper, Ready, Unavailable }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _agentVoiceAmplitude = MutableStateFlow(0f)
    val agentVoiceAmplitude: StateFlow<Float> = _agentVoiceAmplitude.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _piperAvailable = MutableStateFlow(false)
    val piperAvailable: StateFlow<Boolean> = _piperAvailable.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeEngine: TtsEngine? = null
    private var piperEngine: PiperEngine? = null
    private var activeVoice: String? = null
    private val pendingQueue = mutableListOf<Pair<String, (() -> Unit)?>>()

    private fun getDesiredVoice(): String {
        return AppConfigManager.ttsVoice.takeIf { it.isNotBlank() }
            ?: AppConfigManager.agentVoiceProfile.takeIf { it.isNotBlank() }
            ?: "female"
    }

    private fun currentEngineId(): String? = when (activeEngine) {
        is PiperEngine -> "piper"
        is SherpaOnnxTtsEngine -> "sherpa"
        is OpenAITtsEngine -> "openai"
        is ElevenLabsTtsEngine -> "elevenlabs"
        is DeepgramTtsEngine -> "deepgram"
        is AndroidTtsEngine -> "device"
        else -> null
    }

    init {
        _state.value = State.Idle
        val desired = AppConfigManager.ttsEngine
        scope.launch {
            when (desired) {
                "sherpa" -> initSherpaOrFallback()
                "openai", "elevenlabs", "deepgram", "piper" -> reconfigure()
                else -> initAndroidTts()
            }
            drainQueue()
        }
    }

    private suspend fun initAndroidTts() {
        val android = AndroidTtsEngine(context)
        android.state.first { it != TtsEngineState.Initializing }
        val voice = getDesiredVoice()
        android.setVoiceProfile(voice)
        activeVoice = voice
        activeEngine = android
        _state.value = State.Ready
        Log.i("VoiceManager", "Android TTS ready with voice profile: $voice")
    }

    private suspend fun initSherpaOrFallback() {
        val sherpa = SherpaOnnxTtsEngine(context, scope)
        if (sherpa.state.value == TtsEngineState.Ready) {
            activeEngine = sherpa
            activeVoice = getDesiredVoice()
            _state.value = State.Ready
            Log.i("VoiceManager", "Sherpa-ONNX TTS ready")
        } else {
            Log.w("VoiceManager", "Sherpa-ONNX unavailable, falling back to Android TTS")
            initAndroidTts()
        }
    }

    fun triggerPiperDownload() {
        if (_isDownloading.value || _piperAvailable.value) return
        _isDownloading.value = true
        _state.value = State.InitializingPiper
        scope.launch {
            val piper = PiperEngine(context, scope)
            scope.launch {
                piper.downloadProgress.collect { progress ->
                    _downloadProgress.value = progress
                }
            }
            piper.startDownload()
            piper.state.first { it != TtsEngineState.Initializing }
            if (piper.state.value == TtsEngineState.Ready) {
                piperEngine = piper
                activeEngine = piper
                _state.value = State.Ready
                _piperAvailable.value = true
                _downloadProgress.value = 0f
                Log.i("VoiceManager", "Piper TTS ready after download")
            } else {
                _piperAvailable.value = false
                _downloadProgress.value = 0f
                if (activeEngine == null) {
                    initAndroidTts()
                } else {
                    _state.value = State.Ready
                }
                Log.i("VoiceManager", "Piper download failed, keeping current engine")
            }
            _isDownloading.value = false
            drainQueue()
        }
    }

    /**
     * Re-reads AppConfigManager and switches the active engine or voice profile if the
     * user changed settings since last speak.
     */
    fun reconfigure() {
        val desiredEngine = AppConfigManager.ttsEngine
        val desiredVoice = getDesiredVoice()
        val currentEngine = currentEngineId()

        // Same engine, same voice — nothing to do
        if (currentEngine == desiredEngine && activeVoice == desiredVoice && activeEngine != null) return

        scope.launch {
            // Same engine type but voice changed — just update profile in-place
            if (currentEngine == desiredEngine && activeEngine is AndroidTtsEngine) {
                (activeEngine as AndroidTtsEngine).setVoiceProfile(desiredVoice)
                activeVoice = desiredVoice
                Log.i("VoiceManager", "Updated voice profile to: $desiredVoice")
                drainQueue()
                return@launch
            }

            // Engine changed — destroy old, create new
            activeEngine?.destroy()
            activeEngine = null
            when (desiredEngine) {
                "piper" -> {
                    if (piperEngine?.state?.value == TtsEngineState.Ready) {
                        activeEngine = piperEngine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to Piper engine")
                    } else {
                        initAndroidTts()
                    }
                }
                "device" -> initAndroidTts()
                "sherpa" -> initSherpaOrFallback()
                "openai" -> {
                    val engine = OpenAITtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to OpenAI TTS")
                    } else {
                        Log.w("VoiceManager", "OpenAI TTS unavailable, falling back")
                        initAndroidTts()
                    }
                }
                "elevenlabs" -> {
                    val engine = ElevenLabsTtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to ElevenLabs TTS")
                    } else {
                        Log.w("VoiceManager", "ElevenLabs unavailable, falling back")
                        initAndroidTts()
                    }
                }
                "deepgram" -> {
                    val engine = DeepgramTtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to Deepgram TTS")
                    } else {
                        Log.w("VoiceManager", "Deepgram unavailable, falling back")
                        initAndroidTts()
                    }
                }
                else -> initAndroidTts()
            }
            _state.value = State.Ready
            drainQueue()
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val desiredEngine = AppConfigManager.ttsEngine
        val desiredVoice = getDesiredVoice()
        val needsSwitch = currentEngineId() != desiredEngine || activeVoice != desiredVoice || activeEngine == null
        if (needsSwitch) {
            pendingQueue.add(text to onDone)
            reconfigure()
            return
        }
        val engine = activeEngine
        if (engine == null || _state.value != State.Ready) {
            pendingQueue.add(text to onDone)
            return
        }
        doSpeak(text, engine, onDone)
    }

    fun speakThinkingPhrase() {
        speak(ThinkingPhrases.random())
    }

    fun speakWithNaturalBreaks(text: String, onDone: (() -> Unit)? = null) {
        val processed = processForNaturalSpeech(text)
        speak(processed, onDone)
    }

    private fun processForNaturalSpeech(text: String): String {
        if (text.isBlank()) return text
        val cleaned = text
            .replace(Regex("\\*+"), "")
            .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .trim()
        val sentences = cleaned.split(Regex("(?<=[.!?])\\s+"))
        return sentences.joinToString(" ") { sentence ->
            val trimmed = sentence.trim()
            when {
                trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?') -> "$trimmed "
                trimmed.isNotEmpty() -> "$trimmed. "
                else -> trimmed
            }
        }
    }

    private var amplitudeJob: kotlinx.coroutines.Job? = null

    private fun doSpeak(text: String, engine: TtsEngine, onDone: (() -> Unit)?) {
        _isSpeaking.value = true
        startAmplitudeAnimation()
        // Apply configured speed via AndroidTTS if applicable
        val speed = AppConfigManager.ttsSpeed
        if (engine is AndroidTtsEngine) {
            engine.setSpeed(speed)
        }
        engine.speak(text) {
            stopAmplitudeAnimation()
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    private fun startAmplitudeAnimation() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive) {
                val amp = ((sin(System.currentTimeMillis() * 0.005) + 1.0) / 2.0).toFloat()
                _agentVoiceAmplitude.value = amp * 0.6f + 0.2f
                delay(50)
            }
        }
    }

    private fun stopAmplitudeAnimation() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _agentVoiceAmplitude.value = 0f
    }

    private fun drainQueue() {
        val queue = pendingQueue.toList()
        pendingQueue.clear()
        val engine = activeEngine ?: return
        for ((text, callback) in queue) {
            doSpeak(text, engine, callback)
        }
    }

    fun stop() {
        stopAmplitudeAnimation()
        _isSpeaking.value = false
        activeEngine?.stop()
    }

    val isActivelySpeaking: Boolean get() = _isSpeaking.value

    fun destroy() {
        stop()
        pendingQueue.clear()
        activeEngine?.destroy()
        piperEngine?.destroy()
        activeEngine = null
        piperEngine = null
        _state.value = State.Idle
    }
}
