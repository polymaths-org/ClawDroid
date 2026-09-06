package com.clawdroid.app.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchGuardTest {

    @Test
    fun `repeat launch within window is refused`() {
        ToolExecutor.lastLaunchQuery = "Gemini"
        ToolExecutor.lastLaunchAtMs = 1_000L
        assertTrue(ToolExecutor.isRepeatLaunch("Gemini", 2_000L))
        assertTrue(ToolExecutor.isRepeatLaunch("gemini", 2_000L))
        assertFalse(ToolExecutor.isRepeatLaunch("Chrome", 2_000L))
        assertFalse(ToolExecutor.isRepeatLaunch("Gemini", 1_000L + ToolExecutor.LAUNCH_REPEAT_WINDOW_MS + 1))
        assertFalse(ToolExecutor.isRepeatLaunch("", 2_000L))
    }

    @Test
    fun `already open result orders get_screen next`() {
        val result = ToolExecutor.alreadyOpenResult("Gemini")
        assertFalse(result.optBoolean("success", true))
        assertTrue(result.optString("message").contains("get_screen"))
        assertTrue(result.optString("message").contains("Do NOT call launch_app again"))
    }

    @Test
    fun `repeat failed launch within window is refused`() {
        ToolExecutor.lastFailedLaunchQuery = "Email"
        ToolExecutor.lastFailedLaunchAtMs = 1_000L
        assertTrue(ToolExecutor.isRepeatFailedLaunch("Email", 2_000L))
        assertTrue(ToolExecutor.isRepeatFailedLaunch("email", 2_000L))
        assertFalse(ToolExecutor.isRepeatFailedLaunch("Gmail", 2_000L))
        assertFalse(ToolExecutor.isRepeatFailedLaunch("Email", 1_000L + ToolExecutor.LAUNCH_REPEAT_WINDOW_MS + 1))
    }

    @Test
    fun `launch failed result orders service tools next`() {
        val result = ToolExecutor.launchFailedResult("Email", "no activity")
        assertFalse(result.optBoolean("success", true))
        assertTrue(result.optString("message").contains("Do NOT call launch_app"))
        assertTrue(result.optString("message").contains("gmail_"))
    }

    @Test
    fun `star-suffixed keys are stripped before dispatch`() {
        val cleaned = ToolExecutor.stripStarKeys(org.json.JSONObject("{\"to*\":\"a@x.com\",\"subject*\":\"Hi\"}"))
        assertTrue(cleaned.has("to"))
        assertTrue(cleaned.has("subject"))
        assertTrue(!cleaned.has("to*"))
    }

    @Test
    fun `repeated screen failures refuse further reads`() {
        ToolExecutor.consecutiveScreenFailures = 0
        ToolExecutor.lastScreenError = ""
        ToolExecutor.lastScreenFailureAtMs = 0L
        assertFalse(ToolExecutor.shouldRefuseScreenRead(2_000L))
        ToolExecutor.noteScreenResult(org.json.JSONObject("{\"success\":false,\"error\":\"empty_ui_tree\"}"))
        ToolExecutor.noteScreenResult(org.json.JSONObject("{\"success\":false,\"error\":\"empty_ui_tree\"}"))
        assertTrue(ToolExecutor.shouldRefuseScreenRead(System.currentTimeMillis()))
        val result = ToolExecutor.screenUnavailableResult()
        assertFalse(result.optBoolean("success", true))
        assertTrue(result.optString("message").contains("Do NOT call get_screen again"))
        ToolExecutor.noteScreenResult(org.json.JSONObject("{\"success\":true}"))
        assertFalse(ToolExecutor.shouldRefuseScreenRead(System.currentTimeMillis()))
    }
}
