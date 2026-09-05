package com.clawdroid.app.core.bootstrap

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class BootstrapDiagnostics(
    val filesDir: String,
    val prefixDir: String,
    val homeDir: String,
    val projectsDir: String,
    val memoryDir: String,
    val tmpDir: String,
    val commandOutput: String,
)

object BootstrapDiagnosticsRunner {
    suspend fun run(context: Context): BootstrapDiagnostics = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir
        val prefixDir = File(filesDir, "usr")
        val homeDir = File(filesDir, "home")
        val projectsDir = File(homeDir, "projects")
        val memoryDir = File(homeDir, ".memory")
        val tmpDir = File(filesDir, "tmp")

        listOf(prefixDir, homeDir, projectsDir, memoryDir, tmpDir).forEach { dir ->
            check(dir.exists() || dir.mkdirs()) {
                "Unable to create ${dir.absolutePath}"
            }
        }

        BootstrapDiagnostics(
            filesDir = filesDir.absolutePath,
            prefixDir = prefixDir.absolutePath,
            homeDir = homeDir.absolutePath,
            projectsDir = projectsDir.absolutePath,
            memoryDir = memoryDir.absolutePath,
            tmpDir = tmpDir.absolutePath,
            commandOutput = runProbeCommand(filesDir),
        )
    }

    private fun runProbeCommand(filesDir: File): String {
        val process = ProcessBuilder(
            "/system/bin/sh",
            "-c",
            "id; echo ABI=$(getprop ro.product.cpu.abi); echo SDK=$(getprop ro.build.version.sdk); echo FILES=\$PWD",
        )
            .directory(filesDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val completed = process.waitFor(5, TimeUnit.SECONDS)
        return when {
            !completed -> {
                process.destroyForcibly()
                "Probe command timed out"
            }

            process.exitValue() == 0 -> output
            else -> "Probe exited ${process.exitValue()}:\n$output"
        }
    }
}
