package com.clawdroid.app.core.terminal

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

enum class ProcessState {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    WAITING_FOR_INPUT,
    KILLED,
}

data class ManagedProcess(
    val id: String,
    val command: String,
    val cwd: String,
    val process: Process,
    val outputBuffer: OutputBuffer,
    val startedAt: Long = Instant.now().toEpochMilli(),
    val state: AtomicReference<ProcessState> = AtomicReference(ProcessState.RUNNING),
    @Volatile var exitCode: Int? = null,
    @Volatile var prompt: String? = null,
)

data class CommandExecutionResult(
    val exitCode: Int,
    val output: String,
)

data class ProcessStartResult(
    val processId: String,
    val initialOutput: String,
)

data class ProcessStatus(
    val processId: String,
    val command: String,
    val cwd: String,
    val state: ProcessState,
    val exitCode: Int?,
    val recentOutput: String,
    val waitingForInput: Boolean,
    val prompt: String?,
    val startedAt: Long,
)
