package com.clawdroid.app.core.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamErrorTextTest {

    @Test
    fun genericProviderErrorKeepsUnderlyingCause() {
        val raw = "HTTP 500: {\"type\":\"error\",\"error\":{\"type\":\"provider_error\",\"message\":\"upstream unavailable\"}}"
        val text = mapStreamErrorToUserText(raw, hasMedia = false)

        assertTrue(text.startsWith("The model provider returned an error."))
        assertTrue(text.contains("Underlying error: $raw"))
    }

    @Test
    fun rejectedKeyKeepsUnderlyingCause() {
        val raw = "HTTP 401: invalid api key"
        val text = mapStreamErrorToUserText(raw, hasMedia = false)

        assertTrue(text.startsWith("The model provider rejected the API key."))
        assertTrue(text.contains("Underlying error: $raw"))
    }

    @Test
    fun unknownErrorPassesThroughRaw() {
        val raw = "socket closed unexpectedly"
        assertTrue(mapStreamErrorToUserText(raw, hasMedia = false).contains(raw))
    }
}
