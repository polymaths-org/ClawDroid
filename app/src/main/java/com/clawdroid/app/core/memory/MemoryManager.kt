package com.clawdroid.app.core.memory

import android.content.Context
import android.util.Log
import java.io.File

class MemoryManager(private val context: Context) {

    private val sandboxDir = context.filesDir
    private val memoryDir: File get() = File(sandboxDir, "home/.memory")
    private val memoryFile: File get() = File(memoryDir, "memory.md")

    companion object {
        private const val TAG = "MemoryManager"
    }

    init {
        memoryDir.mkdirs()
        if (!memoryFile.exists()) {
            memoryFile.writeText(buildInitialMemory())
            Log.i(TAG, "Created initial memory.md")
        }
    }

    fun readMemory(): String {
        return try {
            if (memoryFile.exists()) memoryFile.readText() else buildInitialMemory()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read memory", e)
            buildInitialMemory()
        }
    }

    fun appendSessionSummary(summary: String) {
        try {
            val entry = buildString {
                appendLine()
                appendLine("---")
                appendLine("## Session: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}")
                appendLine(summary.trim())
            }
            memoryFile.appendText(entry)
            Log.i(TAG, "Session summary appended to memory")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to append session summary", e)
        }
    }

    fun addFact(key: String, value: String) {
        try {
            val lines = memoryFile.readLines().toMutableList()
            val factLine = "- **$key:** $value"
            val factIndex = lines.indexOfFirst { it.startsWith("- **$key:") }
            if (factIndex >= 0) {
                lines[factIndex] = factLine
            } else {
                val factsIndex = lines.indexOfFirst { it.trim() == "## Known Facts" }
                if (factsIndex >= 0 && factsIndex + 1 < lines.size) {
                    lines.add(factsIndex + 1, factLine)
                } else {
                    lines.add("")
                    lines.add("## Known Facts")
                    lines.add(factLine)
                }
            }
            memoryFile.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add fact", e)
        }
    }

    private fun buildInitialMemory(): String = """
# ClawDroid Agent Memory

Persistent memory for the agent. Facts and session summaries are stored here.

## Known Facts

- **Agent Name:** ${com.clawdroid.app.core.config.AppConfigManager.agentName}
- **Personality:** ${com.clawdroid.app.core.config.AppConfigManager.agentPersonality}
- **Purpose:** ${com.clawdroid.app.core.config.AppConfigManager.agentPurpose}
- **Platform:** Android (Linux sandbox)
""".trimIndent()
}
