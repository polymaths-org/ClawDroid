package com.clawdroid.app.core.control

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AndroidControlTools {

    private val screenControlToolNames = setOf(
        "get_screen", "tap", "tap_text", "tap_resource_id", "long_press", "swipe",
        "scroll", "type_text", "clear_text", "press_back", "press_home", "press_recents",
        "open_notifications", "launch_app", "get_installed_apps", "screenshot", "wait",
    )

    fun isScreenControlTool(name: String): Boolean = name in screenControlToolNames

    suspend fun getScreen(context: Context): JSONObject = runTool {
        val service = ScreenReaderService.instance
            ?: return@runTool errorResult(
                "accessibility_service_not_running",
                "User must enable ClawDroid Screen Control in Settings > Accessibility",
            )

        val tree = service.dumpNodeTree()
        if (ScreenCaptureManager.isTreeMeaningful(tree)) {
            return@runTool JSONObject()
                .put("success", true)
                .put("type", "tree")
                .put("data", org.json.JSONObject(tree))
        }

        if (ScreenCaptureManager.isActive()) {
            val base64 = ScreenCaptureManager.captureAsBase64(context)
            if (base64 != null) {
                return@runTool JSONObject()
                    .put("success", true)
                    .put("type", "screenshot")
                    .put("data", base64)
            }
        }

        errorResult(
            "empty_ui_tree",
            "Accessibility tree is empty or unhelpful. Enable screen capture in Settings for vision fallback.",
        )
    }

    suspend fun tap(x: Float, y: Float): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val ok = service.tap(x, y)
        successResult("tapped", ok).put("x", x).put("y", y)
    }

    suspend fun tapText(label: String): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val ok = service.tapByText(label)
        successResult("tapped", ok).put("label", label)
    }

    suspend fun tapResourceId(id: String): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val ok = service.tapByResourceId(id)
        successResult("tapped", ok).put("id", id)
    }

    suspend fun longPress(x: Float, y: Float): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val ok = service.longPress(x, y)
        successResult("long_pressed", ok).put("x", x).put("y", y)
    }

    suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Int,
    ): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val ok = service.swipe(x1, y1, x2, y2, durationMs.toLong())
        successResult("swiped", ok)
            .put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
            .put("duration_ms", durationMs)
    }

    fun scroll(direction: String): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        val ok = service.scroll(direction)
        successResult("scrolled", ok).put("direction", direction)
    }

    fun typeText(text: String): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        val ok = service.typeText(text)
        successResult("typed", ok).put("text", text)
    }

    fun clearText(): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        val ok = service.clearText()
        successResult("cleared", ok)
    }

    fun pressBack(): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        successResult("pressed_back", service.pressBack())
    }

    fun pressHome(): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        successResult("pressed_home", service.pressHome())
    }

    fun pressRecents(): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        successResult("pressed_recents", service.pressRecents())
    }

    fun openNotifications(): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        successResult("opened_notifications", service.openNotifications())
    }

    fun launchApp(packageName: String): JSONObject = runToolSync {
        val service = requireService() ?: return@runToolSync serviceNotRunning()
        val ok = service.launchApp(packageName)
        successResult("launched", ok).put("package_name", packageName)
    }

    suspend fun getInstalledApps(context: Context): JSONObject = withContext(Dispatchers.Default) {
        runTool {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                .map { app ->
                    JSONObject()
                        .put("name", pm.getApplicationLabel(app).toString())
                        .put("package_name", app.packageName)
                }
            val arr = JSONArray()
            apps.forEach { arr.put(it) }
            JSONObject()
                .put("success", true)
                .put("apps", arr)
                .put("count", apps.size)
        }
    }

    fun screenshot(context: Context): JSONObject = runToolSync {
        if (!ScreenCaptureManager.isActive()) {
            return@runToolSync errorResult(
                "screen_capture_not_active",
                "Request screen capture permission in Settings > Android Control",
            )
        }
        val base64 = ScreenCaptureManager.captureAsBase64(context)
            ?: return@runToolSync errorResult("capture_failed", "Failed to capture screenshot")
        JSONObject()
            .put("success", true)
            .put("type", "screenshot")
            .put("data", base64)
    }

    suspend fun wait(ms: Int): JSONObject = runTool {
        val bounded = ms.coerceIn(0, 5000)
        delay(bounded.toLong())
        JSONObject()
            .put("success", true)
            .put("waited_ms", bounded)
    }

    private fun requireService(): ScreenReaderService? = ScreenReaderService.instance

    private fun serviceNotRunning(): JSONObject = errorResult(
        "accessibility_service_not_running",
        "User must enable ClawDroid Screen Control in Settings > Accessibility",
    )

    private fun successResult(action: String, ok: Boolean): JSONObject = JSONObject()
        .put("success", ok)
        .put("action", action)

    private fun errorResult(error: String, message: String): JSONObject = JSONObject()
        .put("success", false)
        .put("error", error)
        .put("message", message)

    private inline fun runToolSync(block: () -> JSONObject): JSONObject = try {
        block()
    } catch (error: Exception) {
        errorResult(error::class.java.simpleName, error.message ?: "Unknown error")
    }

    private suspend inline fun runTool(block: () -> JSONObject): JSONObject = try {
        block()
    } catch (error: Exception) {
        errorResult(error::class.java.simpleName, error.message ?: "Unknown error")
    }
}
