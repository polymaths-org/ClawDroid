package com.clawdroid.app.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.assistant.voice.OpenAIRealtimeVoiceTransport
import com.clawdroid.app.core.assistant.voice.RealtimeVoiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class RealtimeAudioSession(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val realtimeClient: OpenAIRealtimeClient = OpenAIRealtimeClient(),
    private val transport: OpenAIRealtimeVoiceTransport = OpenAIRealtimeVoiceTransport(),
) {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _userAmplitude = MutableStateFlow(0f)
    val userAmplitude: StateFlow<Float> = _userAmplitude.asStateFlow()

    private val _agentAmplitude = MutableStateFlow(0f)
    val agentAmplitude: StateFlow<Float> = _agentAmplitude.asStateFlow()

    private var micJob: Job? = null
    private var eventsJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    suspend fun start(
        onStatus: suspend (String) -> Unit = {},
        onUserTranscriptDelta: suspend (String) -> Unit = {},
        onUserTranscriptCompleted: suspend (String) -> Unit = {},
        onAgentTranscriptDelta: suspend (String) -> Unit = {},
        onAgentTranscriptCompleted: suspend (String) -> Unit = {},
        onUserSpeechStarted: suspend () -> Unit = {},
        onAgentResponseStarted: suspend () -> Unit = {},
        onAgentResponseCompleted: suspend () -> Unit = {},
        onError: suspend (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "Microphone permission is required for realtime voice." }

            stop()
            onStatus("Creating realtime session...")
            val secret = realtimeClient.createClientSecret().getOrThrow()
            startPlayback()
            startEventLoop(
                onStatus = onStatus,
                onUserTranscriptDelta = onUserTranscriptDelta,
                onUserTranscriptCompleted = onUserTranscriptCompleted,
                onAgentTranscriptDelta = onAgentTranscriptDelta,
                onAgentTranscriptCompleted = onAgentTranscriptCompleted,
                onUserSpeechStarted = onUserSpeechStarted,
                onAgentResponseStarted = onAgentResponseStarted,
                onAgentResponseCompleted = onAgentResponseCompleted,
                onError = onError,
            )
            transport.connect(secret.value)
            startMicLoop()
            _isActive.value = true
        }.onFailure { error ->
            onError(error.message ?: "Realtime voice failed to start.")
            stop()
        }
    }

    suspend fun sendText(text: String) {
        if (text.isNotBlank()) transport.sendText(text)
    }

    suspend fun interrupt() {
        transport.interrupt()
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        _agentAmplitude.value = 0f
        VoiceActivityGate.markAgentSpeechEnded()
    }

    suspend fun stop() {
        micJob?.cancelAndJoin()
        eventsJob?.cancelAndJoin()
        micJob = null
        eventsJob = null
        VoiceActivityGate.markAgentSpeechEnded()
        releaseAudioEffects()
        runCatching {
            audioRecord?.stop()
        }
        audioRecord?.release()
        audioRecord = null
        runCatching {
            audioTrack?.stop()
        }
        audioTrack?.release()
        audioTrack = null
        transport.disconnect()
        _isActive.value = false
        _userAmplitude.value = 0f
        _agentAmplitude.value = 0f
    }

    fun destroy() {
        scope.launch {
            stop()
        }
    }

    private fun startEventLoop(
        onStatus: suspend (String) -> Unit,
        onUserTranscriptDelta: suspend (String) -> Unit,
        onUserTranscriptCompleted: suspend (String) -> Unit,
        onAgentTranscriptDelta: suspend (String) -> Unit,
        onAgentTranscriptCompleted: suspend (String) -> Unit,
        onUserSpeechStarted: suspend () -> Unit,
        onAgentResponseStarted: suspend () -> Unit,
        onAgentResponseCompleted: suspend () -> Unit,
        onError: suspend (String) -> Unit,
    ) {
        eventsJob = scope.launch {
            transport.events.collect { event ->
                when (event) {
                    RealtimeVoiceEvent.Connected -> onStatus("Realtime voice ready")
                    RealtimeVoiceEvent.Disconnected -> {
                        VoiceActivityGate.markAgentSpeechEnded()
                        _isActive.value = false
                        onStatus("Realtime voice disconnected")
                    }
                    RealtimeVoiceEvent.UserSpeechStarted -> onUserSpeechStarted()
                    RealtimeVoiceEvent.UserSpeechStopped -> Unit
                    is RealtimeVoiceEvent.UserTranscriptDelta -> onUserTranscriptDelta(event.text)
                    is RealtimeVoiceEvent.UserTranscriptCompleted -> onUserTranscriptCompleted(event.text)
                    RealtimeVoiceEvent.ResponseStarted -> {
                        VoiceActivityGate.markAgentSpeechStarted()
                        onAgentResponseStarted()
                    }
                    RealtimeVoiceEvent.ResponseCompleted -> {
                        VoiceActivityGate.markAgentSpeechEnded()
                        onAgentResponseCompleted()
                    }
                    is RealtimeVoiceEvent.ResponseTranscriptDelta -> onAgentTranscriptDelta(event.text)
                    is RealtimeVoiceEvent.ResponseTranscriptCompleted -> onAgentTranscriptCompleted(event.text)
                    is RealtimeVoiceEvent.TranscriptReceived -> Unit
                    is RealtimeVoiceEvent.AudioReceived -> playPcm(event.chunk)
                    is RealtimeVoiceEvent.Error -> {
                        VoiceActivityGate.markAgentSpeechEnded()
                        onError(event.message)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicLoop() {
        micJob = scope.launch {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(BYTES_PER_20_MS * 4)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
            audioRecord = recorder
            enableAudioEffects(recorder.audioSessionId)
            val frame = ByteArray(BYTES_PER_20_MS)
            recorder.startRecording()
            while (isActive) {
                val read = recorder.read(frame, 0, frame.size)
                if (read > 0) {
                    val chunk = frame.copyOf(read)
                    val amplitude = calculatePcmAmplitude(chunk)
                    _userAmplitude.value = amplitude
                    val likelyAssistantEcho = VoiceActivityGate.shouldSuppressRecognition() &&
                        amplitude < BARGE_IN_AMPLITUDE_THRESHOLD
                    if (!likelyAssistantEcho) {
                        transport.sendAudio(chunk)
                    }
                }
            }
        }
    }

    private fun enableAudioEffects(audioSessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = runCatching {
                AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching {
                NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        }
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = runCatching {
                AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        }
    }

    private fun releaseAudioEffects() {
        runCatching { echoCanceler?.release() }
        runCatching { noiseSuppressor?.release() }
        runCatching { automaticGainControl?.release() }
        echoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null
    }

    private fun startPlayback() {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BYTES_PER_20_MS * 12)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .build()
            .apply { play() }
    }

    private fun playPcm(chunk: ByteArray) {
        VoiceActivityGate.markAgentAudioPlayback()
        _agentAmplitude.value = calculatePcmAmplitude(chunk)
        audioTrack?.write(chunk, 0, chunk.size)
    }

    private fun calculatePcmAmplitude(chunk: ByteArray): Float {
        if (chunk.size < 2) return 0f
        var sum = 0.0
        var count = 0
        var index = 0
        while (index + 1 < chunk.size) {
            val sample = ((chunk[index + 1].toInt() shl 8) or (chunk[index].toInt() and 0xff)).toShort()
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sum += normalized * normalized
            count++
            index += 2
        }
        return sqrt(sum / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val SAMPLE_RATE = 24_000
        private const val BYTES_PER_SAMPLE = 2
        private const val BYTES_PER_20_MS = SAMPLE_RATE / 50 * BYTES_PER_SAMPLE
        private const val BARGE_IN_AMPLITUDE_THRESHOLD = 0.32f
    }
}
