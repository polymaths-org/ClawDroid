package com.clawdroid.app.core.memory

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Mini context (mini.md): a pocket-sized identity + context card for
 * small-context on-device models.
 *
 * Cloud models get the full memory files, prompt files, and history. Local
 * phone models (0.6B–4B, 1–2K context window) cannot afford any of that, so
 * the agent injects this one tiny file instead: who the agent is, who the
 * owner is, and the 2–3 rules that matter most.
 *
 * The file lives at `home/.memory/mini.md`, is generated during setup, and
 * can be regenerated / resized / deleted from Agent settings. [MiniContext]
 * holds the pure build/trim logic so unit tests can drive it without Android.
 */
object MiniContext {
    const val FILE_NAME = "mini.md"
    const val DEFAULT_MAX_LINES = 12
    const val MIN_LINES = 4
    const val MAX_LINES = 40
    /** Defensive cap so a hand-edited file can never blow the local prompt budget. */
    const val MAX_INJECT_CHARS = 1_500

    fun coerceMaxLines(value: Int): Int = value.coerceIn(MIN_LINES, MAX_LINES)

    /**
     * Builds the compact identity card. Every line is self-contained so
     * [trimToLines] can cut anywhere without breaking meaning.
     */
    fun buildContent(
        agentName: String,
        personality: String,
        purpose: String,
        ownerName: String,
        ownerInfo: String,
    ): String {
        val lines = mutableListOf("# Mini Context")
        val agentLine = buildString {
            append("Agent ${agentName.trim().ifBlank { "Nova" }}")
            val p = personality.trim()
            if (p.isNotEmpty()) append(" — $p")
            val pu = purpose.trim()
            if (pu.isNotEmpty()) append(". Purpose: $pu")
        }
        lines += agentLine
        val owner = ownerName.trim()
        val info = ownerInfo.trim().replace(Regex("\\s+"), " ")
        if (owner.isNotEmpty() || info.isNotEmpty()) {
            lines += buildString {
                append("Owner")
                if (owner.isNotEmpty()) append(": $owner")
                if (info.isNotEmpty()) {
                    if (owner.isNotEmpty()) append(". ") else append(": ")
                    append(info)
                }
            }
        }
        lines += "Local mode: tiny context window. Keep replies short. Ask before external sends."
        lines += "Filenames are case-sensitive. List a directory before reading when unsure."
        return lines.joinToString("\n").trim() + "\n"
    }

    /** Keeps at most [maxLines] lines; drops trailing blanks. Never returns blank for blank input handling upstream. */
    fun trimToLines(content: String, maxLines: Int): String {
        val limit = coerceMaxLines(maxLines)
        return content.lines().take(limit).joinToString("\n").trim() + "\n"
    }
}

class MiniContextManager(private val context: Context) {

    val file: File
        get() = File(File(context.filesDir, "home/.memory"), MiniContext.FILE_NAME)

    fun exists(): Boolean = runCatching { file.exists() }.getOrDefault(false)

    fun readRaw(): String = runCatching {
        if (file.exists()) file.readText() else ""
    }.getOrDefault("")

    fun lineCount(): Int = readRaw().lines().count { it.isNotBlank() }

    fun charCount(): Int = readRaw().length

    fun write(content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun delete(): Boolean = runCatching {
        if (file.exists()) file.delete() else true
    }.getOrDefault(false)

    /**
     * (Re)generates mini.md from the current identity settings. With
     * [overwrite] = false the existing file is left untouched.
     *
     * @return the content that is now on disk.
     */
    fun generate(
        agentName: String,
        personality: String,
        purpose: String,
        ownerName: String,
        ownerInfo: String,
        maxLines: Int,
        overwrite: Boolean = true,
    ): String {
        if (!overwrite && exists()) return readRaw()
        val content = MiniContext.trimToLines(
            MiniContext.buildContent(agentName, personality, purpose, ownerName, ownerInfo),
            maxLines,
        )
        runCatching { write(content) }
            .onFailure { Log.w(TAG, "Failed to write mini.md", it) }
        return content
    }

    fun generateIfMissing(
        agentName: String,
        personality: String,
        purpose: String,
        ownerName: String,
        ownerInfo: String,
        maxLines: Int,
    ): String = generate(agentName, personality, purpose, ownerName, ownerInfo, maxLines, overwrite = false)

    /** Content to inject into the local prompt, already trimmed to budget. Blank when disabled or missing. */
    fun readForPrompt(maxLines: Int): String {
        val raw = readRaw()
        if (raw.isBlank()) return ""
        return MiniContext.trimToLines(raw, maxLines).trim().take(MiniContext.MAX_INJECT_CHARS)
    }

    companion object {
        private const val TAG = "MiniContextManager"
    }
}
