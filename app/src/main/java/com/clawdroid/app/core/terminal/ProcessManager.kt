package com.clawdroid.app.core.terminal

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ProcessManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    suspend fun executeCommand(
        command: String,
        cwd: String? = null,
        timeout: Duration = 30.seconds,
    ): CommandExecutionResult = withContext(Dispatchers.IO) {
        val managed = startManagedProcess(command, cwd)
        val completed = withTimeoutOrNull(timeout) {
            managed.process.waitFor()
        }

        if (completed == null) {
            managed.state.set(ProcessState.TIMED_OUT)
            managed.process.destroyForcibly()
            managed.process.waitFor()
            error("Command timed out after ${timeout.inWholeSeconds}s")
        }

        managed.exitCode = completed
        managed.state.compareAndSet(ProcessState.RUNNING, if (completed == 0) ProcessState.COMPLETED else ProcessState.FAILED)
        CommandExecutionResult(
            exitCode = completed,
            output = managed.outputBuffer.getForLlm(),
        )
    }

    suspend fun startProcess(
        command: String,
        cwd: String? = null,
        timeout: Duration = 5.minutesBounded(),
    ): ProcessStartResult {
        val managed = startManagedProcess(command, cwd)
        scope.launch {
            delay(timeout)
            if (managed.state.get() == ProcessState.RUNNING || managed.state.get() == ProcessState.WAITING_FOR_INPUT) {
                managed.state.set(ProcessState.TIMED_OUT)
                managed.process.destroyForcibly()
            }
        }
        delay(2_500)
        return ProcessStartResult(
            processId = managed.id,
            initialOutput = managed.outputBuffer.getForLlm(),
        )
    }

    suspend fun checkProcess(processId: String): ProcessStatus {
        val managed = processes[processId] ?: error("Unknown process id: $processId")
        val state = managed.state.get()
        return ProcessStatus(
            processId = managed.id,
            command = managed.command,
            cwd = managed.cwd,
            state = state,
            exitCode = managed.exitCode,
            recentOutput = managed.outputBuffer.getForLlm(),
            waitingForInput = state == ProcessState.WAITING_FOR_INPUT,
            prompt = managed.prompt,
            startedAt = managed.startedAt,
        )
    }

    suspend fun sendInput(processId: String, input: String): ProcessStatus = withContext(Dispatchers.IO) {
        val managed = processes[processId] ?: error("Unknown process id: $processId")
        managed.process.outputStream.write(InputTranslator.translate(input))
        managed.process.outputStream.flush()
        managed.state.compareAndSet(ProcessState.WAITING_FOR_INPUT, ProcessState.RUNNING)
        delay(1_000)
        checkProcess(processId)
    }

    suspend fun killProcess(processId: String): ProcessStatus = withContext(Dispatchers.IO) {
        val managed = processes[processId] ?: error("Unknown process id: $processId")
        managed.state.set(ProcessState.KILLED)
        managed.process.destroyForcibly()
        delay(250)
        checkProcess(processId)
    }

    suspend fun listProcesses(): List<ProcessStatus> = processes.keys.sorted().map { checkProcess(it) }

    suspend fun killAll() {
        processes.keys.forEach { killProcess(it) }
    }

    private fun startManagedProcess(command: String, cwd: String?): ManagedProcess {
        val env = EnvironmentSetup.build(context)
        val workingDirectory = cwd?.takeIf { it.isNotBlank() }?.let(::File) ?: env.home
        check(workingDirectory.exists() || workingDirectory.mkdirs()) {
            "Unable to create working directory ${workingDirectory.absolutePath}"
        }

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

        val managed = ManagedProcess(
            id = UUID.randomUUID().toString(),
            command = command,
            cwd = workingDirectory.absolutePath,
            process = process,
            outputBuffer = OutputBuffer(),
        )
        processes[managed.id] = managed
        observe(managed)
        return managed
    }

    private fun observe(managed: ManagedProcess) {
        scope.launch {
            val readBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = runCatching { managed.process.inputStream.read(readBuffer) }.getOrDefault(-1)
                if (read <= 0) break
                val text = AnsiStripper.strip(String(readBuffer, 0, read))
                managed.outputBuffer.append(text)
                detectPrompt(managed, text)
            }
        }
        scope.launch {
            val exitCode = managed.process.waitFor()
            managed.exitCode = exitCode
            if (managed.state.get() !in setOf(ProcessState.KILLED, ProcessState.TIMED_OUT)) {
                managed.state.set(if (exitCode == 0) ProcessState.COMPLETED else ProcessState.FAILED)
            }
        }
    }

    private fun detectPrompt(managed: ManagedProcess, text: String) {
        val prompt = promptPatterns.firstOrNull { it.containsMatchIn(text) }?.pattern
        if (prompt != null && managed.state.get() == ProcessState.RUNNING) {
            managed.prompt = prompt
            managed.state.set(ProcessState.WAITING_FOR_INPUT)
        }
    }

    private fun Int.minutesBounded(): Duration = this.coerceAtMost(180).seconds * 60

    private companion object {
        val promptPatterns = listOf(
            Regex("\\[[Yy]/[Nn]\\]"),
            Regex("\\[[Nn]/[Yy]\\]"),
            Regex("password:", RegexOption.IGNORE_CASE),
            Regex("(^|\\n)>>> ?$"),
            Regex("\\? .+›"),
        )
    }
}
