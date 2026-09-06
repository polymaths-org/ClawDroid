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

    data class VoiceRef(val name: String, val id: String)

    private val apiKey: String
        get() = AppConfigManager.elevenlabsApiKey

    private val storedVoice: String
        get() {
            val stored = AppConfigManager.ttsVoice
            if (stored.isNotBlank() && stored != "alloy" && stored != "onyx") return stored
            return DEFAULT_VOICE_NAME
        }

    companion object {
        private const val TAG = "ElevenLabsTtsEngine"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"
        private const val MODEL_ID = "eleven_multilingual_v2"
        private const val DEFAULT_VOICE_NAME = "Sarah"
        private const val VOICES_CACHE_TTL_MS = 24L * 60 * 60 * 1000

        val PRESET_VOICES = listOf(
            "Sarah" to "EXAVITQu4vr4xnSDxMaL",
            "Roger" to "CwhRBWXzGAHq8TQ4Fs17",
            "Laura" to "FGY2WhTYpPnrIDTdsKH5",
            "Charlie" to "IKne3meq5aSn9XLyUdCD",
            "George" to "JBFqnCBsd6RMkjVDRZzb",
            "Callum" to "N2lVS1w4EtoT3dr4eOWO",
            "River" to "SAz9YHcvj6GT2YYXdXww",
            "Harry" to "SOYHLrjzK2X1ezoPC6cr",
            "Liam" to "TX3LPaxmHKxFdv7VOQHJ",
            "Alice" to "Xb7hH8MSUJpSbSDYk0k2",
            "Matilda" to "XrExE9yKIg1WjnnlVkGX",
            "Will" to "bIHbv24MWmeRgasZH58o",
            "Jessica" to "cgSgspJ2msm6clMCkdW9",
            "Eric" to "cjVigY5qzO86Huf0OWal",
            "Bella" to "hpp4J3VqNfWAUOO0d1Us",
            "Chris" to "iP95p4xoKVk53GoZ742B",
            "Brian" to "nPczCjzI2devNBz1zQrb",
            "Daniel" to "onwK4e9ZLuTAKqWW03F9",
            "Lily" to "pFZP5JQG7iQjIQuC4Bku",
            "Adam" to "pNInz6obpgDQGcFmaJgB",
            "Bill" to "pqHfZKP75CvOlQylNhV4",
        )

        private fun shortName(name: String): String =
            name.substringBefore(" -").substringBefore(" (").trim()

        /**
         * Pure voice matcher (no Android/network): raw IDs pass through,
         * names match case-insensitively against "Name - descriptor" library
         * entries, then fall back to prefix matching.
         */
        fun matchVoiceId(query: String, voices: List<VoiceRef>): String? {
            val q = query.trim()
            if (q.isEmpty()) return null
            voices.firstOrNull { it.id == q }?.let { return it.id }
            voices.firstOrNull { shortName(it.name).equals(q, ignoreCase = true) }?.let { return it.id }
            voices.firstOrNull { shortName(it.name).startsWith(q, ignoreCase = true) }?.let { return it.id }
            return null
        }
    }

    private var cachedVoices: List<VoiceRef>? = null
    private var cachedVoicesAt: Long = 0L

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

    /**
     * Resolves the stored voice setting to a live voice ID. The settings UI
     * accepts names ("Sarah"), so resolve against the account's voice list
     * (cached in memory) with the preset map as fallback. Never returns blank.
     */
    private fun resolveVoiceId(): String {
        val query = storedVoice
        val live = liveVoices()
        val candidates = live + PRESET_VOICES.map { VoiceRef(it.first, it.second) }
        matchVoiceId(query, candidates)?.let { return it }
        live.firstOrNull()?.let { return it.id }
        // Last resort: send the raw value so the server error names the problem.
        return query.ifBlank { DEFAULT_VOICE_NAME }
    }

    private fun liveVoices(): List<VoiceRef> {
        val now = System.currentTimeMillis()
        cachedVoices?.let { if (now - cachedVoicesAt < VOICES_CACHE_TTL_MS) return it }
        val fetched = fetchVoices()
        if (fetched != null) {
            cachedVoices = fetched
            cachedVoicesAt = now
            return fetched
        }
        return cachedVoices.orEmpty()
    }

    private fun fetchVoices(): List<VoiceRef>? {
        if (apiKey.isBlank()) return null
        val connection = (URL("$BASE_URL/voices").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("xi-api-key", apiKey)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val voices = JSONObject(body).optJSONArray("voices") ?: return null
            (0 until voices.length()).mapNotNull { i ->
                val v = voices.optJSONObject(i) ?: return@mapNotNull null
                val name = v.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = v.optString("voice_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VoiceRef(name, id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ElevenLabs voices fetch failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun synthesize(text: String, outputFile: File): Boolean {
        val voiceId = resolveVoiceId()
        val payload = JSONObject()
            .put("text", text)
            .put("model_id", MODEL_ID)
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
                if (responseCode == 401) {
                    // Invalid key or disabled account: remember so the voice
                    // manager falls back to Android TTS instead of failing
                    // every utterance until settings change.
                    AppConfigManager.elevenlabsAuthInvalid = true
                }
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
