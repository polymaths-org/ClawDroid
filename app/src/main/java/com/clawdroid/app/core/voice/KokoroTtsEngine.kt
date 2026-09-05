package com.clawdroid.app.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Kokoro TTS Engine - High-quality, natural-sounding TTS
 * Uses pre-downloaded Kokoro binary for on-device synthesis
 */
class KokoroTtsEngine(
    private val context: Context,
    private val scope: CoroutineScope
) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Initializing)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val kokoro = KokoroWrapper(context)

    init {
        scope.launch(Dispatchers.IO) {
            try {
                if (kokoro.isAvailable()) {
                    _state.value = TtsEngineState.Ready
                    Log.i("KokoroTtsEngine", "Kokoro initialized successfully")
                } else {
                    Log.w("KokoroTtsEngine", "Kokoro not available, cannot initialize")
                    _state.value = TtsEngineState.Unavailable
                }
            } catch (e: Exception) {
                Log.e("KokoroTtsEngine", "Failed to initialize Kokoro", e)
                _state.value = TtsEngineState.Unavailable
            }
        }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (_state.value != TtsEngineState.Ready) {
            Log.w("KokoroTtsEngine", "Engine not ready, state: ${_state.value}")
            onDone?.invoke()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                _isSpeaking.value = true
                val audioFile = generateAudio(text)
                if (audioFile != null && audioFile.exists()) {
                    playAudio(audioFile, onDone)
                } else {
                    Log.e("KokoroTtsEngine", "Failed to generate audio for text: $text")
                    _isSpeaking.value = false
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                Log.e("KokoroTtsEngine", "Error speaking text", e)
                _isSpeaking.value = false
                onDone?.invoke()
            }
        }
    }

    private suspend fun generateAudio(text: String): File? {
        return try {
            kokoro.generateSpeech(text)
        } catch (e: Exception) {
            Log.e("KokoroTtsEngine", "Audio generation failed", e)
            null
        }
    }

    private fun playAudio(audioFile: File, onDone: (() -> Unit)?) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    onDone?.invoke()
                    try {
                        audioFile.delete()
                    } catch (e: Exception) {
                        Log.w("KokoroTtsEngine", "Failed to delete temp audio file", e)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("KokoroTtsEngine", "MediaPlayer error: $what, $extra")
                    _isSpeaking.value = false
                    onDone?.invoke()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("KokoroTtsEngine", "Failed to play audio", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    override fun stop() {
        _isSpeaking.value = false
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    override fun destroy() {
        stop()
        kokoro.cleanup()
        _state.value = TtsEngineState.Idle
    }
}

/**
 * Internal wrapper for Kokoro binary interactions
 */
private class KokoroWrapper(private val context: Context) {

    private val sandboxDir = File(context.filesDir, "kokoro")

    fun isAvailable(): Boolean {
        val binary = File(sandboxDir, "kokoro")
        return binary.exists() && binary.canExecute()
    }

    suspend fun generateSpeech(text: String): File? {
        if (!isAvailable()) {
            Log.e("KokoroWrapper", "Kokoro binary not available")
            return null
        }

        return try {
            val outputFile = File(context.cacheDir, "kokoro_${UUID.randomUUID()}.wav")
            val processBuilder = ProcessBuilder(
                File(sandboxDir, "kokoro").absolutePath,
                "--text",
                text,
                "--output",
                outputFile.absolutePath,
                "--voice",
                "af_heart"  // Natural female voice
            )
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                Log.i("KokoroWrapper", "Audio generated: ${outputFile.absolutePath}")
                outputFile
            } else {
                Log.e("KokoroWrapper", "Kokoro generation failed with exit code $exitCode")
                outputFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e("KokoroWrapper", "Exception during speech generation", e)
            null
        }
    }

    fun cleanup() {
        try {
            context.cacheDir.listFiles { file ->
                file.name.startsWith("kokoro_") && file.name.endsWith(".wav")
            }?.forEach {
                it.delete()
            }
        } catch (e: Exception) {
            Log.w("KokoroWrapper", "Cleanup failed", e)
        }
    }
}
