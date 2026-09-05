package com.clawdroid.app.core.assistant.context

import android.util.Log
import com.clawdroid.app.core.assistant.AssistantContextSnapshot
import com.clawdroid.app.core.assistant.CaptureMethod
import com.clawdroid.app.core.control.ScreenReaderService
import org.json.JSONArray
import org.json.JSONObject

object AndroidControlContextBridge {
    private const val TAG = "AndroidControlContextBridge"

    suspend fun captureSnapshot(screenshotPath: String? = null): AssistantContextSnapshot? {
        val service = ScreenReaderService.instance ?: run {
            Log.i(TAG, "captureSnapshot skipped: ScreenReaderService not active screenshotPath=$screenshotPath")
            return null
        }
        val treeJson = service.dumpNodeTree()
        Log.i(TAG, "dumpNodeTree len=${treeJson.length} screenshotPath=$screenshotPath")
        if (treeJson.isBlank()) {
            Log.w(TAG, "captureSnapshot failed: empty tree")
            return null
        }

        return try {
            val root = JSONObject(treeJson)
            val sourcePackage = root.optString("package")
                .takeIf { it.isNotBlank() && it != "null" }
            val nodesArray = root.optJSONArray("nodes")
            Log.i(TAG, "parsed tree package=$sourcePackage nodes=${nodesArray?.length() ?: 0} truncated=${root.optBoolean("truncated")}")

            val visibleTextBuilder = StringBuilder()
            val contentDescBuilder = StringBuilder()
            var focusedText: String? = null

            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val node = nodesArray.optJSONObject(i)
                    walkJsonNode(node, visibleTextBuilder, contentDescBuilder) { text ->
                        focusedText = text
                    }
                }
            }

            val snapshot = AssistantContextSnapshot(
                sourcePackage = sourcePackage,
                sourceActivity = null,
                visibleText = visibleTextBuilder.toString().trim(),
                contentDescriptionText = contentDescBuilder.toString().trim(),
                focusedText = focusedText,
                webUri = null, // Accessibility nodes usually don't expose web domain easily unless parsed from chrome nodes
                screenshotPath = screenshotPath,
                selectedRegionPath = null,
                capturedAt = System.currentTimeMillis(),
                captureMethod = CaptureMethod.ANDROID_CONTROL_TREE
            )
            Log.i(TAG, "captureSnapshot success visibleLen=${snapshot.visibleText.length} descLen=${snapshot.contentDescriptionText.length} focused=${snapshot.focusedText?.take(48)}")
            snapshot
        } catch (e: Exception) {
            Log.e(TAG, "captureSnapshot parse failed", e)
            null
        }
    }

    private fun walkJsonNode(
        node: JSONObject?,
        visibleBuilder: StringBuilder,
        contentDescBuilder: StringBuilder,
        onFocused: (String) -> Unit,
    ) {
        if (node == null) return

        val text = node.optString("text", "").trim()
        val contentDesc = node.optString("contentDescription", "").trim()
        val isEditable = node.optBoolean("isEditable", false)

        if (text.isNotEmpty()) {
            // Check for potential passwords in keys
            val resourceId = node.optString("resourceId", "").lowercase()
            val isPassword = resourceId.contains("password") || resourceId.contains("pin")
            val formattedText = if (isPassword) "[REDACTED PASSWORD]" else text
            
            visibleBuilder.append(formattedText).append("\n")
            // In accessibility node dumps, "focused" text is often the editable or currently active input field text
            if (isEditable) {
                onFocused(formattedText)
            }
        }

        if (contentDesc.isNotEmpty()) {
            contentDescBuilder.append(contentDesc).append("\n")
        }

        val children = node.optJSONArray("children")
        if (children != null) {
            for (i in 0 until children.length()) {
                walkJsonNode(children.optJSONObject(i), visibleBuilder, contentDescBuilder, onFocused)
            }
        }
    }
}
