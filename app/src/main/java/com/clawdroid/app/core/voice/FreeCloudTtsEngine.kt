package com.clawdroid.app.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

class FreeCloudTtsEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Ready)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var currentQueue = mutableListOf<File>()
    private var queueIndex = 0

    companion object {
        private const val TAG = "FreeCloudTts"
        private const val MAX_CHARS = 180
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
        try {
            val segments = splitText(text, MAX_CHARS)
            currentQueue.clear()
            queueIndex = 0

            for ((i, segment) in segments.withIndex()) {
                val outputFile = File(context.cacheDir, "fcloud_${UUID.randomUUID()}.mp3")
                val success = fetchAudio(segment, outputFile)
                if (success && outputFile.exists() && outputFile.length() > 200) {
                    currentQueue.add(outputFile)
                } else {
                    Log.w(TAG, "Segment $i fetch failed: ${segment.take(40)}")
                }
            }

            if (currentQueue.isEmpty()) {
                _isSpeaking.value = false
                onDone?.invoke()
                return
            }

            withContext(Dispatchers.Main) {
                playNext(onDone)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FreeCloudTTS speak failed", e)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    private fun playNext(onDone: (() -> Unit)?) {
        if (queueIndex >= currentQueue.size) {
            _isSpeaking.value = false
            currentQueue.forEach { it.delete() }
            currentQueue.clear()
            onDone?.invoke()
            return
        }

        val file = currentQueue[queueIndex]
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener {
                queueIndex++
                file.delete()
                playNext(onDone)
            }
            setOnErrorListener { _, _, _ ->
                Log.w(TAG, "MediaPlayer error on segment $queueIndex")
                queueIndex++
                file.delete()
                playNext(onDone)
                true
            }
            runCatching {
                setDataSource(file.absolutePath)
                prepareAsync()
            }.onFailure {
                Log.w(TAG, "MediaPlayer prepare failed", it)
                queueIndex++
                file.delete()
                playNext(onDone)
            }
        }
    }

    private fun fetchAudio(text: String, outputFile: File): Boolean {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://translate.google.com/translate_tts?ie=UTF-8&q=$encoded&tl=en&client=tw-ob")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.setRequestProperty("Referer", "https://translate.google.com/")

            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buf = ByteArray(4096)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                        }
                    }
                }
                true
            } else {
                Log.w(TAG, "HTTP ${conn.responseCode} for text: ${text.take(40)}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchAudio failed", e)
            false
        }
    }

    internal fun splitText(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val segments = mutableListOf<String>()
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val current = StringBuilder()

        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length > maxLen) {
                segments.add(current.toString().trim())
                current.clear()
            }
            if (sentence.length > maxLen) {
                if (current.isNotEmpty()) {
                    segments.add(current.toString().trim())
                    current.clear()
                }
                sentence.split(Regex("(?<=[,;:])\\s+")).forEach { chunk ->
                    if (current.isNotEmpty() && current.length + chunk.length > maxLen) {
                        segments.add(current.toString().trim())
                        current.clear()
                    }
                    if (chunk.length > maxLen) {
                        if (current.isNotEmpty()) {
                            segments.add(current.toString().trim())
                            current.clear()
                        }
                        chunk.chunked(maxLen).forEach { segments.add(it) }
                    } else {
                        if (current.isNotEmpty()) current.append(" ")
                        current.append(chunk)
                    }
                }
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) segments.add(current.toString().trim())
        return segments
    }

    override fun stop() {
        _isSpeaking.value = false
        mediaPlayer?.apply {
            runCatching { stop(); release() }
            mediaPlayer = null
        }
        currentQueue.forEach { it.delete() }
        currentQueue.clear()
        queueIndex = 0
    }

    override fun destroy() {
        stop()
        _state.value = TtsEngineState.Idle
    }
}
