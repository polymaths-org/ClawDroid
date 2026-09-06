package com.clawdroid.app.core.memory

import com.clawdroid.app.core.localllm.LocalPromptTools
import com.clawdroid.app.data.api.ChatMessage
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniContextTest {

    @Test
    fun `buildContent includes agent and owner identity`() {
        val out = MiniContext.buildContent(
            agentName = "Nova",
            personality = "Helpful",
            purpose = "Coding help",
            ownerName = "Sam",
            ownerInfo = "Prefers Python.",
        )
        assertTrue(out.startsWith("# Mini Context"))
        assertTrue(out.contains("Nova"))
        assertTrue(out.contains("Helpful"))
        assertTrue(out.contains("Coding help"))
        assertTrue(out.contains("Sam"))
        assertTrue(out.contains("Prefers Python."))
    }

    @Test
    fun `buildContent skips blank identity fields`() {
        val out = MiniContext.buildContent(
            agentName = "",
            personality = "",
            purpose = "",
            ownerName = "",
            ownerInfo = "",
        )
        assertTrue(out.contains("Agent Nova"))
        assertFalse(out.contains("Owner"))
    }

    @Test
    fun `trimToLines caps line count`() {
        val content = (1..30).joinToString("\n") { "line $it" }
        val trimmed = MiniContext.trimToLines(content, 12)
        assertEquals(12, trimmed.trim().lines().size)
        assertTrue(trimmed.startsWith("line 1"))
    }

    @Test
    fun `trimToLines coerces out-of-range budgets`() {
        val content = (1..50).joinToString("\n") { "line $it" }
        assertEquals(MiniContext.MIN_LINES, MiniContext.trimToLines(content, 1).trim().lines().size)
        assertEquals(MiniContext.MAX_LINES, MiniContext.trimToLines(content, 999).trim().lines().size)
    }

    @Test
    fun `coerceMaxLines clamps to min-max range`() {
        assertEquals(MiniContext.MIN_LINES, MiniContext.coerceMaxLines(0))
        assertEquals(MiniContext.MAX_LINES, MiniContext.coerceMaxLines(10_000))
        assertEquals(12, MiniContext.coerceMaxLines(12))
    }

    @Test
    fun `buildPrompt injects mini context after system prompt`() {
        val msgs = listOf(ChatMessage(role = "user", content = "hi"))
        val out = LocalPromptTools.buildPrompt(msgs, JSONArray(), emptySet(), null, miniContext = "Agent Nova — test card.")
        assertTrue(out.contains("Agent Nova — test card."))
        val systemIdx = out.indexOf(LocalPromptTools.LOCAL_SYSTEM.take(20))
        val miniIdx = out.indexOf("Agent Nova — test card.")
        assertTrue(systemIdx >= 0 && miniIdx > systemIdx)
    }

    @Test
    fun `buildPrompt without mini context is unchanged`() {
        val msgs = listOf(ChatMessage(role = "user", content = "hi"))
        val withNull = LocalPromptTools.buildPrompt(msgs, JSONArray(), emptySet(), null, miniContext = null)
        val withBlank = LocalPromptTools.buildPrompt(msgs, JSONArray(), emptySet(), null, miniContext = "   ")
        val legacy = LocalPromptTools.buildPrompt(msgs, JSONArray(), emptySet(), null)
        assertEquals(legacy, withNull)
        assertEquals(legacy, withBlank)
    }
}
