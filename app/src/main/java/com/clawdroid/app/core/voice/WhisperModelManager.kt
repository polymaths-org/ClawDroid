package com.clawdroid.app.core.voice

import android.content.Context
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

data class WhisperModelInfo(
    val id: String,
    val label: String,
    val fileName: String,
    val downloadUrl: String,
    val approximateSize: String,
)

object WhisperModelManager {
    val models = listOf(
        WhisperModelInfo(
            id = "lite",
            label = "Lite",
            fileName = "ggml-tiny.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            approximateSize = "75 MB",
        ),
        WhisperModelInfo(
            id = "base",
            label = "Base",
            fileName = "ggml-base.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            approximateSize = "142 MB",
        ),
        WhisperModelInfo(
            id = "small",
            label = "Small",
            fileName = "ggml-small.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            approximateSize = "466 MB",
        ),
    )

    fun modelInfo(id: String): WhisperModelInfo = models.firstOrNull { it.id == id } ?: models.first()

    fun modelFile(context: Context, id: String): File {
        val dir = File(context.filesDir, "voice/whisper").apply { mkdirs() }
        return File(dir, modelInfo(id).fileName)
    }

    fun runtimeDirectory(context: Context): File {
        return File(context.filesDir, "voice/whisper/runtime").apply { mkdirs() }
    }

    fun findRuntimeExecutable(context: Context): File? {
        val prefix = File(context.filesDir, "usr")
        val runtime = runtimeDirectory(context)
        return listOf(
            File(runtime, "whisper-cli"),
            File(runtime, "main"),
            File(runtime, "whisper.cpp"),
            File(context.filesDir, "whisper/whisper-cli"),
            File(context.filesDir, "whisper/main"),
            File(prefix, "bin/whisper-cli"),
            File(prefix, "bin/whisper.cpp"),
            File(prefix, "bin/main"),
        ).firstOrNull { it.exists() && it.canExecute() }
    }

    fun runtimeStatus(context: Context): String {
        val executable = findRuntimeExecutable(context)
        return if (executable != null) {
            "Runtime installed: ${executable.absolutePath}"
        } else {
            "Runtime missing. Install whisper.cpp in the Linux runtime or place whisper-cli in ${runtimeDirectory(context).absolutePath}."
        }
    }

    fun isDownloaded(context: Context, id: String): Boolean {
        val file = modelFile(context, id)
        return file.exists() && file.length() > 1024 * 1024
    }

    suspend fun download(
        context: Context,
        id: String,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val info = modelInfo(id)
            val target = modelFile(context, id)
            if (isDownloaded(context, id)) return@runCatching target
            val temp = File(target.parentFile, "${target.name}.part")
            val connection = URL(info.downloadUrl).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            val total = connection.contentLengthLong.takeIf { it > 0L }
            var written = 0L
            connection.getInputStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total != null) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not move downloaded Whisper model into place." }
            onProgress(1f)
            target
        }
    }

    suspend fun installRuntime(
        context: Context,
        onProgress: (String) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            findRuntimeExecutable(context)?.let { return@runCatching it }
            onProgress("Preparing Linux runtime...")
            BootstrapManager.ensureBootstrapped(context) { progress ->
                onProgress("${progress.stage}: ${progress.detail}")
            }
            val env = EnvironmentSetup.build(context)
            onProgress("Installing whisper.cpp...")
            val sourceDir = File(context.filesDir, "voice/whisper/src")
            val buildDir = File(context.filesDir, "voice/whisper/build")
            val runtimeDir = runtimeDirectory(context)
            val installScript = """
                set -e
                apt-get update
                if apt-get install -y whisper.cpp || apt-get install -y whisper-cpp || apt-get install -y whisper; then
                  exit 0
                fi
                apt-get install -y git cmake make clang
                rm -rf ${shellQuote(sourceDir.absolutePath)} ${shellQuote(buildDir.absolutePath)}
                git clone --depth 1 https://github.com/ggml-org/whisper.cpp ${shellQuote(sourceDir.absolutePath)}
                cmake -S ${shellQuote(sourceDir.absolutePath)} -B ${shellQuote(buildDir.absolutePath)} -DCMAKE_BUILD_TYPE=Release -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=ON -DGGML_OPENMP=OFF
                cmake --build ${shellQuote(buildDir.absolutePath)} --config Release -j2
                mkdir -p ${shellQuote(runtimeDir.absolutePath)}
                candidate=${'$'}(find ${shellQuote(buildDir.absolutePath)} -type f \( -name whisper-cli -o -name main \) | head -n 1)
                test -n "${'$'}candidate"
                cp "${'$'}candidate" ${shellQuote(File(runtimeDir, "whisper-cli").absolutePath)}
                chmod 755 ${shellQuote(File(runtimeDir, "whisper-cli").absolutePath)}
            """.trimIndent()
            val command = listOf(
                File(env.prefix, "bin/bash").absolutePath,
                "--noprofile",
                "--norc",
                "-c",
                installScript,
            )
            val process = ProcessBuilder(command)
                .directory(env.home)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment().putAll(env.values)
                }
                .start()

            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            output.appendLine(line)
                            onProgress(line.take(160))
                        }
                    }
                }
            }.apply { start() }
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                error("Timed out installing whisper.cpp.")
            }
            reader.join(1_000)
            if (process.exitValue() != 0) {
                error("Could not install whisper.cpp: ${output.toString().takeLast(800)}")
            }
            findRuntimeExecutable(context)
                ?: error("whisper.cpp installed, but no whisper-cli executable was found.")
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
