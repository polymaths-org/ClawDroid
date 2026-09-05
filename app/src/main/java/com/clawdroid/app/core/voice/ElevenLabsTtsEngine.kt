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

class ElevenLabsTtsEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Idle)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    private val apiKey: String
        get() = AppConfigManager.elevenlabsApiKey

    private val currentVoice: String
        get() {
            val stored = AppConfigManager.ttsVoice
            if (stored.isNotBlank() && stored != "alloy" && stored != "onyx") return stored
            return "21m00Tcm4TlvDq8ikWAM"
        }

    companion object {
        private const val TAG = "ElevenLabsTtsEngine"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"

        val PRESET_VOICES = listOf(
            "21m00Tcm4TlvDq8ikWAM" to "Rachel (Female)",
            "AZnzlk1XvdvUeBnXmlld" to "Domi (Female)",
            "EXAVITQu4vrVxnwl2K6Ez" to "Bella (Female)",
            "ErXwobaYiN019PkySvjV" to "Antoni (Male)",
            "MF3mGyEYCl7XYWbV9V6O" to "Elli (Female)",
            "TxGEqnHWrfWFTfGW9XjX" to "Josh (Male)",
            "VR6AewLTigWG4xSOGBAt" to "Arnold (Male)",
            "ODq5zmih8GrVes37Dizd" to "Patrick (Male)",
            "XUq2iLhUeRMkYQP5CJTU" to "Emily (Female)",
            "N2lVS1w4EtoT3dr4eOWO" to "Callum (Male)",
        )
    }

    init {
        _state.value = if (apiKey.isNotBlank()) TtsEngineState.Ready else TtsEngineState.Unavailable
        if (_state.value == TtsEngineState.Unavailable) {
            Log.w(TAG, "No ElevenLabs API key configured")
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
        val outputFile = File(context.cacheDir, "elevenlabs_${UUID.randomUUID()}.mp3")
        try {
            val success = synthesize(text, outputFile)
            if (success && outputFile.exists() && outputFile.length() > 100) {
                withContext(Dispatchers.Main) {
                    playAudio(outputFile, onDone)
                }
            } else {
                _isSpeaking.value = false
                onDone?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "ElevenLabs speak failed", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    private fun synthesize(text: String, outputFile: File): Boolean {
        val voiceId = currentVoice
        val payload = JSONObject()
            .put("text", text)
            .put("model_id", "eleven_monolingual_v1")
            .put("voice_settings", JSONObject()
                .put("stability", 0.5)
                .put("similarity_boost", 0.75)
                .put("style", 0.0)
            )

        val connection = (URL("$BASE_URL/text-to-speech/$voiceId").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("xi-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg")
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
                Log.w(TAG, "ElevenLabs HTTP $responseCode: $errorBody")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "ElevenLabs HTTP failed", e)
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
