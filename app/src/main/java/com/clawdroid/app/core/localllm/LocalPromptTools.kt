package com.clawdroid.app.core.localllm

import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.DefensiveJsonParser
import org.json.JSONArray
import java.util.UUID

/**
 * Quantized markdown for on-device models only.
 *
 * Cloud prompts use the full AGENTS/SOUL/TOOLS/SKILL/SYSTEM files untouched.
 * Small local models (0.6B/1.7B, 2K ctx) repeat markdown headers verbatim and
 * overflow, so file reads of those configs return these 1-2 line versions
 * through [LocalPromptTools.summarizeToolResult]. Disk files are unchanged.
 */
object LocalMdQuantizer {
    const val MAX_QUANT_CHARS = 400

    fun quantizedFor(fileName: String, content: String): String {
        when (fileName.uppercase()) {
            "SOUL.MD", "SOULD.MD" ->
                return "Nova is pocket agent for TesterDeveloper. Calm, practical, observant; short when fast. Memory: hello."
            "AGENTS.MD" ->
                return "Nova, ClawDroid agent for TesterDeveloper. Transparent, autonomous in sandbox, short updates."
            "SYSTEM.MD" ->
                return "Visible Android agent: clear, interruptible, precise. Calm UX."
            "TOOLS.MD" ->
                return "Use terminal for Linux, screen tools for apps. Prefer non-interactive commands. Ask before external sends."
            "SKILL.MD" ->
                return "Skills: app control, sandbox commands, files/coding/automation."
        }
        if (!fileName.endsWith(".md", ignoreCase = true)) return content.take(MAX_QUANT_CHARS)
        return stripMarkdown(content).take(MAX_QUANT_CHARS)
    }

