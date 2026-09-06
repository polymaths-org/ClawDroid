package com.clawdroid.app.core.agent

import com.clawdroid.app.core.config.AppConfigManager

/**
 * Code mode: when the conversation is about coding, the agent acts as a code
 * reviewer (reads files first, explains diffs, runs checks) and the UI shows
 * a Code-mode chip plus the VS Code-style explorer.
 *
 * Resolution order: manual override pref (on/off) wins, otherwise auto-detect
 * from recent user messages.
 */
object CodeMode {

    private val CODE_EXTENSIONS = setOf(
        "kt", "kts", "java", "py", "js", "jsx", "ts", "tsx", "go", "rs",
        "c", "h", "cpp", "hpp", "swift", "rb", "php", "cs", "scala",
        "gradle", "xml", "json", "yaml", "yml", "toml", "md", "sh",
        "sql", "css", "scss", "html", "vue", "dart", "lua", "r",
    )

    private val CODE_KEYWORDS = listOf(
        "code", "function", "class", "method", "variable", "bug", "fix",
        "refactor", "debug", "compile", "build", "error", "exception",
        "stacktrace", "stack trace", "review", "pull request", "commit",
        "merge conflict", "test", "unittest", "api", "endpoint", "regex",
        "algorithm", "deploy", "logcat", "crash", " nullpointer", "import",
    )

    /** Heuristic: true when [text] looks like a coding task. */
    fun isCodingTask(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        var score = 0
        if (CODE_EXTENSIONS.any { ext -> Regex("""\.$ext\b""").containsMatchIn(lower) }) score += 2
        if (lower.contains("src/") || lower.contains("app/src") || lower.contains("```")) score += 2
        score += CODE_KEYWORDS.count { lower.contains(it) }
        return score >= 2
    }

    /**
     * Resolve code mode for a conversation. Manual override wins;
     * auto-detect scans the latest user messages.
     */
    fun resolveForConversation(userTexts: List<String>): Boolean = when (AppConfigManager.codeModeOverride) {
        "on" -> true
        "off" -> false
        else -> userTexts.takeLast(6).any(::isCodingTask)
    }

    /** Extra system-prompt section injected while code mode is active. */
    fun reviewerPrompt(): String = buildString {
        appendLine("## Code Mode (reviewer)")
        appendLine("The user is doing coding work. Act as a sharp code reviewer:")
        appendLine("- Read the relevant files BEFORE proposing changes; never guess APIs.")
        appendLine("- Explain what you will change and why, then make the edit.")
        appendLine("- After edits, run the cheapest available check (build, unit tests, lint) and report real results.")
        appendLine("- When showing code, keep snippets short and point at file:line.")
        appendLine("- If the user opens the Code explorer, assume they are looking at the files with you.")
    }.trimEnd()
}
