package com.clawdroid.app.core.localllm

import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.DefensiveJsonParser
import org.json.JSONArray
import java.util.UUID

/**
 * Pure prompt handling for the on-device provider. No Android APIs so unit
 * tests can drive the shipped parsing and trimming without hardware.
 */
object LocalPromptTools {
    const val MAX_SYSTEM_CHARS = 2_000
    const val MAX_MSG_CHARS = 1_500
    const val MAX_HISTORY = 12
    const val MAX_TOOLS = 20
    const val MAX_PROMPT_CHARS = 6_000

    val TOOL_JSON_REGEX = Regex("```tool_json\\s*(\\{.*?\\})\\s*```", RegexOption.DOT_MATCHES_ALL)

    const val TOOL_PREAMBLE = """You control an Android phone agent. Available tools (reply with at most ONE per message):
"""

    const val TOOL_SUFFIX = """

To call a tool, end your response with exactly one fenced block:
```tool_json
{"name": "<tool name>", "arguments": {...}}
```
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
            specs.add("{\"name\":\"$name\",\"description\":\"$desc\"}")
        }
        return specs.joinToString("\n")
    }

    fun buildPrompt(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        allowlist: Set<String>,
    ): String {
        val sb = StringBuilder()
        val systems = messages.filter { it.role == "system" }.mapNotNull { it.content }
        if (systems.isNotEmpty()) {
            sb.append(systems.joinToString("\n\n").take(MAX_SYSTEM_CHARS)).append("\n\n")
        }
        val toolSpecs = renderTools(tools, allowlist)
        if (toolSpecs.isNotEmpty()) {
            sb.append(TOOL_PREAMBLE).append(toolSpecs).append(TOOL_SUFFIX).append("\n\n")
        }
        for (m in messages.filter { it.role != "system" }.takeLast(MAX_HISTORY)) {
            when (m.role) {
                "user" -> sb.append("User: ").append(m.content.orEmpty().take(MAX_MSG_CHARS)).append('\n')
                "assistant" -> {
                    if (m.toolCalls.isNotEmpty()) {
                        for (c in m.toolCalls) {
                            sb.append("Assistant tool call: ${c.name} ${c.arguments.take(500)}\n")
                        }
                    } else {
                        sb.append("Assistant: ").append(m.content.orEmpty().take(MAX_MSG_CHARS)).append('\n')
                    }
                }
                "tool" -> sb.append("Tool result: ").append(m.content.orEmpty().take(MAX_MSG_CHARS)).append('\n')
            }
        }
        sb.append("Assistant:")
        val out = sb.toString()
        return if (out.length > MAX_PROMPT_CHARS) out.takeLast(MAX_PROMPT_CHARS) else out
    }

    fun parseToolCall(text: String): CompletedToolCall? {
        val match = TOOL_JSON_REGEX.findAll(text).lastOrNull() ?: return null
        val obj = DefensiveJsonParser.parseObjectOrError(match.groupValues[1]).getOrNull()
            ?: return null
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
