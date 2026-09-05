package com.clawdroid.app.core.control

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AndroidControlTools {

    private const val TAG = "AndroidControlTools"

    private val screenControlToolNames = setOf(
        "get_screen", "tap", "tap_text", "tap_resource_id", "long_press", "swipe",
        "scroll", "type_text", "clear_text", "press_back", "press_home", "press_recents",
        "open_notifications", "launch_app", "get_installed_apps", "screenshot", "wait",
        "perform_android_actions", "send_message_in_current_chat",
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

    fun launchApp(packageName: String, appContext: Context? = null): JSONObject = runToolSync {
        Log.i(TAG, "launchApp package=$packageName appContext=$appContext")

        // Method 1: AccessibilityService (has background start privileges)
        val service = ScreenReaderService.instance
        if (service != null) {
            Log.i(TAG, "launchApp: trying accessibility service")
            val ok = service.launchApp(packageName)
            if (ok) {
                Log.i(TAG, "launchApp: success via accessibility service")
                return@runToolSync buildResult("launched", true, packageName, "accessibility_service")
            }
            Log.w(TAG, "launchApp: accessibility service launch returned false")
        } else {
            Log.w(TAG, "launchApp: ScreenReaderService.instance is null")
        }

        // Method 2: Application context (works if app is in foreground)
        if (appContext != null) {
            Log.i(TAG, "launchApp: trying application context")
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                appContext.startActivity(intent)
                Log.i(TAG, "launchApp: success via application context")
                return@runToolSync buildResult("launched", true, packageName, "app_context")
            } catch (e: Exception) {
                Log.w(TAG, "launchApp: application context threw: ${e.message}")
            }
        }

        Log.e(TAG, "launchApp: all methods failed for $packageName")
        errorResult(
            "app_not_launched",
            "Could not launch app '$packageName'. All methods failed.",
        ).put("package_name", packageName)
    }

    suspend fun getInstalledApps(context: Context): JSONObject = withContext(Dispatchers.Default) {
        runTool {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(launcherIntent, 0)
                .map { it.activityInfo.applicationInfo }
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .distinctBy { it.packageName }
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

    suspend fun performActions(context: Context, actions: JSONArray, verify: Boolean): JSONObject = runTool {
        val results = JSONArray()
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i)
                ?: return@runTool errorResult("invalid_action", "Action at index $i must be an object")
            val actionName = action.optString("action").ifBlank {
                action.optString("type")
            }
            val result = when (actionName) {
                "tap" -> tap(
                    x = action.getDouble("x").toFloat(),
                    y = action.getDouble("y").toFloat(),
                )
                "tap_text" -> tapText(action.getString("label"))
                "tap_resource_id" -> tapResourceId(action.getString("id"))
                "long_press" -> longPress(
                    x = action.getDouble("x").toFloat(),
                    y = action.getDouble("y").toFloat(),
                )
                "swipe" -> swipe(
                    x1 = action.getDouble("x1").toFloat(),
                    y1 = action.getDouble("y1").toFloat(),
                    x2 = action.getDouble("x2").toFloat(),
                    y2 = action.getDouble("y2").toFloat(),
                    durationMs = action.optInt("duration_ms", 400),
                )
                "scroll" -> scroll(action.getString("direction"))
                "type_text" -> typeText(action.getString("text"))
                "clear_text" -> clearText()
                "press_back" -> pressBack()
                "wait" -> wait(action.optInt("ms", 250))
                else -> return@runTool errorResult(
                    "unsupported_batch_action",
                    "Unsupported action '$actionName' at index $i",
                )
            }
            results.put(
                JSONObject()
                    .put("index", i)
                    .put("action", actionName)
                    .put("result", result)
            )

            if (result.optBoolean("success") == false) {
                return@runTool JSONObject()
                    .put("success", false)
                    .put("stopped_at", i)
                    .put("results", results)
            }
        }

        val response = JSONObject()
            .put("success", true)
            .put("count", actions.length())
            .put("results", results)

        if (verify) {
            response.put("verification", getScreen(context))
        }
        response
    }

    suspend fun sendMessageInCurrentChat(context: Context, text: String, count: Int): JSONObject = runTool {
        val service = requireService() ?: return@runTool serviceNotRunning()
        val boundedCount = count.coerceIn(1, 20)
        val results = JSONArray()

        for (i in 0 until boundedCount) {
            val focused = focusLikelyChatInput(context, service)
            delay(180)
            val typed = service.typeText(text)
            if (!typed) {
                return@runTool JSONObject()
                    .put("success", false)
                    .put("error", "message_input_not_available")
                    .put("message", "I could not focus an editable message input in the current chat.")
                    .put("sent_count", i)
                    .put("focused", focused)
                    .put("results", results)
            }

            delay(160)
            val sent = tapLikelySendButton(context, service)
            if (!sent) {
                return@runTool JSONObject()
                    .put("success", false)
                    .put("error", "send_button_not_available")
                    .put("message", "I typed the message, but could not find or tap the send button.")
                    .put("sent_count", i)
                    .put("results", results)
            }

            results.put(
                JSONObject()
                    .put("index", i)
                    .put("text", text)
                    .put("focused", focused)
                    .put("sent", true)
            )
            delay(260)
        }

        JSONObject()
            .put("success", true)
            .put("sent_count", boundedCount)
            .put("text", text)
            .put("results", results)
    }

    private suspend fun focusLikelyChatInput(context: Context, service: ScreenReaderService): Boolean {
        val inputResourceIds = listOf(
            "com.whatsapp:id/entry",
            "com.whatsapp.w4b:id/entry",
            "org.telegram.messenger:id/chat_edit_text",
            "org.thunderdog.challegram:id/input",
        )
        for (id in inputResourceIds) {
            if (service.tapByResourceId(id)) return true
        }

        val inputLabels = listOf(
            "Message",
            "Type a message",
            "Write a message",
            "Text message",
            "Message input",
        )
        for (label in inputLabels) {
            if (service.tapByText(label)) return true
        }

        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels * 0.42f
        val candidateYs = listOf(
            metrics.heightPixels - 150f,
            metrics.heightPixels - 230f,
            metrics.heightPixels * 0.92f,
            metrics.heightPixels * 0.54f,
        )
        for (y in candidateYs) {
            if (service.tap(x, y)) return true
            delay(120)
        }
        return false
    }

    private suspend fun tapLikelySendButton(context: Context, service: ScreenReaderService): Boolean {
        val sendResourceIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "org.telegram.messenger:id/chat_send_button",
            "org.thunderdog.challegram:id/btn_send",
        )
        for (id in sendResourceIds) {
            if (service.tapByResourceId(id)) return true
        }

        val sendLabels = listOf("Send", "Send message")
        for (label in sendLabels) {
            if (service.tapByText(label)) return true
        }

        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels - 58f
        val candidateYs = listOf(
            metrics.heightPixels - 150f,
            metrics.heightPixels - 230f,
            metrics.heightPixels * 0.92f,
            metrics.heightPixels * 0.54f,
        )
        for (y in candidateYs) {
            if (service.tap(x, y)) return true
            delay(120)
        }
        return false
    }

    private fun requireService(): ScreenReaderService? = ScreenReaderService.instance

    private fun serviceNotRunning(): JSONObject = errorResult(
        "accessibility_service_not_running",
        "User must enable ClawDroid Screen Control in Settings > Accessibility",
    )

    private fun successResult(action: String, ok: Boolean): JSONObject = JSONObject()
        .put("success", ok)
        .put("action", action)

    private fun buildResult(action: String, ok: Boolean, packageName: String, method: String): JSONObject = JSONObject()
        .put("success", ok)
        .put("action", action)
        .put("package_name", packageName)
        .put("method", method)

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
