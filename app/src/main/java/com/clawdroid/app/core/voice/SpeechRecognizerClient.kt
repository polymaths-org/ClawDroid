package com.clawdroid.app.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.sqrt

class SpeechRecognizerClient(
    private val context: Context,
    private val forceSystemRecognizer: Boolean = false,
    private val fastMode: Boolean = true,
    private val wakeMode: Boolean = false,
) {

    private var recognizer: SpeechRecognizer? = null
    private var whisperJob: Job? = null
    private var whisperRecorder: AudioRecord? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _userVoiceAmplitude = MutableStateFlow(0f)
    val userVoiceAmplitude: StateFlow<Float> = _userVoiceAmplitude.asStateFlow()

    private var onResult: ((String) -> Unit)? = null
    private var onPartialResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var sessionPeakAmplitude = 0f

    fun startListening(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        cancelActiveRecognizer(clearCallbacks = false)

        this.onResult = onResult
        this.onPartialResult = onPartialResult
        this.onError = onError
        sessionPeakAmplitude = 0f

        if (shouldUseWhisper()) {
            startWhisperListening()
            return
        }

        recognizer = createConfiguredRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val language = AppConfigManager.speechRecognitionLanguage
            if (useLegacySystemRecognizer()) {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    if (language == "auto") Locale.getDefault() else Locale.forLanguageTag(language),
                )
            } else {
                if (language == "auto") {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(language).toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, if (wakeMode) 5 else 3)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    completeSilenceMillis(),
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    possiblyCompleteSilenceMillis(),
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    minimumInputMillis(),
                )
                if (!fastMode || wakeMode) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, AppConfigManager.speechPreferOnDevice)
                }
                if ((!fastMode || wakeMode) && android.os.Build.VERSION.SDK_INT >= 34 && AppConfigManager.speechLanguageSwitchEnabled) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(
                        RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                        RecognizerIntent.LANGUAGE_SWITCH_BALANCED,
                    )
                }
            }
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
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                _partialResult.value = text
                this@SpeechRecognizerClient.onPartialResult?.invoke(text)
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
                recognizer?.destroy()
                recognizer = null
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0.0f, 1.0f)
                sessionPeakAmplitude = maxOf(sessionPeakAmplitude, normalized)
                _userVoiceAmplitude.value = normalized
            }
        })

        recognizer?.startListening(intent)
    }

    private fun completeSilenceMillis(): Long = when {
        wakeMode -> 10_000L
        fastMode -> 520L
        else -> 900L
    }

    private fun possiblyCompleteSilenceMillis(): Long = when {
        wakeMode -> 6_000L
        fastMode -> 360L
        else -> 650L
    }

    private fun minimumInputMillis(): Long = when {
        wakeMode -> 1_100L
        fastMode -> 250L
        else -> 500L
    }

    fun stopListening() {
        recognizer?.stopListening()
        runCatching {
            whisperRecorder?.stop()
        }
    }

    fun cancelListening() {
        cancelActiveRecognizer(clearCallbacks = true)
    }

    private fun cancelActiveRecognizer(clearCallbacks: Boolean) {
        whisperJob?.cancel()
        whisperJob = null
        runCatching {
            whisperRecorder?.stop()
        }
        whisperRecorder?.release()
        whisperRecorder = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        _isListening.value = false
        _partialResult.value = ""
        _userVoiceAmplitude.value = 0f
        if (!clearCallbacks) return
        onResult = null
        onPartialResult = null
        onError = null
    }

    fun destroy() {
        cancelListening()
    }

    private fun useLegacySystemRecognizer(): Boolean =
        !wakeMode && AppConfigManager.speechRecognitionEngine == "system_legacy"

    private fun createConfiguredRecognizer(): SpeechRecognizer {
        if (useLegacySystemRecognizer()) {
            return SpeechRecognizer.createSpeechRecognizer(context)
        }
        val preferOnDevice = !forceSystemRecognizer && !fastMode && AppConfigManager.speechPreferOnDevice
        if (preferOnDevice &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun shouldUseWhisper(): Boolean {
        return !forceSystemRecognizer &&
            AppConfigManager.speechRecognitionEngine == "whisper" &&
            WhisperModelManager.isDownloaded(context, AppConfigManager.whisperModelSize) &&
            WhisperModelManager.findRuntimeExecutable(context) != null
    }

    @SuppressLint("MissingPermission")
    private fun startWhisperListening() {
        if (!WhisperModelManager.isDownloaded(context, AppConfigManager.whisperModelSize)) {
            reportWhisperError("Whisper model is not downloaded.")
            return
        }
        if (WhisperModelManager.findRuntimeExecutable(context) == null) {
            reportWhisperError("Whisper.cpp runtime is not installed.")
            return
        }

        whisperJob = scope.launch {
            val wavFile = File(context.cacheDir, "whisper_input_${System.currentTimeMillis()}.wav")
            try {
                val pcm = recordUntilSilence()
                if (pcm.size < SAMPLE_RATE_HZ * BYTES_PER_SAMPLE / 3) {
                    reportWhisperError("No speech detected")
                    return@launch
                }
                _partialResult.value = "Transcribing..."
                writePcm16Wav(wavFile, pcm, SAMPLE_RATE_HZ)
                val transcript = WhisperCliTranscriber(context)
                    .transcribe(wavFile, AppConfigManager.whisperModelSize)
                    .getOrThrow()
                withContext(Dispatchers.Main) {
                    _isListening.value = false
                    _partialResult.value = ""
                    _userVoiceAmplitude.value = 0f
                    onResult?.invoke(transcript)
                }
            } catch (error: Throwable) {
                reportWhisperError(error.message ?: "Whisper recognition failed")
            } finally {
                wavFile.delete()
                runCatching {
                    whisperRecorder?.release()
                }
                whisperRecorder = null
            }
        }
    }

    private fun reportWhisperError(message: String) {
        scope.launch(Dispatchers.Main) {
            _isListening.value = false
            _partialResult.value = ""
            _userVoiceAmplitude.value = 0f
            onError?.invoke(message)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun recordUntilSilence(): ByteArray = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(FRAME_BYTES * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        whisperRecorder = recorder
        val output = ByteArrayOutputStream()
        val frame = ByteArray(FRAME_BYTES)
        var speechStarted = false
        var silenceMs = 0L
        var recordedMs = 0L

        recorder.startRecording()
        _isListening.value = true
        while (isActive && recordedMs < MAX_RECORDING_MS) {
            val read = recorder.read(frame, 0, frame.size)
            if (read <= 0) continue
            output.write(frame, 0, read)
            recordedMs += FRAME_MS
            val amplitude = calculatePcmAmplitude(frame, read)
            _userVoiceAmplitude.value = amplitude
            if (amplitude >= configuredSpeechThreshold()) {
                speechStarted = true
                silenceMs = 0L
            } else if (speechStarted) {
                silenceMs += FRAME_MS
            }
            if (speechStarted && recordedMs >= MIN_RECORDING_MS && silenceMs >= END_SILENCE_MS) break
        }
        runCatching {
            recorder.stop()
        }
        output.toByteArray()
    }

    private fun calculatePcmAmplitude(bytes: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort()
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sum += normalized * normalized
            count++
            i += 2
        }
        return sqrt(sum / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }

    private fun configuredSpeechThreshold(): Float {
        return if (AppConfigManager.voiceNoiseGateEnabled) {
            AppConfigManager.voiceNoiseGate.coerceIn(0.03f, 0.60f)
        } else {
            SPEECH_THRESHOLD
        }
    }

    private fun writePcm16Wav(file: File, pcm: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { output ->
            val byteRate = sampleRate * BYTES_PER_SAMPLE
            val totalDataLen = pcm.size + 36
            val header = ByteArray(44)
            fun putString(offset: Int, value: String) {
                value.encodeToByteArray().copyInto(header, offset)
            }
            fun putIntLe(offset: Int, value: Int) {
                header[offset] = (value and 0xff).toByte()
                header[offset + 1] = ((value shr 8) and 0xff).toByte()
                header[offset + 2] = ((value shr 16) and 0xff).toByte()
                header[offset + 3] = ((value shr 24) and 0xff).toByte()
            }
            fun putShortLe(offset: Int, value: Int) {
                header[offset] = (value and 0xff).toByte()
                header[offset + 1] = ((value shr 8) and 0xff).toByte()
            }
            putString(0, "RIFF")
            putIntLe(4, totalDataLen)
            putString(8, "WAVE")
            putString(12, "fmt ")
            putIntLe(16, 16)
            putShortLe(20, 1)
            putShortLe(22, 1)
            putIntLe(24, sampleRate)
            putIntLe(28, byteRate)
            putShortLe(32, BYTES_PER_SAMPLE)
            putShortLe(34, 16)
            putString(36, "data")
            putIntLe(40, pcm.size)
            output.write(header)
            output.write(pcm)
        }
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val FRAME_MS = 40L
        private const val FRAME_BYTES = 1_280
        private const val MIN_RECORDING_MS = 600L
        private const val END_SILENCE_MS = 900L
        private const val MAX_RECORDING_MS = 12_000L
        private const val SPEECH_THRESHOLD = 0.025f
    }
}
