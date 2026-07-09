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

class VoiceManager(private val context: Context) {

    enum class State { Idle, InitializingPiper, Ready, Unavailable }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _agentVoiceAmplitude = MutableStateFlow(0f)
    val agentVoiceAmplitude: StateFlow<Float> = _agentVoiceAmplitude.asStateFlow()

    private val _agentVoiceLevels = MutableStateFlow<List<Float>>(emptyList())
    val agentVoiceLevels: StateFlow<List<Float>> = _agentVoiceLevels.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _piperAvailable = MutableStateFlow(false)
    val piperAvailable: StateFlow<Boolean> = _piperAvailable.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeEngine: TtsEngine? = null
    private var activeEngineId: String? = null
    private var piperEngine: PiperEngine? = null
    private var activeVoice: String? = null
    private val pendingQueue = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val speechQueue = ArrayDeque<Pair<String, (() -> Unit)?>>()
    private var outputAudioMeter: OutputAudioMeter? = null

    private fun getDesiredVoice(): String {
        return AppConfigManager.ttsVoice.takeIf { it.isNotBlank() }
            ?: AppConfigManager.agentVoiceProfile.takeIf { it.isNotBlank() }
            ?: "female"
    }

    init {
        _state.value = State.Idle
        val desired = AppConfigManager.ttsEngine
        scope.launch {
            if (TtsEngineManager.isDeviceEngineId(desired)) {
                initAndroidTts(desired)
            } else {
                initCloudTts(desired)
            }
            drainQueue()
        }
    }

    private fun createCloudEngine(engineId: String): TtsEngine? = when (engineId) {
        "openai" -> OpenAITtsEngine(context, scope)
        "elevenlabs" -> ElevenLabsTtsEngine(context, scope)
        "deepgram" -> DeepgramTtsEngine(context, scope)
        else -> OpenAITtsEngine(context, scope)
    }

    private fun initCloudTts(engineId: String) {
        val engine = createCloudEngine(engineId)
        if (engine?.state?.value == TtsEngineState.Ready) {
            activeEngine = engine
            activeEngineId = engineId
            activeVoice = getDesiredVoice()
            _state.value = State.Ready
            Log.i("VoiceManager", "Cloud TTS ready: $engineId")
        } else {
            activeEngine = null
            _state.value = State.Unavailable
            Log.w("VoiceManager", "Cloud TTS unavailable: $engineId")
        }
    }

