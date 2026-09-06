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
    const val MAX_MSG_CHARS = 600
    const val MAX_MINI_CONTEXT_CHARS = 800
    const val MAX_HISTORY = 5
    const val MAX_TOOLS = 6
    const val MAX_PROMPT_CHARS = 2_800

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
        return THINK_REGEX.find(text)?.value?.take(400).orEmpty()
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
                if (err.contains("accessibility_service_not_running", ignoreCase = true) ||
                    err.contains("empty_ui_tree", ignoreCase = true) ||
                    err.contains("screen_unavailable", ignoreCase = true)
                ) {
                    out += " STOP. Do not claim to see the screen, any app, or any results. Report this error and stop."
                }
                return out.take(600)
            }
            if (obj.optBoolean("success") == false && obj.has("action")) {
                val action = obj.optString("action").take(60)
                val label = obj.optString("label").take(80)
                val target = if (label.isNotEmpty()) "'$label'" else action
                return "Tap failed: no clickable $target found. Call get_screen first and use an exact visible label.".take(600)
            }
            if (obj.has("warning")) {
                return obj.optString("warning").take(500)
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
            if (obj.has("messages")) {
                val arr = obj.optJSONArray("messages")
                if (arr != null) {
                    if (arr.length() == 0) return "Inbox is empty."
                    val lines = buildList {
                        for (i in 0 until arr.length().coerceAtMost(10)) {
                            val m = arr.optJSONObject(i) ?: continue
                            val from = m.optString("from").take(60)
                            val subject = m.optString("subject").take(80)
                            val id = m.optString("id")
                            if (subject.isNotEmpty()) add("$subject — $from (id $id)")
                        }
                    }
                    if (lines.isNotEmpty()) return "Emails:\n" + lines.joinToString("\n")
                }
            }
            if (obj.has("events")) {
                val arr = obj.optJSONArray("events")
                if (arr != null) {
                    if (arr.length() == 0) return "No upcoming events."
                    val lines = buildList {
                        for (i in 0 until arr.length().coerceAtMost(10)) {
                            val e = arr.optJSONObject(i) ?: continue
                            val summary = e.optString("summary").take(80)
                            val start = e.optString("start").take(40)
                            if (summary.isNotEmpty()) add("$summary at $start")
                        }
                    }
                    if (lines.isNotEmpty()) return "Events:\n" + lines.joinToString("\n")
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
            if (tree != null) return summarizeScreenTree(tree).take(600)
            if (obj.has("content")) {
                val c = obj.optString("content")
                if (c.isNotEmpty()) {
                    val p = obj.optString("path").take(200)
                    val fileName = p.substringAfterLast('/').ifBlank { "file" }
                    val body = if (p.endsWith(".md", ignoreCase = true)) {
                        LocalMdQuantizer.quantizedFor(fileName, c)
                    } else {
                        c.take(500)
                    }
                    return if (p.isNotEmpty()) "File $p:\n$body" else body.take(600)
                }
            }
        } catch (_: Exception) { }
        s = s.replace("/data/user/0/com.clawdroid.app/files/home/", "")
            .replace("/data/data/com.clawdroid.app/files/home/", "")
        return s.take(600)
    }

    private fun jsonLabel(node: org.json.JSONObject): String {
        val text = if (node.isNull("text")) "" else node.optString("text").trim()
        val desc = if (node.isNull("contentDescription")) "" else node.optString("contentDescription").trim()
        // org.json returns the literal "null" for JSONObject.NULL — never surface it.
        val cleanText = if (text.isEmpty() || text.equals("null", ignoreCase = true)) "" else text
        val cleanDesc = if (desc.isEmpty() || desc.equals("null", ignoreCase = true)) "" else desc
        return (cleanText.ifBlank { cleanDesc }).trim()
    }

    internal fun summarizeScreenTree(tree: org.json.JSONObject): String {
        val pkg = tree.optString("package").takeIf { it.isNotEmpty() && it != "null" }
        val nodes = tree.optJSONArray("nodes") ?: return "Empty screen."
        val editables = mutableListOf<String>()
        val texts = mutableListOf<String>()
        val clickables = mutableListOf<String>()
        fun visit(arr: org.json.JSONArray) {
            for (i in 0 until arr.length()) {
                if (texts.size >= 20 && clickables.size >= 10 && editables.size >= 5) return
                val n = arr.optJSONObject(i) ?: continue
                // Children are nested under "children" (see ScreenReaderService).
                // Visit them depth-first so nested inputs like Ask Gemini surface.
                val children = n.optJSONArray("children")
                val label = jsonLabel(n)
                if (label.isNotEmpty() && label.length <= 60 && label !in editables &&
                    label !in clickables && label !in texts
                ) {
                    if (n.optBoolean("isEditable", false)) {
                        if (editables.size < 5) editables.add(label)
                    } else if (n.optBoolean("isClickable", false)) {
                        if (clickables.size < 10) clickables.add(label)
                    } else {
                        if (texts.size < 20) texts.add(label)
                    }
                }
                if (children != null && children.length() > 0) visit(children)
            }
        }
        visit(nodes)
        if (texts.isEmpty() && clickables.isEmpty() && editables.isEmpty()) return "Empty screen."
        return buildString {
            if (pkg != null) append("App $pkg. ")
            if (editables.isNotEmpty()) append("Editable input (tap_text its label, then type_text the message): ${editables.joinToString(" | ")}. ")
            if (clickables.isNotEmpty()) append("Tappable (tap_text ONLY takes these exact labels): ${clickables.joinToString(" | ")}. ")
            if (texts.isNotEmpty()) append("Info text (NOT tappable, never pass to tap_text): ${texts.joinToString(" | ")}.")
        }.trim()
    }

    fun stripRolePrefix(text: String): String {
        return text.replace(
            Regex("^(\\s*(Assistant|User|Tool result|System)\\s*:\\s*)+", RegexOption.IGNORE_CASE),
            "",
        ).trim()
    }

    val INNER_TURN_REGEX = Regex("\\n\\s*(Assistant|User|Tool result|System)\\s*:.*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val TOOL_CALL_ECHO_REGEX = Regex("^\\s*(Assistant tool call|Prior tool call)\\s*:.*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

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

    const val TOOL_PREAMBLE = """You control an Android phone agent. Available tools (default to plain text; reply with at most ONE tool per message ONLY when you need it):
"""

    const val TOOL_SUFFIX = """

To call a tool, end your response with exactly one fenced block:
```tool_json
{"name": "<tool name>", "arguments": {...}}
```
Each tool spec lists its args (* = required, strip the * and never emit it in JSON). Use those exact argument names without the *.
After a Tool result, reply in plain text using that result.
NEVER repeat the same tool call with the same arguments twice.
If the result answers the user, print it and stop. No more tool blocks.
Keep replies short."""

    val BASIC_TOOLS = setOf("execute_command", "read_file", "write_file", "list_directory")

    /**
     * Intent-routed tool allowlist for on-device models. Capability-capped:
     * local models only get read-only tools they reliably handle (list/get,
     * launch, screen read, atomic sender, sandbox files). Write tools
     * (gmail_send, gmail_create_draft, calendar_create) and fragile tap/type
     * chains are cloud-only — offering them locally caused star-arg failures
     * and multi-step loops. Each set stays within [MAX_TOOLS].
     */
    fun localAllowlist(modelId: String, lastUserText: String): Set<String> {
        val lower = lastUserText.lowercase()
        val wantsGmail = lower.contains("gmail") || lower.contains("email") ||
            lower.contains("mail") || lower.contains("inbox") || lower.contains("draft") ||
            lower.contains("send mail") || lower.contains("check mail")
        val wantsCalendar = lower.contains("calendar") || lower.contains("event") ||
            lower.contains("meeting") || lower.contains("schedule") ||
            lower.contains("appointment") || lower.contains("invite")
        if (wantsGmail && wantsCalendar) {
            return linkedSetOf(
                "gmail_list_messages", "gmail_get_message",
                "calendar_list_events", "launch_app", "get_screen",
            )
        }
        if (wantsGmail) {
            return linkedSetOf(
                "gmail_list_messages", "gmail_get_message",
                "launch_app", "get_screen",
            )
        }
        if (wantsCalendar) {
            return linkedSetOf(
                "calendar_list_events", "launch_app", "get_screen",
            )
        }
        // Open an app and say/send something there (open Gemini and say
        // hello). The one-shot sender does focus+type+send atomically.
        // Kept out of file/gmail/calendar phrasing.
        // Triggers on Gemini/Bard/chat mentions even without open/launch, so
        // follow-ups like "say hello" still get the sender.
        val mentionsApp = lower.contains("open") || lower.contains("launch") ||
            Regex("\\bapp\\b").containsMatchIn(lower) || lower.contains("gemini") || lower.contains("bard") ||
            lower.contains("chat") || lower.contains("message") ||
            lower.contains("whatsapp") || lower.contains("telegram")
        val wantsSayVerb = lower.contains("say") || lower.contains("tell") ||
            lower.contains("send") || lower.contains("type") || lower.contains("write") ||
            lower.contains("hello") || Regex("\\bhi\\b").containsMatchIn(lower)
        val wantsSay = mentionsApp && wantsSayVerb &&
            !lower.contains("file") && !lower.contains("gmail") &&
            !lower.contains("calendar") && !lower.contains("event") &&
            !lower.contains("invite") && !lower.contains("meeting")
        if (wantsSay) {
            return linkedSetOf(
                "launch_app", "get_screen", "send_message_in_current_chat",
            )
        }
        // Pure knowledge/chat needs no tool: definitions, full forms,
        // explanations, greetings with no app/file/mail action. Offer no
        // tools so the model answers directly instead of list_directory {}.
        if (isPureChat(lower)) return emptySet()
        if (modelId == LocalLlmConfig.GGUF_MODEL_06B) {
            // 0.6B is file-only by default, but open/launch phrasing still
            // needs screen tools or the task degrades to file tools.
            if (lower.contains("open") || lower.contains("launch") || lower.contains("gemini")) {
                return linkedSetOf("launch_app", "get_screen", "execute_command", "list_directory")
            }
            return BASIC_TOOLS
        }
        return linkedSetOf(
            "launch_app", "get_screen",
            "execute_command", "read_file", "write_file", "list_directory",
        )
    }

    fun isPureChat(lower: String): Boolean {
        val hasAction = lower.contains("gmail") || lower.contains("email") ||
            lower.contains("mail") || lower.contains("inbox") ||
            lower.contains("calendar") || lower.contains("event") ||
            lower.contains("meeting") || lower.contains("schedule") ||
            lower.contains("appointment") || lower.contains("invite") ||
            lower.contains("open") || lower.contains("launch") ||
            Regex("\\bapp\\b").containsMatchIn(lower) || lower.contains("gemini") ||
            lower.contains("bard") || lower.contains("whatsapp") ||
            lower.contains("telegram") || lower.contains("file") ||
            lower.contains("folder") || lower.contains("directory") ||
            lower.contains("list") || lower.contains("read") ||
            lower.contains("command") || lower.contains("run") ||
            lower.contains("execute") || lower.contains("shell") ||
            lower.contains("screen") || lower.contains("tap") ||
            lower.contains("click") || lower.contains("press") ||
            lower.contains("scroll") || lower.contains("swipe") ||
            lower.contains("say") || lower.contains("send") ||
            lower.contains("type") || lower.contains("chat") ||
            lower.contains("message")
        // No action keywords means knowledge/chat: answer directly with no
        // tools. This covers "tell me about X" as well as what/who questions.
        return !hasAction
    }

    fun renderTools(tools: JSONArray?, allowlist: Set<String>): String {
        if (tools == null || tools.length() == 0) return ""
        val byName = mutableMapOf<String, String>()
        for (i in 0 until tools.length()) {
            val fn = runCatching { tools.getJSONObject(i).optJSONObject("function") }.getOrNull()
                ?: continue
            val name = fn.optString("name").trim()
            if (name.isEmpty() || name !in allowlist || name in byName) continue
            val desc = fn.optString("description").trim().take(80).replace('\n', ' ')
            val args = argSpec(fn)
            byName[name] = "{\"name\":\"$name\",\"description\":\"$desc\"$args}"
        }
        // Emit in allowlist order so launch_app/get_screen survive the MAX_TOOLS cap.
        return allowlist.filter { it in byName }.take(MAX_TOOLS).map { byName.getValue(it) }
            .joinToString("\n")
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
        "Answer briefly from Tool results. Local mail/calendar is read-only: you can list and read, never send or create on-device. " +
        "If the user asks general knowledge, definitions, full forms, or chat with no app/file/mail action, answer directly in plain text with no tool block. Only call a tool when you need live screen, files, or mail. " +
        "Filenames are case-sensitive (SYSTEM.md != system.md). List directory before reading when unsure. " +
        "To list files, call list_directory with {\"path\": \".\"} for home. " +
        "To open an app, call launch_app with the app name. Never tap blind at guessed coordinates. " +
        "After launch_app, you MUST call get_screen before any tap. get_screen takes no arguments: always {}. " +
        "tap_text ONLY accepts Tappable labels, never the message to send; the message goes in send_message_in_current_chat only. " +
        "Recipe for 'open Gemini and say hello' (hello is the MESSAGE, never a label): launch_app Gemini, get_screen {}, " +
        "then send_message_in_current_chat with text hello. Do NOT tap_text hello. Do NOT define Gemini. " +
        "Gemini runs inside package com.google.android.googlequicksearchbox. If get_screen shows Ask Gemini, you ARE in Gemini. Do NOT call launch_app again. " +
        "After get_screen shows an Editable input, your NEXT call MUST be send_message_in_current_chat. NEVER call launch_app twice in a row. " +
        "If get_screen already shows the screen you were asked to open, describe it and stop. " +
        "If a tap fails, pick a different exact label. Never repeat the same failed tap. " +
        "Never claim to see an app, page, or search results without a successful tool result showing them. " +
        "If a tool returns an Error, report it and stop instead of inventing success. "

    fun buildPrompt(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        allowlist: Set<String>,
        workingDir: String? = null,
        miniContext: String? = null,
    ): String = buildPromptWithBudgets(
        messages, tools, allowlist, workingDir, miniContext,
        maxPromptChars = MAX_PROMPT_CHARS, maxHistory = MAX_HISTORY,
        maxMsgChars = MAX_MSG_CHARS, maxMiniChars = MAX_MINI_CONTEXT_CHARS,
    )

    /** Seamless per-model entry: budgets come from [LocalLlmConfig.harnessFor]. */
    fun buildPromptForModel(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        allowlist: Set<String>,
        modelId: String,
        workingDir: String? = null,
        miniContext: String? = null,
    ): String {
        val h = LocalLlmConfig.harnessFor(modelId)
        return buildPromptWithBudgets(
            messages, tools, allowlist, workingDir, miniContext,
            maxPromptChars = h.maxPromptChars, maxHistory = h.maxHistory,
            maxMsgChars = h.maxMsgChars, maxMiniChars = h.maxMiniChars,
        )
    }

    fun buildPromptWithBudgets(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        allowlist: Set<String>,
        workingDir: String? = null,
        miniContext: String? = null,
        maxPromptChars: Int = MAX_PROMPT_CHARS,
        maxHistory: Int = MAX_HISTORY,
        maxMsgChars: Int = MAX_MSG_CHARS,
        maxMiniChars: Int = MAX_MINI_CONTEXT_CHARS,
    ): String {
        val sb = StringBuilder()
        sb.append(LOCAL_SYSTEM).append("\n\n")
        val toolSpecs = renderTools(tools, allowlist)
        val toolsBlock = if (toolSpecs.isNotEmpty()) {
            TOOL_PREAMBLE + toolSpecs + TOOL_SUFFIX + "\n\n"
        } else ""
        val dirBlock = if (!workingDir.isNullOrBlank()) {
            "Working directory: $workingDir\nFor file tools use relative paths or paths under it.\n\n"
        } else ""
        // The system+tools head must survive truncation: without visible tool
        // specs a small model can only chat, never act. Mini context yields first.
        val fixedLen = sb.length + toolsBlock.length + dirBlock.length
        val miniAllowed = (maxPromptChars - fixedLen - 1_000).coerceIn(0, maxMiniChars)
        val mini = miniContext?.trim().orEmpty()
        if (mini.isNotEmpty() && miniAllowed > 0) {
            // Pocket identity card (mini.md): who the agent/owner are, in a few
            // lines, so small-context models still know who they serve.
            sb.append(mini.take(miniAllowed)).append("\n\n")
        }
        sb.append(toolsBlock).append(dirBlock)
        val head = sb.toString()
        // Fill history newest-first into the remaining budget, dropping oldest
        // first. The newest line and the newest user message are always kept,
        // so the current task statement survives long tool threads.
        val tail = "Assistant:"
        var remaining = maxPromptChars - head.length - tail.length
        val lines = historyLines(messages, maxHistory, maxMsgChars)
        val newestIdx = lines.indices.lastOrNull() ?: -1
        val newestUserIdx = lines.indexOfLast { it.first == "user" }
        // The original request (first user message) must survive: repeat
        // warnings and nudges pile up in long threads and would otherwise push
        // the task itself out of the window, leaving the model with orders to
        // "send the message" but no idea what the message is.
        val firstUserIdx = lines.indexOfFirst { it.first == "user" }
        val guaranteed = linkedSetOf(newestIdx, newestUserIdx, firstUserIdx).filter { it >= 0 }.sorted()
        val kept = mutableListOf<Pair<Int, String>>()
        var used = 0
        for (i in guaranteed) {
            val text = lines[i].second
            if (text.length <= remaining - used) {
                kept.add(i to text)
                used += text.length
            } else if (kept.isEmpty()) {
                val fit = text.takeLast(remaining.coerceAtLeast(0))
                kept.add(i to fit)
                used += fit.length
            }
        }
        for (i in lines.indices.reversed()) {
            if (kept.any { it.first == i }) continue
            val text = lines[i].second
            if (text.length > remaining - used) continue
            kept.add(i to text)
            used += text.length
        }
        kept.sortedBy { it.first }.forEach { sb.append(it.second) }
        sb.append(tail)
        return sb.toString()
    }

    private fun historyLines(
        messages: List<ChatMessage>,
        maxHistory: Int = MAX_HISTORY,
        maxMsgChars: Int = MAX_MSG_CHARS,
    ): List<Pair<String, String>> {
        val filtered = messages.filter { it.role != "system" }
            .filter { m -> !isCompactionNoise(m.content.orEmpty()) }
        // The task statement (newest user message) must survive even when the
        // recent window is all tool chatter. The original request (oldest user
        // message) must survive too: warnings and nudges would otherwise push
        // it out, leaving orders to "send the message" with no message text.
        val newestUser = filtered.lastOrNull { it.role == "user" }
        val oldestUser = filtered.firstOrNull { it.role == "user" }
        val window = filtered.takeLast(maxHistory).toMutableList()
        if (oldestUser != null && window.none { it === oldestUser }) {
            window.add(0, oldestUser)
        }
        if (newestUser != null && window.none { it === newestUser }) {
            window.add(0, newestUser)
        }
        return window.mapNotNull { m ->
            when (m.role) {
                "user" -> "user" to ("User: " + m.content.orEmpty().take(maxMsgChars) + "\n")
                "assistant" -> {
                    if (m.toolCalls.isNotEmpty()) {
                        "assistant" to buildString {
                            for (c in m.toolCalls) {
                                append("Prior tool call: ${c.name} ${c.arguments.take(300)}\n")
                            }
                        }
                    } else {
                        val clean = stripRolePrefix(stripThinking(m.content.orEmpty())).take(maxMsgChars)
                        if (clean.isNotEmpty()) "assistant" to ("Assistant: $clean\n") else null
                    }
                }
                "tool" -> "tool" to ("Tool result: " + summarizeToolResult(m.content.orEmpty()).take(maxMsgChars) + "\n")
                else -> null
            }
        }
    }

    fun parseToolCall(text: String): CompletedToolCall? {
        val fenced = TOOL_JSON_REGEX.findAll(text).lastOrNull()
        if (fenced != null) {
            val obj = DefensiveJsonParser.parseObjectOrError(fenced.groupValues[1]).getOrNull()
            if (obj != null) {
                val name = obj.optString("name").trim()
                if (name.isNotEmpty()) {
                    val args = stripStarKeys(obj.optJSONObject("arguments"))?.toString() ?: "{}"
                    return CompletedToolCall(id = UUID.randomUUID().toString(), name = name, arguments = args)
                }
            }
        }
        val plain = PLAIN_TOOL_REGEX.findAll(text).lastOrNull() ?: return null
        val rebuilt = "{\"name\":\"${plain.groupValues[1]}\",\"arguments\":${plain.groupValues[2]}}"
        val obj = DefensiveJsonParser.parseObjectOrError(rebuilt).getOrNull() ?: return null
        val name = obj.optString("name").trim()
        if (name.isEmpty()) return null
        val args = stripStarKeys(obj.optJSONObject("arguments"))?.toString() ?: "{}"
        return CompletedToolCall(id = UUID.randomUUID().toString(), name = name, arguments = args)
    }

    /**
     * Small local models copy the required-marker * into JSON keys
     * (emitting "to*" instead of "to"). Strip a single trailing * from
     * every argument name so required args still resolve.
     */
    internal fun stripStarKeys(args: org.json.JSONObject?): org.json.JSONObject? {
        if (args == null) return null
        val out = org.json.JSONObject()
        val keys = args.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val clean = if (key.endsWith("*") && key.length > 1) key.dropLast(1) else key
            out.put(clean, args.get(key))
        }
        return out
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
