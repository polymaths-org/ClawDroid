package com.clawdroid.app.core.memory

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryManager(private val context: Context) {

    private val sandboxDir = context.filesDir
    private val memoryDir: File get() = File(sandboxDir, "home/.memory")
    private val memoryFile: File get() = File(memoryDir, "memory.md")
    private val userFile: File get() = File(memoryDir, "user.md")
    private val changesFile: File get() = File(memoryDir, "CHANGES.md")
    private val appAccessFile: File get() = File(memoryDir, "APP_ACCESS.md")
    private val heartbeatsFile: File get() = File(memoryDir, "heartbeats.md")
    private val skillsDir: File get() = File(sandboxDir, "home/.skills")
    private val skillIndexFile: File get() = File(skillsDir, "INDEX.md")

    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_CONTEXT_CHARS = 4_096
    }

    init {
        memoryDir.mkdirs()
        skillsDir.mkdirs()
        if (!memoryFile.exists()) {
            memoryFile.writeText(buildInitialMemory())
            Log.i(TAG, "Created initial memory.md")
        }
        ensureFile(userFile, buildInitialUserMemory())
        ensureFile(changesFile, "# Changes\n\nDurable notes about what has been built, changed, or debugged.\n")
        ensureFile(appAccessFile, "# App Access\n\nAllow/deny notes for apps, services, and external actions.\n")
        ensureFile(heartbeatsFile, "# Heartbeats\n\nRecurring checks, background tasks, and trigger notes.\n")
        ensureFile(skillIndexFile, buildDefaultSkillIndex())
    }

    fun readMemory(): String {
        return try {
            listOf(
                skillIndexFile.readTextIfExists(),
                memoryFile.readTextIfExists(),
                userFile.readTextIfExists(),
                changesFile.readTextIfExists(),
                appAccessFile.readTextIfExists(),
                heartbeatsFile.readTextIfExists(),
            )
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read memory", e)
            buildInitialMemory()
        }
    }

    fun readSkillIndex(): String = skillIndexFile.readTextIfExists().ifBlank { buildDefaultSkillIndex() }

    fun searchMemory(keyword: String): String {
        val keywords = extractKeywords(keyword)
        if (keywords.isEmpty()) return getRelevantContext(keyword)
        val matches = mutableListOf<String>()
        memoryFiles().forEach { file ->
            val text = file.readTextIfExists()
            if (text.isBlank()) return@forEach
            val lines = text.lines()
            val matchedLines = lines.filter { line ->
                val lower = line.lowercase(Locale.US)
                keywords.any { it in lower }
            }.take(30)
            if (matchedLines.isNotEmpty()) {
                matches += "## ${file.name}\n" + matchedLines.joinToString("\n")
            }
        }
        return matches.joinToString("\n\n").take(MAX_CONTEXT_CHARS)
    }

    fun saveMemory(section: String, content: String) {
        val target = when (section.trim().lowercase(Locale.US)) {
            "user", "preferences", "profile" -> userFile
            "changes", "change", "built" -> changesFile
            "access", "app_access", "apps" -> appAccessFile
            "heartbeats", "heartbeat", "triggers" -> heartbeatsFile
            "skills", "skill_index", "index" -> skillIndexFile
            else -> memoryFile
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val entry = buildString {
            appendLine()
            appendLine("## $timestamp")
            appendLine(content.trim())
        }
        runCatching {
            target.parentFile?.mkdirs()
            target.appendText(entry)
        }.onFailure { Log.w(TAG, "Failed to save memory section=$section", it) }
    }

    fun getRelevantContext(task: String): String {
        val keywords = extractKeywords(task)
        val parts = mutableListOf<String>()
        parts += readSkillIndex()

        val relevantSkillDetails = loadRelevantSkillDetails(keywords)
        if (relevantSkillDetails.isNotBlank()) {
            parts += relevantSkillDetails
        }

        val relevantMemories = searchMemoryLines(keywords)
        if (relevantMemories.isNotBlank()) {
            parts += relevantMemories
        } else {
            parts += memoryFile.readTextIfExists().take(1200)
            parts += userFile.readTextIfExists().take(900)
        }

        return parts
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .take(MAX_CONTEXT_CHARS)
    }

    fun appendSessionSummary(summary: String) {
        try {
            val entry = buildString {
                appendLine()
                appendLine("---")
                appendLine("## Session: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
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

    private fun buildInitialUserMemory(): String = """
# User Memory

Durable preferences, identity details, and workflow notes learned from the user.
""".trimIndent()

    private fun buildDefaultSkillIndex(): String = """
# SKILL INDEX (Always in context)

## Android
- `app_control` - Open apps, tap, type, scroll, read screen.
- `whatsapp_send` - Send WhatsApp messages only after the user provides exact text.
- `phone_calls` - Prepare calls and Android intents with user control.

## Desktop (INTERPOLE)
- `interpole_file_ops` - List/read/write files on the paired desktop.
- `interpole_exec` - Run shell commands on the paired desktop when execution is enabled.
- `interpole_terminal` - Create/interact with TUI sessions through tmux.
- `interpole_window` - Use desktop environment specific window tools when configured.
- `interpole_media` - Use desktop media tools such as playerctl where available.
- `interpole_notify` - Send desktop notifications.
- `interpole_transfer` - Push/pull files between phone and desktop using the INTERPOLE file server.

## Agent
- `memory_read` - Read relevant long-term memory.
- `memory_write` - Save durable facts and session notes.
- `agent_ask` - Proactively ask the user a question.
- `agent_answer` - Mark a proactive question as answered.
- `self_manage` - Manage alarms, reminders, and todos.
""".trimIndent()

    private fun ensureFile(file: File, content: String) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    private fun memoryFiles(): List<File> = listOf(memoryFile, userFile, changesFile, appAccessFile, heartbeatsFile)

    private fun File.readTextIfExists(): String = runCatching {
        if (exists()) readText() else ""
    }.getOrDefault("")

    private fun extractKeywords(text: String): Set<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "that", "this", "from", "into", "your", "you",
            "are", "was", "were", "what", "when", "where", "how", "please", "about",
        )
        return text
            .lowercase(Locale.US)
            .split(Regex("[^a-z0-9_:-]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    private fun loadRelevantSkillDetails(keywords: Set<String>): String {
        if (keywords.isEmpty()) return ""
        val files = skillsDir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && it.extension.lowercase(Locale.US) in setOf("md", "txt") && it != skillIndexFile }
            .filter { file ->
                val name = file.nameWithoutExtension.lowercase(Locale.US)
                keywords.any { key -> key in name || name in key }
            }
            .take(3)
            .toList()
        return files.joinToString("\n\n") { file ->
            "## Skill Detail: ${file.name}\n${file.readTextIfExists().take(1400)}"
        }
    }

    private fun searchMemoryLines(keywords: Set<String>): String {
        if (keywords.isEmpty()) return ""
        val sections = mutableListOf<String>()
        memoryFiles().forEach { file ->
            val matches = file.readTextIfExists()
                .lines()
                .filter { line ->
                    val lower = line.lowercase(Locale.US)
                    keywords.any { it in lower }
                }
                .take(24)
            if (matches.isNotEmpty()) {
                sections += "## Relevant ${file.name}\n${matches.joinToString("\n")}"
            }
        }
        return sections.joinToString("\n\n").take(2600)
    }
}
