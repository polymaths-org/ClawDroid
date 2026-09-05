package com.clawdroid.app.core.terminal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OutputBuffer(
    private val capacity: Int = 500,
) {
    private val mutex = Mutex()
    private val lines = ArrayDeque<String>()
    private val firstLines = mutableListOf<String>()
    private var omittedLines = 0

    suspend fun append(text: String) {
        if (text.isEmpty()) return
        mutex.withLock {
            text.lineSequence().forEach { line ->
                if (firstLines.size < 5) firstLines += line
                lines.addLast(line)
                while (lines.size > capacity) {
                    lines.removeFirst()
                    omittedLines += 1
                }
            }
        }
    }

    suspend fun getForUi(): String = mutex.withLock {
        lines.joinToString("\n")
    }

    suspend fun getRecentLines(count: Int): String = mutex.withLock {
        lines.takeLast(count).joinToString("\n")
    }

    suspend fun getForLlm(): String = mutex.withLock {
        val recent = lines.takeLast(30)
        buildString {
            firstLines.forEach { appendLine(it) }
            val hiddenCount = (omittedLines + lines.size - recent.size - firstLines.size).coerceAtLeast(0)
            if (hiddenCount > 0) appendLine("... omitted $hiddenCount lines ...")
            recent.forEach { appendLine(it) }
        }.trim()
    }
}
