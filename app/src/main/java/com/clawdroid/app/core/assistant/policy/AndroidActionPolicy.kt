package com.clawdroid.app.core.assistant.policy

import android.content.Context
import com.clawdroid.app.core.assistant.permissions.AssistantCapability
import com.clawdroid.app.core.assistant.permissions.AssistantPermissionCoordinator
import com.clawdroid.app.core.assistant.permissions.PermissionBlockedResult
import org.json.JSONObject

object AndroidActionPolicy {

    sealed class PolicyResult {
        object Allowed : PolicyResult()
        data class Blocked(val blockedResult: PermissionBlockedResult) : PolicyResult()
        data class RequiresConfirmation(val title: String, val message: String) : PolicyResult()
    }

    fun preflightAction(context: Context, toolName: String, args: JSONObject): PolicyResult {
        // 1. Accessibility tools check
        val isAccessibilityTool = toolName in setOf(
            "get_screen", "tap", "tap_text", "tap_resource_id", "long_press",
            "swipe", "scroll", "type_text", "clear_text", "press_back",
            "press_home", "press_recents", "open_notifications", "launch_app",
            "perform_android_actions", "send_message_in_current_chat"
        )
        if (isAccessibilityTool) {
            val blocked = AssistantPermissionCoordinator.checkCapability(context, AssistantCapability.SCREEN_CONTROL_ACCESSIBILITY)
            if (blocked != null) {
                return PolicyResult.Blocked(blocked)
            }
        }

        // 2. Media projection / Screenshot check
        if (toolName == "screenshot") {
            val blocked = AssistantPermissionCoordinator.checkCapability(context, AssistantCapability.SCREEN_CAPTURE)
            if (blocked != null) {
                return PolicyResult.Blocked(blocked)
            }
        }

        // 3. Sensitive write actions verification
        val isSensitiveAction = toolName in setOf(
            "gmail_send_message", "gmail_create_draft",
            "calendar_create_event", "google_drive_create_file", 
            "github_create_issue", "github_create_pr", "notion_create_page"
        )
        if (isSensitiveAction) {
            return PolicyResult.RequiresConfirmation(
                title = "Confirm sensitive tool call",
                message = "The assistant is trying to run '$toolName'. Do you want to allow this action?"
            )
        }

        return PolicyResult.Allowed
    }
}
