package com.clawdroid.app.core.assistant.permissions

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.assistant.overlay.AssistantOverlayCoordinator
import com.clawdroid.app.core.assistant.policy.AndroidActionPolicy
import com.clawdroid.app.core.control.AndroidControlTools
import com.clawdroid.app.core.engine.ToolExecutionResult
import com.clawdroid.app.core.engine.ToolExecutor
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.DefensiveJsonParser
import org.json.JSONObject

object PermissionAwareToolExecutor {
    private const val TAG = "PermissionAwareToolExecutor"
    private val toolsNoOverlayHide = emptySet<String>()
    private val visionBlockingTools = setOf("get_screen", "screenshot")
    private val touchBlockingTools = setOf(
        "tap", "tap_text", "tap_resource_id", "long_press", "swipe", "scroll",
        "type_text", "clear_text", "press_back", "press_home", "press_recents",
        "open_notifications", "launch_app", "perform_android_actions", "send_message_in_current_chat"
    )

    suspend fun execute(
        context: Context,
        call: CompletedToolCall,
        onProgress: (suspend (String) -> Unit)? = null
    ): ToolExecutionResult {
        val args = DefensiveJsonParser.parseObjectOrError(call.arguments).getOrElse { JSONObject() }
        Log.i(TAG, "execute start tool=${call.name} callId=${call.id} argsLen=${call.arguments.length}")
        
        return when (val preflight = AndroidActionPolicy.preflightAction(context, call.name, args)) {
            is AndroidActionPolicy.PolicyResult.Blocked -> {
                Log.w(TAG, "execute blocked tool=${call.name} capability=${preflight.blockedResult.capability}")
                ToolExecutionResult(
                    callId = call.id,
                    content = preflight.blockedResult.toJsonObject().toString(),
                    isError = true
                )
            }
            is AndroidActionPolicy.PolicyResult.RequiresConfirmation -> {
                Log.w(TAG, "execute requires confirmation tool=${call.name}")
                // Return structured JSON for overlay/chat confirmation prompt
                val confirmJson = JSONObject()
                    .put("success", false)
                    .put("error", "confirmation_required")
                    .put("title", preflight.title)
                    .put("message", preflight.message)
                    .put("tool_name", call.name)
                    .put("arguments", args)
                
                ToolExecutionResult(
                    callId = call.id,
                    content = confirmJson.toString(),
                    isError = true
                )
            }
            AndroidActionPolicy.PolicyResult.Allowed -> {
                if (AndroidControlTools.isScreenControlTool(call.name) && call.name !in toolsNoOverlayHide) {
                    val settleMs = when (call.name) {
                        in visionBlockingTools -> 3_000L
                        in touchBlockingTools -> 350L
                        else -> 150L
                    }
                    AssistantOverlayCoordinator.withOverlayHiddenForExternalUi(
                        reason = call.name,
                        settleMs = settleMs,
                    ) {
                        Log.i(TAG, "execute allowed visible-overlay tool=${call.name} settleMs=$settleMs")
                        ToolExecutor.execute(context, call, onProgress)
                    }
                } else {
                    Log.i(TAG, "execute allowed normal tool=${call.name}")
                    ToolExecutor.execute(context, call, onProgress)
                }
            }
        }
    }
}
