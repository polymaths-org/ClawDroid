package com.clawdroid.app.core.voice

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class WhisperCliTranscriber(private val context: Context) {

    suspend fun transcribe(wavFile: File, modelId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(wavFile.exists() && wavFile.length() > 44) { "Recorded audio was empty." }
            val executable = WhisperModelManager.findRuntimeExecutable(context)
                ?: error("Whisper.cpp runtime not installed. Install whisper.cpp in the Linux runtime or place whisper-cli in ${WhisperModelManager.runtimeDirectory(context).absolutePath}.")
            val modelFile = WhisperModelManager.modelFile(context, modelId)
            require(modelFile.exists()) { "Whisper model is not downloaded: ${WhisperModelManager.modelInfo(modelId).label}" }

            val outputPrefix = File(context.cacheDir, "whisper_transcript_${System.currentTimeMillis()}")
            val command = buildCommand(executable, modelFile, wavFile, outputPrefix)
            val env = EnvironmentSetup.build(context)
            val process = ProcessBuilder(command)
                .directory(executable.parentFile ?: context.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(env.values)
                }
                .start()

            val output = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            output.appendLine(line)
                        }
                    }
                }
            }.apply { start() }

            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("Whisper transcription timed out.")
            }
            readerThread.join(1_000)

            val transcriptFile = File("${outputPrefix.absolutePath}.txt")
            val transcript = if (transcriptFile.exists()) {
                transcriptFile.readText()
            } else {
                output.toString()
            }
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("whisper_", ignoreCase = true) }
                .filterNot { it.startsWith("system_info", ignoreCase = true) }
                .joinToString(" ")
                .trim()

            transcriptFile.delete()
            if (process.exitValue() != 0) {
                error("Whisper exited with ${process.exitValue()}: ${output.toString().takeLast(500)}")
            }
            transcript.ifBlank { error("Whisper did not return a transcript.") }
        }
    }

    private fun buildCommand(
        executable: File,
        modelFile: File,
        wavFile: File,
        outputPrefix: File,
    ): List<String> {
        val command = mutableListOf(
            executable.absolutePath,
            "-m", modelFile.absolutePath,
            "-f", wavFile.absolutePath,
            "-otxt",
            "-of", outputPrefix.absolutePath,
            "-nt",
        )
        val language = AppConfigManager.speechRecognitionLanguage
            .takeUnless { it == "auto" }
            ?.substringBefore('-')
            ?.takeIf { it.isNotBlank() }
        if (language != null) {
            command += listOf("-l", language)
        }
        return command
    }
}
