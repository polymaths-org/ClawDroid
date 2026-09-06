package com.clawdroid.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Voice setting accepts names ("Sarah") or raw IDs; both must resolve to a
 * live voice ID instead of being sent verbatim to the API.
 */
class ElevenLabsVoiceMatchTest {

    private val voices = listOf(
        ElevenLabsTtsEngine.VoiceRef("Sarah - Mature, Reassuring, Confident", "EXAVITQu4vr4xnSDxMaL"),
        ElevenLabsTtsEngine.VoiceRef("Rachel (Female)", "deadbeefdeadbeefdead"),
        ElevenLabsTtsEngine.VoiceRef("Adam - Dominant, Firm", "pNInz6obpgDQGcFmaJgB"),
    )

    @Test
    fun rawId_passesThrough() {
        assertEquals(
            "pNInz6obpgDQGcFmaJgB",
            ElevenLabsTtsEngine.matchVoiceId("pNInz6obpgDQGcFmaJgB", voices),
        )
    }

    @Test
    fun exactName_matchesCaseInsensitively_andStripsDescriptors() {
        assertEquals(
            "EXAVITQu4vr4xnSDxMaL",
            ElevenLabsTtsEngine.matchVoiceId("sarah", voices),
        )
        assertEquals(
            "deadbeefdeadbeefdead",
            ElevenLabsTtsEngine.matchVoiceId("Rachel", voices),
        )
    }

    @Test
    fun prefix_matches() {
        assertEquals(
            "pNInz6obpgDQGcFmaJgB",
            ElevenLabsTtsEngine.matchVoiceId("ada", voices),
        )
    }

    @Test
    fun blankOrUnknown_returnsNull() {
        assertNull(ElevenLabsTtsEngine.matchVoiceId("   ", voices))
        assertNull(ElevenLabsTtsEngine.matchVoiceId("nobody-here", voices))
    }
}
