package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val output: String,
)

object CommandTool {
    suspend fun execute(
        context: Context,
        command: String,
        cwd: String?,
        timeoutSeconds: Long = 30,
    ): CommandResult = withContext(Dispatchers.IO) {
        val env = EnvironmentSetup.build(context)
        val workingDirectory = cwd
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?: env.home

        val process = ProcessBuilder(
            File(env.prefix, "bin/bash").absolutePath,
            "--noprofile",
            "--norc",
            "-c",
            command,
        )
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(env.values)
            }
            .start()

        coroutineScope {
            val output = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText().trim() }
            }
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                error("Command timed out after ${timeoutSeconds}s")
            }
            CommandResult(exitCode = process.exitValue(), output = output.await())
        }
    }
}
