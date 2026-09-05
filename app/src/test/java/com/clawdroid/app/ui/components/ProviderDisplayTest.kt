package com.clawdroid.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderDisplayTest {

    @Test
    fun zenIdShowsHumanName() {
        assertEquals("OpenCode Zen", providerDisplayName("opencode_zen"))
        assertEquals("OpenCode Zen", providerDisplayName("OpenCode_Zen"))
    }

    @Test
    fun knownProvidersMap() {
        assertEquals("Ollama", providerDisplayName("ollama"))
        assertEquals("Together AI", providerDisplayName("together"))
        assertEquals("xAI", providerDisplayName("xai"))
    }

    @Test
    fun unknownIdFallsBackToPrettified() {
        assertEquals("My Custom", providerDisplayName("my_custom"))
    }
}
