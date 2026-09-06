package com.clawdroid.app.core.engine

import com.clawdroid.app.data.api.CompletedToolCall
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopDetectorTest {

    private fun call(name: String, args: String = "{}") =
        CompletedToolCall(id = name, name = name, arguments = args)

    @Test
    fun `repeated launch warns with send next step`() {
        val detector = LoopDetector()
        detector.record(call("launch_app", "{\"app_name\":\"Gemini\"}"))
        val second = detector.record(call("launch_app", "{\"app_name\":\"Gemini\"}"))
        assertTrue(second is LoopCheckResult.Warn)
        val message = (second as LoopCheckResult.Warn).message
        assertTrue(message.contains("Do NOT call launch_app again"))
        assertTrue(message.contains("send_message_in_current_chat"))
    }

    @Test
    fun `identical screen calls stop at cap`() {
        val detector = LoopDetector()
        var result: LoopCheckResult = LoopCheckResult.Ok
        repeat(5) { result = detector.record(call("get_screen")) }
        assertTrue(result is LoopCheckResult.Stop)
    }

    @Test
    fun `empty turn nudges while run is blank`() {
        assertTrue(EmptyTurnPolicy.shouldNudge(0, true, false, 0))
        assertTrue(EmptyTurnPolicy.shouldNudge(0, true, false, 1))
        assertTrue(!EmptyTurnPolicy.shouldNudge(0, true, false, 2))
        assertTrue(!EmptyTurnPolicy.shouldNudge(10, true, false, 0))
        assertTrue(!EmptyTurnPolicy.shouldNudge(0, false, false, 0))
        assertTrue(!EmptyTurnPolicy.shouldNudge(0, true, true, 0))
        assertTrue(EmptyTurnPolicy.NUDGE.contains("send_message_in_current_chat"))
    }
}