    private suspend fun initAndroidTts(engineId: String = "device") {
        val packageName = TtsEngineManager.packageNameFromDeviceEngineId(engineId)
        val android = AndroidTtsEngine(context, packageName)
        android.state.first { it != TtsEngineState.Initializing }
        if (android.state.value != TtsEngineState.Ready && packageName != null) {
            Log.w("VoiceManager", "Android TTS unavailable: $packageName, falling back to system default")
            android.destroy()
            initAndroidTts("device")
            return
        }
        val voice = getDesiredVoice()
        android.setVoiceProfile(voice)
        activeVoice = voice
        activeEngine = android
        activeEngineId = engineId
        _state.value = State.Ready
        Log.i("VoiceManager", "Android TTS ready engine=${packageName ?: "default"} voiceProfile=$voice")
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
                activeEngineId = "piper"
                _state.value = State.Ready
                _piperAvailable.value = true
                _downloadProgress.value = 0f
                Log.i("VoiceManager", "Piper TTS ready after download")
            } else {
                _piperAvailable.value = false
                _downloadProgress.value = 0f
                if (activeEngine == null) {
                    initAndroidTts("device")
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
        val currentEngine = activeEngineId

        // Same engine, same voice — nothing to do
        if (currentEngine == desiredEngine && activeVoice == desiredVoice && activeEngine != null) return

        scope.launch {
            // Same engine type but voice changed — just update profile in-place
            if (currentEngine == desiredEngine && activeEngine is AndroidTtsEngine) {
                (activeEngine as AndroidTtsEngine).setVoiceProfile(desiredVoice)
                activeVoice = desiredVoice
                Log.i("VoiceManager", "Updated voice profile to: $desiredVoice")
                return@launch
            }

            // Engine changed — destroy old, create new
            activeEngine?.destroy()
            activeEngine = null
            activeEngineId = null
            when {
                TtsEngineManager.isDeviceEngineId(desiredEngine) -> initAndroidTts(desiredEngine)
                desiredEngine == "openai" -> {
                    val engine = OpenAITtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeEngineId = desiredEngine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to OpenAI TTS")
                    } else {
                        Log.w("VoiceManager", "OpenAI TTS unavailable")
                        _state.value = State.Unavailable
                    }
                }
                desiredEngine == "elevenlabs" -> {
                    val engine = ElevenLabsTtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeEngineId = desiredEngine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to ElevenLabs TTS")
                    } else {
                        Log.w("VoiceManager", "ElevenLabs unavailable")
                        _state.value = State.Unavailable
                    }
                }
                desiredEngine == "deepgram" -> {
                    val engine = DeepgramTtsEngine(context, scope)
                    if (engine.state.value == TtsEngineState.Ready) {
                        activeEngine = engine
                        activeEngineId = desiredEngine
                        activeVoice = desiredVoice
                        Log.i("VoiceManager", "Switched to Deepgram TTS")
                    } else {
                        Log.w("VoiceManager", "Deepgram unavailable")
                        _state.value = State.Unavailable
                    }
                }
                else -> initCloudTts("openai")
            }
            if (activeEngine != null) _state.value = State.Ready
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        reconfigure()
        val spokenText = TextCleaningUtils.fullyCleanForTts(text)
        if (spokenText.isBlank()) {
            onDone?.invoke()
            return
        }
        if (_muted.value) {
            onDone?.invoke()
            return
        }
        val engine = activeEngine
        if (engine == null || _state.value != State.Ready) {
            pendingQueue.add(spokenText to onDone)
            return
        }
        var queued = false
        synchronized(speechQueue) {
            if (_isSpeaking.value || speechQueue.isNotEmpty()) {
                speechQueue.addLast(spokenText to onDone)
                queued = true
            }
        }
        if (queued) return
        doSpeak(spokenText, engine, onDone)
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
        VoiceActivityGate.markAgentSpeechStarted()
        startOutputVisualization()
        // Apply configured speed via AndroidTTS if applicable
        val speed = AppConfigManager.ttsSpeed
        if (engine is AndroidTtsEngine) {
            engine.setSpeed(speed)
        }
        engine.speak(text) {
            stopOutputVisualization()
            _isSpeaking.value = false
            VoiceActivityGate.markAgentSpeechEnded()
            onDone?.invoke()
            speakNextQueued()
        }
    }

    private fun speakNextQueued() {
        val engine = activeEngine ?: return
        val next = synchronized(speechQueue) {
            if (speechQueue.isEmpty()) null else speechQueue.removeFirst()
        } ?: return
        doSpeak(next.first, engine, next.second)
    }

    private fun startOutputVisualization() {
        stopOutputVisualization()
        val meter = OutputAudioMeter { levels, amplitude ->
            _agentVoiceLevels.value = levels
            _agentVoiceAmplitude.value = amplitude
        }
        outputAudioMeter = meter
        if (meter.start()) return

        amplitudeJob = scope.launch {
            while (isActive) {
                _agentVoiceLevels.value = List(14) { 0.12f }
                _agentVoiceAmplitude.value = 0.12f
                delay(120)
            }
        }
    }

    private fun stopOutputVisualization() {
        outputAudioMeter?.stop()
        outputAudioMeter = null
        amplitudeJob?.cancel()
        amplitudeJob = null
        _agentVoiceAmplitude.value = 0f
        _agentVoiceLevels.value = emptyList()
    }

    private fun drainQueue() {
        val queue = pendingQueue.toList()
        pendingQueue.clear()
        for ((text, callback) in queue) {
            speak(text, callback)
        }
    }

    fun stop() {
        stopOutputVisualization()
        _isSpeaking.value = false
        VoiceActivityGate.markAgentSpeechEnded()
        synchronized(speechQueue) {
            speechQueue.clear()
        }
        activeEngine?.stop()
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) stop()
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
