package com.clawdroid.app.core.localllm

import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmProviderFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptToolsTest {

    @Test
    fun `local provider id selects on-device path`() {
        assertTrue(LlmProviderFactory.isLocalSelected(LocalLlmConfig.PROVIDER_ID))
        assertTrue(!LlmProviderFactory.isLocalSelected("openai"))
    }

    @Test
    fun `valid tool_json parses with arguments`() {
        val text = "I'll list files.\n```tool_json\n" +
            "{\"name\": \"list_directory\", \"arguments\": {\"path\": \"/tmp\"}}\n```"
        val call = LocalPromptTools.parseToolCall(text)
        assertNotNull(call)
        assertEquals("list_directory", call!!.name)
        assertTrue(call.arguments.contains("/tmp"))
    }

    @Test
    fun `garbled tool_json with trailing comma recovers`() {
        val text = "```tool_json\n{\"name\": \"read_file\", \"arguments\": {\"path\": \"/a\",}}\n```"
        val call: CompletedToolCall? = LocalPromptTools.parseToolCall(text)
        assertNotNull(call)
        assertEquals("read_file", call!!.name)
    }

    @Test
    fun `plain text without block returns null`() {
        assertNull(LocalPromptTools.parseToolCall("Just a normal reply, no tool."))
    }

    @Test
    fun `renderTools keeps basic tools and drops others`() {
        val tools = JSONArray()
        for (name in listOf("execute_command", "read_file", "write_file", "list_directory", "gmail_send_message")) {
            tools.put(
                JSONObject().put(
                    "function",
                    JSONObject().put("name", name).put("description", "desc $name"),
                ),
            )
        }
        val allow = setOf("execute_command", "read_file", "write_file", "list_directory")
        val out = LocalPromptTools.renderTools(tools, allow)
        assertTrue(out.contains("execute_command"))
        assertTrue(out.contains("list_directory"))
        assertTrue(!out.contains("gmail_send_message"))
    }

    @Test
    fun `buildPrompt trims long history within budget`() {
        val msgs = mutableListOf(ChatMessage(role = "system", content = "sys"))
        repeat(30) { i ->
            msgs.add(ChatMessage(role = "user", content = "q$i ".repeat(400)))
            msgs.add(ChatMessage(role = "tool", content = "r$i ".repeat(400)))
        }
        val prompt = LocalPromptTools.buildPrompt(msgs, null, LocalPromptTools.BASIC_TOOLS)
        assertTrue(prompt.length <= LocalPromptTools.MAX_PROMPT_CHARS)
        assertTrue(prompt.endsWith("Assistant:"))
        assertTrue(!prompt.contains("q0"))
    }

    @Test
    fun `plugin load error maps to actionable message`() {
        val msg = LocalPromptTools.loadErrorMessage(RuntimeException("invalid plugin 7"))
        assertTrue(msg.contains("runtime plugin", ignoreCase = true))
        assertTrue(msg.contains("Re-download", ignoreCase = true))
    }
}
