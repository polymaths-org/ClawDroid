package com.clawdroid.app.core.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
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
import java.util.concurrent.TimeUnit

class PiperEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : TtsEngine {

    private val _state = MutableStateFlow(TtsEngineState.Idle)
    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    val isDownloading: Boolean get() = _downloadProgress.value in 0f..0.99f

    private val sandboxDir = context.filesDir
    private val piperDir = File(sandboxDir, "piper")
    private val piperBinary = File(piperDir, "piper")
    private val voicesDir = File(piperDir, "voices")
    private val voiceModel: File get() = File(voicesDir, "en_US-ryan-medium.onnx")
    private val voiceConfig: File get() = File(voicesDir, "en_US-ryan-medium.onnx.json")

    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "PiperEngine"
        private const val PIPER_BINARY_URL =
            "https://github.com/rhasspy/piper/releases/latest/download/piper_linux_aarch64.tar.gz"
        private const val VOICE_MODEL_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/ryan/medium/en_US-ryan-medium.onnx"
        private const val VOICE_CONFIG_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/ryan/medium/en_US-ryan-medium.onnx.json"

        private const val PIPER_INSTALL_URL =
            "https://github.com/rhasspy/piper/releases/latest/download/piper_linux_aarch64.tar.gz"
    }

    val isInstalled: Boolean get() = piperBinary.exists() && voiceModel.exists()

    /**
     * Initializes Piper. If already installed, transitions to Ready immediately.
     * Otherwise downloads the binary and voice model.
     */
    fun startDownload() {
        if (_state.value == TtsEngineState.Initializing) return
        if (isInstalled) {
            _state.value = TtsEngineState.Ready
            _downloadProgress.value = 1f
            return
        }
        _state.value = TtsEngineState.Initializing
        _downloadProgress.value = 0f
        scope.launch(Dispatchers.IO) {
            setup()
        }
    }

    private suspend fun setup() {
        try {
            piperDir.mkdirs()
            voicesDir.mkdirs()
            File(sandboxDir, "tmp").mkdirs()

            if (!piperBinary.exists()) {
                Log.i(TAG, "Downloading Piper binary...")
                downloadAndExtractPiper()
            }

            if (!voiceModel.exists()) {
                Log.i(TAG, "Downloading voice model (Ryan - male US)...")
                downloadFile(VOICE_MODEL_URL, voiceModel, progressStart = 0.5f, progressEnd = 0.9f)
                downloadFile(VOICE_CONFIG_URL, voiceConfig, progressStart = 0.9f, progressEnd = 0.95f)
            }

            _downloadProgress.value = 0.95f
            val works = verifyPiper()
            _downloadProgress.value = 1f
            _state.value = if (works) TtsEngineState.Ready else TtsEngineState.Unavailable
            Log.i(TAG, "Piper state: ${_state.value}")
        } catch (e: Exception) {
            Log.w(TAG, "Piper setup failed", e)
            _state.value = TtsEngineState.Unavailable
            _downloadProgress.value = 0f
        }
    }

    private fun downloadAndExtractPiper() {
        val url = URL(PIPER_BINARY_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true

        val tarFile = File(piperDir, "piper.tar.gz")
        val contentLength = connection.contentLength
        var bytesRead = 0L
        connection.inputStream.use { input ->
            FileOutputStream(tarFile).use { output ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        _downloadProgress.value = (bytesRead.toFloat() / contentLength) * 0.5f
                    }
                }
            }
        }

        // Extract binary from tar.gz
        val process = ProcessBuilder(
            "tar", "xzf", tarFile.absolutePath, "-C", piperDir.absolutePath
        ).start()
        process.waitFor(30, TimeUnit.SECONDS)

        val extractedBinary = File(piperDir, "piper")
        if (extractedBinary.exists()) {
            extractedBinary.setExecutable(true)
        }
        tarFile.delete()
    }

    private suspend fun downloadFile(urlString: String, destination: File, progressStart: Float = 0.5f, progressEnd: Float = 1.0f) {
        withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true

            val contentLength = connection.contentLength
            var bytesRead = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            val range = progressEnd - progressStart
                            _downloadProgress.value = progressStart + (bytesRead.toFloat() / contentLength) * range
                        }
                    }
                }
            }
        }
    }

    private fun verifyPiper(): Boolean {
        if (!piperBinary.exists() || !voiceModel.exists()) return false
        return try {
            val process = ProcessBuilder(
                piperBinary.absolutePath, "--help"
            )
                .redirectErrorStream(true)
                .start()
            process.waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Piper verification failed", e)
            false
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
        val inputFile = File(sandboxDir, "tmp/piper_input.txt")
        val outputFile = File(sandboxDir, "tmp/piper_output.wav")

        try {
            inputFile.writeText(text)

            val env = EnvironmentSetup.build(context)

            val pb = ProcessBuilder(
                piperBinary.absolutePath,
                "--model", voiceModel.absolutePath,
                "--output-file", outputFile.absolutePath,
            )
            pb.directory(piperDir)
            pb.environment().clear()
            pb.environment().putAll(env.values)
            pb.environment()["PIPER_HOME"] = piperDir.absolutePath
            pb.redirectInput(ProcessBuilder.Redirect.from(inputFile))
            val process = pb.start()

            val finished = process.waitFor(120, TimeUnit.SECONDS)
            val exitCode = if (finished) process.exitValue() else -1

            if (finished && exitCode == 0 && outputFile.exists() && outputFile.length() > 1000) {
                withContext(Dispatchers.Main) {
                    playAudio(outputFile, onDone)
                }
            } else {
                Log.w(TAG, "Piper failed: exit=$exitCode size=${outputFile.length()}")
                _isSpeaking.value = false
                onDone?.invoke()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Piper speak failed", e)
            _isSpeaking.value = false
            onDone?.invoke()
        } finally {
            inputFile.delete()
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
            runCatching {
                stop()
                release()
            }
            mediaPlayer = null
        }
    }

    override fun destroy() {
        stop()
        _state.value = TtsEngineState.Idle
    }
}
