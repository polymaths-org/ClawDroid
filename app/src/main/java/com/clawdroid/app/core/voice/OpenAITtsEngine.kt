package com.clawdroid.app.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class OpenAITtsEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Idle)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    private val apiKey: String
        get() = AppConfigManager.openaiTtsApiKey
            .takeIf { it.isNotBlank() }
            ?: AppConfigManager.apiKey

    private val currentVoice: String
        get() = AppConfigManager.ttsVoice.takeIf { it.isNotBlank() } ?: "alloy"

    companion object {
        private const val TAG = "OpenAITtsEngine"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }

    init {
        _state.value = if (apiKey.isNotBlank()) TtsEngineState.Ready else TtsEngineState.Unavailable
        if (_state.value == TtsEngineState.Unavailable) {
            Log.w(TAG, "No API key available for OpenAI TTS")
        }
    }

    override fun speak(text: String, onDone: (() -> Unit)?) {
        if (_state.value != TtsEngineState.Ready) {
            onDone?.invoke()
            return
        }
        scope.launch(Dispatchers.IO) {
            doSpeak(text, onDone)
        }
    }

    private suspend fun doSpeak(text: String, onDone: (() -> Unit)?) {
        _isSpeaking.value = true
        val outputFile = File(context.cacheDir, "openai_tts_${UUID.randomUUID()}.mp3")
        try {
            val success = synthesize(text, outputFile)
            if (success && outputFile.exists() && outputFile.length() > 100) {
                withContext(Dispatchers.Main) {
                    playAudio(outputFile, onDone)
                }
            } else {
                Log.w(TAG, "Synthesis failed or empty output")
                _isSpeaking.value = false
                onDone?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenAI TTS speak failed", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    private fun synthesize(text: String, outputFile: File): Boolean {
        val voice = currentVoice
        val payload = JSONObject()
            .put("model", "tts-1")
            .put("voice", voice)
            .put("input", text)
            .put("response_format", "mp3")

        val connection = (URL("$DEFAULT_BASE_URL/audio/speech").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                        }
                    }
                }
                return true
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "OpenAI TTS HTTP $responseCode: $errorBody")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenAI TTS HTTP failed", e)
            return false
        } finally {
            connection.disconnect()
        }
    }

    private fun playAudio(file: File, onDone: (() -> Unit)?) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener {
                _isSpeaking.value = false
                file.delete()
                onDone?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                _isSpeaking.value = false
                file.delete()
                onDone?.invoke()
                true
            }
            runCatching {
                setDataSource(file.absolutePath)
                prepareAsync()
            }.onFailure {
                _isSpeaking.value = false
                file.delete()
                onDone?.invoke()
            }
        }
    }

    override fun stop() {
        _isSpeaking.value = false
        mediaPlayer?.apply {
            runCatching { stop(); release() }
            mediaPlayer = null
        }
    }

    override fun destroy() {
        stop()
        _state.value = TtsEngineState.Idle
    }
}
