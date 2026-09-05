package com.clawdroid.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAgentErrorTest {

    @Test
    fun serverErrorShowsShortMessageAndKeepsDetails() {
        val raw = "Assistant run failed: HTTP 500: {\"type\":\"error\",\"error\":{\"type\":\"error\",\"message\":\"Internal server error\"}}"
        val ui = formatAgentError(raw)

        assertEquals("The model service had a problem. Try again in a moment.", ui.shortMessage)
        assertTrue(ui.isProviderError)
        assertEquals(raw, ui.details)
        assertFalse(ui.shortMessage.contains("{"))
    }

    @Test
    fun rejectedKeyPointsToProviderSettings() {
        val ui = formatAgentError("HTTP 401: {\"error\":\"invalid api key\"}")

        assertEquals("Your API key was rejected. Check it in Settings > Provider.", ui.shortMessage)
        assertTrue(ui.isProviderError)
    }

    @Test
    fun plainMessagePassesThroughWithoutDetails() {
        val ui = formatAgentError("Stopped: user pressed stop")

        assertEquals("Stopped: user pressed stop", ui.shortMessage)
        assertNull(ui.details)
        assertFalse(ui.isProviderError)
    }

    @Test
    fun connectionFailureStaysVisibleWithShortMessage() {
        val ui = formatAgentError("Failed to connect to localhost/127.0.0.1:11434")

        assertEquals("Could not reach the model service. Check your connection and retry.", ui.shortMessage)
        assertTrue(ui.isProviderError)
        assertFalse(ui.shortMessage.contains("{"))
    }
}
