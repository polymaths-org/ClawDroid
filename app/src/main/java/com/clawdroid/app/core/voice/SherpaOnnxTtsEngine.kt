package com.clawdroid.app.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SherpaOnnxTtsEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) : TtsEngine {
    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        const val DISPLAY_NAME = "Sherpa-ONNX Offline Voice"

        fun selectedPreset(): OfflineTtsModelPreset =
            OfflineTtsModelCatalog.byId(AppConfigManager.offlineTtsModelId)

        fun modelDir(context: Context): File =
            OfflineTtsModelCatalog.modelDir(context, selectedPreset())

        fun isModelInstalled(context: Context): Boolean =
            OfflineTtsModelCatalog.isInstalled(context, selectedPreset())

        fun isAbiSupported(): Boolean =
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" || it == "x86_64" || it == "x86" }

        fun deleteModel(context: Context) {
            OfflineTtsModelCatalog.modelDir(context, selectedPreset()).deleteRecursively()
        }

        fun nativeLibraryAvailable(): Boolean =
            runCatching {
                Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
                true
            }.getOrDefault(false)

        fun readinessMessage(context: Context, preset: OfflineTtsModelPreset = selectedPreset()): String {
            if (!isAbiSupported()) return "Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
            if (!nativeLibraryAvailable()) return "Sherpa native runtime is not available"
            if (!OfflineTtsModelCatalog.isInstalled(context, preset)) return "${preset.label} is not installed"
            return runCatching {
                val engine = OfflineTts(config = buildConfigFor(context, preset))
                engine.release()
                "Ready"
            }.getOrElse { error ->
                "Init failed: ${error.message ?: error::class.java.simpleName}"
            }
        }

        fun buildConfigFor(context: Context, preset: OfflineTtsModelPreset): OfflineTtsConfig {
            val dir = OfflineTtsModelCatalog.modelDir(context, preset).absolutePath
            val modelConfig = when (preset.engineType) {
                OfflineTtsEngineType.VITS -> OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$dir/${preset.modelFile}",
                        tokens = "$dir/tokens.txt",
                        dataDir = "$dir/espeak-ng-data",
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                )
            }
            return OfflineTtsConfig(model = modelConfig, maxNumSentences = 1, silenceScale = 0.2f)
        }
    }

    private val _state = MutableStateFlow(TtsEngineState.Initializing)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var offlineTts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var speakJob: Job? = null
    private val preset = selectedPreset()

    init {
        _state.value = runCatching {
            check(isAbiSupported()) { "Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}" }
            check(OfflineTtsModelCatalog.isInstalled(context, preset)) { "Model files are missing" }
            offlineTts = OfflineTts(config = buildConfigFor(context, preset))
            TtsEngineState.Ready
        }.onFailure { error ->
            Log.w(TAG, "Sherpa-ONNX unavailable: ${error.message}", error)
        }.getOrDefault(TtsEngineState.Unavailable)
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        val engine = offlineTts
        if (_state.value != TtsEngineState.Ready || engine == null || text.isBlank()) {
            onDone?.invoke()
            return
        }
        speakJob?.cancel()
        speakJob = scope.launch(Dispatchers.IO) {
            _isSpeaking.value = true
            runCatching {
                val sid = AppConfigManager.offlineTtsSpeakerId.coerceAtLeast(0)
                val audio = engine.generate(text, sid = sid, speed = AppConfigManager.ttsSpeed.coerceIn(0.5f, 2.0f))
                withContext(Dispatchers.Main) {
                    play(audio.samples, audio.sampleRate, onDone)
                }
            }.onFailure { error ->
                Log.w(TAG, "Sherpa synthesis failed: ${error.message}", error)
                _isSpeaking.value = false
                onDone?.invoke()
            }
        }
    }

    override fun stop() {
        speakJob?.cancel()
        speakJob = null
        releaseTrack()
    }

    private fun releaseTrack() {
        audioTrack?.apply {
            runCatching { stop() }
            release()
        }
        audioTrack = null
        _isSpeaking.value = false
    }

    override fun destroy() {
        stop()
        offlineTts?.release()
        offlineTts = null
        _state.value = TtsEngineState.Idle
    }

    private fun play(samples: FloatArray, sampleRate: Int, onDone: (() -> Unit)?) {
        releaseTrack()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 4)
            .build()
        audioTrack = track
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written <= 0) {
            _isSpeaking.value = false
            onDone?.invoke()
            return
        }
        track.setNotificationMarkerPosition(written)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack) {
                _isSpeaking.value = false
                track.release()
                if (audioTrack == track) audioTrack = null
                onDone?.invoke()
            }

            override fun onPeriodicNotification(track: AudioTrack) = Unit
        })
        _isSpeaking.value = true
        track.play()
    }
}