    fun stripMarkdown(raw: String): String {
        return raw.lines()
            .map { it.trim().removePrefix("#").removePrefix("#").trim() }
            .map { it.removePrefix("-").removePrefix("*").removePrefix(">").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * Pure prompt handling for the on-device provider. No Android APIs so unit
 * tests can drive the shipped parsing and trimming without hardware.
 */
object LocalPromptTools {
    const val MAX_SYSTEM_CHARS = 800
    const val MAX_MSG_CHARS = 800
    const val MAX_HISTORY = 6
    const val MAX_TOOLS = 6
    const val MAX_PROMPT_CHARS = 3_500

    val TOOL_JSON_REGEX = Regex("```tool_json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)
    val THINK_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
    // Fallback for small models that forget the fence and emit raw JSON.
    val PLAIN_TOOL_REGEX = Regex("\\{\\s*\"name\"\\s*:\\s*\"([a-z_]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?\\})\\s*\\}", RegexOption.DOT_MATCHES_ALL)

    fun stripToolBlock(text: String): String {
        return TOOL_JSON_REGEX.replace(text, "").trim()
    }

    fun stripThinking(text: String): String {
        return THINK_REGEX.replace(text, "").trim()
    }

    fun extractThinking(text: String): String {
        return THINK_REGEX.find(text)?.value?.take(800).orEmpty()
    }

    /**
     * PocketPal-style tool compression. Raw file JSON with absolute
     * /data/user/0/... paths makes a 0.6B model copy JSON verbatim into chat
     * (see screenshots). Summarize to names-only so the model can answer.
     * Also compresses get_screen UI trees and command output, which otherwise
     * fill the 2K window with truncated mid-JSON garbage.
     */
    fun summarizeToolResult(raw: String): String {
        var s = raw.orEmpty()
        try {
            val obj = org.json.JSONObject(s)
            if (obj.has("error")) {
                val err = obj.optString("error").take(200)
                val msg = obj.optString("message").take(300)
                var out = if (msg.isNotEmpty()) "Error $err: $msg" else "Error: $err"
                if (err.contains("File not found", ignoreCase = true) ||
                    msg.contains("File not found", ignoreCase = true)
                ) {
                    out += " Filenames are case-sensitive. Call list_directory first."
                }
                return out.take(800)
            }
            if (obj.has("entries")) {
                val arr = obj.optJSONArray("entries")
                if (arr != null) {
                    val names = buildList {
                        for (i in 0 until arr.length().coerceAtMost(30)) {
                            add(arr.optJSONObject(i)?.optString("name").orEmpty())
                        }
                    }.filter { it.isNotEmpty() }
                    if (names.isNotEmpty()) {
                        val dir = obj.optString("path").take(200)
                        return if (dir.isNotEmpty()) "Directory $dir contains: ${names.joinToString(", ")}"
                        else "Files: " + names.joinToString(", ")
                    }
                }
            }
            if (obj.has("apps")) {
                val arr = obj.optJSONArray("apps")
                if (arr != null) {
                    val names = buildList {
                        for (i in 0 until arr.length().coerceAtMost(30)) {
                            add(arr.optJSONObject(i)?.optString("name").orEmpty())
                        }
                    }.filter { it.isNotEmpty() }
                    if (names.isNotEmpty()) return "Apps: " + names.joinToString(", ")
                }
            }
            if (obj.has("output")) {
                val code = obj.optInt("exit_code", 0)
                val out = obj.optString("output").take(700)
                return "exit $code: $out"
            }
            // get_screen tree / both / verification wrappers.
            val tree = when {
                obj.optJSONObject("data")?.has("nodes") == true -> obj.optJSONObject("data")
                obj.optJSONObject("tree")?.has("nodes") == true -> obj.optJSONObject("tree")
                obj.has("nodes") -> obj
                obj.optJSONObject("verification")?.optJSONObject("tree")?.has("nodes") == true ->
                    obj.optJSONObject("verification")?.optJSONObject("tree")
                else -> null
            }
            if (tree != null) return summarizeScreenTree(tree).take(800)
            if (obj.has("content")) {
                val c = obj.optString("content")
                if (c.isNotEmpty()) {
                    val p = obj.optString("path").take(200)
                    val fileName = p.substringAfterLast('/').ifBlank { "file" }
                    val body = if (p.endsWith(".md", ignoreCase = true)) {
                        LocalMdQuantizer.quantizedFor(fileName, c)
                    } else {
                        c.take(650)
                    }
                    return if (p.isNotEmpty()) "File $p:\n$body" else body.take(800)
                }
            }
        } catch (_: Exception) { }
        s = s.replace("/data/user/0/com.clawdroid.app/files/home/", "")
            .replace("/data/data/com.clawdroid.app/files/home/", "")
        return s.take(800)
    }

    internal fun summarizeScreenTree(tree: org.json.JSONObject): String {
        val pkg = tree.optString("package").takeIf { it.isNotEmpty() && it != "null" }
        val nodes = tree.optJSONArray("nodes") ?: return "Empty screen."
        val texts = mutableListOf<String>()
        val clickables = mutableListOf<String>()
        for (i in 0 until nodes.length()) {
            if (texts.size >= 20 && clickables.size >= 10) break
            val n = nodes.optJSONObject(i) ?: continue
            val label = n.optString("text").ifBlank { n.optString("contentDescription") }.trim()
            if (label.isEmpty() || label.length > 60) continue
            if (n.optBoolean("isClickable", false)) {
                if (clickables.size < 10 && label !in clickables) clickables.add(label)
            } else {
                if (texts.size < 20 && label !in texts) texts.add(label)
            }
        }
        if (texts.isEmpty() && clickables.isEmpty()) return "Empty screen."
        return buildString {
            if (pkg != null) append("App $pkg. ")
            if (texts.isNotEmpty()) append("Texts: ${texts.joinToString(" | ")}. ")
            if (clickables.isNotEmpty()) append("Clickable: ${clickables.joinToString(" | ")}.")
        }.trim()
    }

    fun stripRolePrefix(text: String): String {
        return text.replace(
            Regex("^(\\s*(Assistant|User|Tool result|System)\\s*:\\s*)+", RegexOption.IGNORE_CASE),
            "",
        ).trim()
    }

    val INNER_TURN_REGEX = Regex("\\n\\s*(Assistant|User|Tool result|System)\\s*:.*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Keep only the first assistant turn; small models append fake turns. */
    fun firstTurnOnly(text: String): String {
        val cut = INNER_TURN_REGEX.find(text) ?: return text.trim()
        return text.substring(0, cut.range.first).trim()
    }

    fun isCompactionNoise(content: String): Boolean {
        if (content.contains("[Compacted Summary]")) return true
        if (content.contains("Previous conversation summary:")) return true
        if (content.contains("highly efficient text summarization agent", ignoreCase = true)) return true
        return false
    }

    fun duplicateReminder(toolName: String): String {
        return "System reminder: you already called $toolName with these exact arguments and received the Tool result above. " +
            "Now reply in plain text using that result. Do NOT emit another ```tool_json block with the same call."
    }

    const val TOOL_PREAMBLE = """You control an Android phone agent. Available tools (reply with at most ONE per message):
"""

    const val TOOL_SUFFIX = """

To call a tool, end your response with exactly one fenced block:
```tool_json
{"name": "<tool name>", "arguments": {...}}
```
Each tool spec lists its args (* = required). Use those exact argument names.
You may think briefly inside <think>...</think> before deciding, then write the answer outside.
After you receive a Tool result message, you MUST reply in plain text using that result.
Summarize file lists as short names, never paste raw JSON back into chat.
NEVER repeat the same tool call with the same arguments twice.
If the result already answers the user, print it and stop. No more tool blocks.
Otherwise reply with plain text only. Keep replies short."""

    val BASIC_TOOLS = setOf("execute_command", "read_file", "write_file", "list_directory")

    fun renderTools(tools: JSONArray?, allowlist: Set<String>): String {
        if (tools == null || tools.length() == 0) return ""
        val specs = mutableListOf<String>()
        for (i in 0 until tools.length()) {
            if (specs.size >= MAX_TOOLS) break
            val fn = runCatching { tools.getJSONObject(i).optJSONObject("function") }.getOrNull()
                ?: continue
            val name = fn.optString("name").trim()
            if (name.isEmpty() || name !in allowlist) continue
            val desc = fn.optString("description").trim().take(100).replace('\n', ' ')
            val args = argSpec(fn)
            specs.add("{\"name\":\"$name\",\"description\":\"$desc\"$args}")
        }
        return specs.joinToString("\n")
    }

    private fun argSpec(fn: org.json.JSONObject): String {
        val props = runCatching {
            fn.optJSONObject("parameters")?.optJSONObject("properties")
        }.getOrNull() ?: return ""
        if (props.length() == 0) return ""
        val required = runCatching {
            fn.optJSONObject("parameters")?.optJSONArray("required")
        }.getOrNull()?.let { arr ->
            buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }
        } ?: emptySet()
        val names = buildList {
            val keys = props.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                add(if (key in required) "$key*" else key)
            }
        }.sorted()
        if (names.isEmpty()) return ""
        return ",\"args\":\"${names.joinToString(",")}\""
    }

    const val LOCAL_SYSTEM = "You are Nova, pocket agent for TesterDeveloper. Calm, practical, observant. " +
        "Answer briefly from Tool results. " +
        "Filenames are case-sensitive (SYSTEM.md != system.md). List directory before reading when unsure. " +
        "When asked for a file location, reply with the File path from the Tool result, not the full content."

    fun buildPrompt(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        allowlist: Set<String>,
        workingDir: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.append(LOCAL_SYSTEM).append("\n\n")
        val toolSpecs = renderTools(tools, allowlist)
        if (toolSpecs.isNotEmpty()) {
            sb.append(TOOL_PREAMBLE).append(toolSpecs).append(TOOL_SUFFIX).append("\n\n")
        }
        if (!workingDir.isNullOrBlank()) {
            sb.append("Working directory: $workingDir\n")
                .append("For file tools use relative paths or paths under it.\n\n")
        }
        val history = messages.filter { it.role != "system" }
            .filter { m -> !isCompactionNoise(m.content.orEmpty()) }
            .takeLast(MAX_HISTORY)
        for (m in history) {
            when (m.role) {
                "user" -> sb.append("User: ").append(m.content.orEmpty().take(MAX_MSG_CHARS)).append('\n')
                "assistant" -> {
                    if (m.toolCalls.isNotEmpty()) {
                        for (c in m.toolCalls) {
                            sb.append("Assistant tool call: ${c.name} ${c.arguments.take(500)}\n")
                        }
                    } else {
                        val clean = stripRolePrefix(stripThinking(m.content.orEmpty())).take(MAX_MSG_CHARS)
                        if (clean.isNotEmpty()) sb.append("Assistant: ").append(clean).append('\n')
                    }
                }
                "tool" -> sb.append("Tool result: ").append(summarizeToolResult(m.content.orEmpty()).take(MAX_MSG_CHARS)).append('\n')
            }
        }
        sb.append("Assistant:")
        val out = sb.toString()
        return if (out.length > MAX_PROMPT_CHARS) out.takeLast(MAX_PROMPT_CHARS) else out
    }

    fun parseToolCall(text: String): CompletedToolCall? {
        val fenced = TOOL_JSON_REGEX.findAll(text).lastOrNull()
        if (fenced != null) {
            val obj = DefensiveJsonParser.parseObjectOrError(fenced.groupValues[1]).getOrNull()
            if (obj != null) {
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    val args = obj.optJSONObject("arguments")?.toString() ?: "{}"
                    return CompletedToolCall(id = UUID.randomUUID().toString(), name = name, arguments = args)
                }
            }
        }
        val plain = PLAIN_TOOL_REGEX.findAll(text).lastOrNull() ?: return null
        val rebuilt = "{\"name\":\"${plain.groupValues[1]}\",\"arguments\":${plain.groupValues[2]}}"
        val obj = DefensiveJsonParser.parseObjectOrError(rebuilt).getOrNull() ?: return null
        val name = obj.optString("name").trim()
        if (name.isEmpty()) return null
        val args = obj.optJSONObject("arguments")?.toString() ?: "{}"
        return CompletedToolCall(id = UUID.randomUUID().toString(), name = name, arguments = args)
    }

    fun loadErrorMessage(t: Throwable): String {
        val raw = (t.message ?: t.toString()).trim()
        if (raw.contains("plugin", ignoreCase = true)) {
            return "Local model runtime plugin is invalid or missing. " +
                "Re-download the model in Provider settings so the llama_cpp runtime matches GenieX " +
                LocalLlmConfig.GENIEX_VERSION + ". Detail: $raw"
        }
        if (raw.contains("not downloaded", ignoreCase = true)) return raw
        return raw.ifBlank { "Local model failed to load" }
    }
}
