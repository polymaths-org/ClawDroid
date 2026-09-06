package com.clawdroid.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiVoiceTest {

    @Test
    fun knownVoices_passThrough() {
        assertEquals("alloy", OpenAITtsEngine.resolveVoice("alloy"))
        assertEquals("nova", OpenAITtsEngine.resolveVoice(" Nova "))
        assertEquals("shimmer", OpenAITtsEngine.resolveVoice("SHIMMER"))
    }

    @Test
    fun foreignVoiceNames_fallBackToAlloy() {
        assertEquals("alloy", OpenAITtsEngine.resolveVoice("Sarah"))
        assertEquals("alloy", OpenAITtsEngine.resolveVoice(""))
        assertEquals("alloy", OpenAITtsEngine.resolveVoice("rachel"))
    }

    @Test
    fun transcriptionMultipart_hasModelAndFile() {
        val wav = byteArrayOf(1, 2, 3, 4)
        val body = SpeechRecognizerClient.buildTranscriptionMultipart(
            boundary = "TESTBOUND",
            model = "gpt-4o-mini-transcribe",
            filename = "openai_stt_1.wav",
            wavBytes = wav,
        ).toString(Charsets.ISO_8859_1)
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("gpt-4o-mini-transcribe"))
        assertTrue(body.contains("filename=\"openai_stt_1.wav\""))
        assertTrue(body.contains("Content-Type: audio/wav"))
        assertTrue(body.endsWith("--TESTBOUND--\r\n"))
    }
}
