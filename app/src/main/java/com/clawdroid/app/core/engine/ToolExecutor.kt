package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.core.tools.BrowseWebTool
import com.clawdroid.app.core.tools.CheckProcessTool
import com.clawdroid.app.core.tools.CommandTool
import com.clawdroid.app.core.tools.EditFileTool
import com.clawdroid.app.core.tools.KillProcessTool
import com.clawdroid.app.core.tools.ListDirectoryTool
import com.clawdroid.app.core.tools.ListProcessesTool
import com.clawdroid.app.core.tools.NotificationTool
import com.clawdroid.app.core.tools.ReadFileTool
import com.clawdroid.app.core.tools.SendInputTool
import com.clawdroid.app.core.tools.StartProcessTool
import com.clawdroid.app.core.tools.WebSearchTool
import com.clawdroid.app.core.tools.WriteFileTool
import com.clawdroid.app.core.tools.GoogleTools
import com.clawdroid.app.core.tools.checkAndRequestStoragePermission
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.DefensiveJsonParser
import org.json.JSONObject


data class ToolExecutionResult(
    val callId: String,
    val content: String,
    val isError: Boolean = false,
)

object ToolExecutor {
    suspend fun execute(
        context: Context,
        call: CompletedToolCall,
        onProgress: (suspend (String) -> Unit)? = null,
    ): ToolExecutionResult = runCatching {
        val args = DefensiveJsonParser.parseObjectOrError(call.arguments).getOrThrow()
        when (call.name) {
            "execute_command" -> {
                val command = args.getString("command")
                if (!checkAndRequestStoragePermission(context, command)) {
                    throw SecurityException("Storage permission is missing on the device. I have launched the Android system settings screen for the user to grant 'All Files Access'. Please inform the user that they must toggle the permission 'ON' and then ask you to retry.")
                }
                executeCommand(context, args, onProgress)
            }
            "start_process" -> StartProcessTool.execute(
                context = context,
                command = args.getString("command"),
                cwd = args.optString("cwd").takeIf { it.isNotBlank() },
                timeoutSeconds = args.optLong("timeout_seconds", 300),
            )
            "check_process" -> CheckProcessTool.execute(context, args.getString("process_id"))
            "send_input" -> SendInputTool.execute(
                context = context,
                processId = args.getString("process_id"),
                input = args.getString("input"),
            )
            "kill_process" -> KillProcessTool.execute(context, args.getString("process_id"))
            "list_processes" -> ListProcessesTool.execute(context)
            "read_file" -> ReadFileTool.execute(
                context = context,
                path = args.getString("path"),
                startLine = args.optIntOrNull("start_line"),
                endLine = args.optIntOrNull("end_line"),
            )
            "write_file" -> WriteFileTool.execute(
                context = context,
                path = args.getString("path"),
                content = args.getString("content"),
            )
            "edit_file" -> EditFileTool.execute(
                context = context,
                path = args.getString("path"),
                search = args.getString("search"),
                replace = args.getString("replace"),
            )
            "list_directory" -> ListDirectoryTool.execute(context, args.getString("path"))
            "browse_web" -> BrowseWebTool.execute(args.getString("url"))
            "web_search" -> WebSearchTool.execute(args.getString("query"))
            "send_notification" -> NotificationTool.execute(
                context = context,
                title = args.getString("title"),
                body = args.getString("body"),
            )
            "gmail_list_messages", "gmail_get_message", "gmail_send_message", "gmail_create_draft" -> {
                if (!com.clawdroid.app.core.service.GoogleAuthManager.isGoogleConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.googleConnectorEnabled ||
                    !com.clawdroid.app.core.config.AppConfigManager.googleGmailEnabled) {
                    throw IllegalStateException("Gmail tools are currently disabled or Google account is disconnected.")
                }
                when (call.name) {
                    "gmail_list_messages" -> GoogleTools.listEmails(
                        query = args.optString("query").takeIf { it.isNotBlank() },
                        maxResults = args.optInt("max_results", 10)
                    )
                    "gmail_get_message" -> GoogleTools.getEmail(args.getString("id"))
                    "gmail_send_message" -> GoogleTools.sendEmail(
                        to = args.getString("to"),
                        subject = args.getString("subject"),
                        body = args.getString("body")
                    )
                    "gmail_create_draft" -> GoogleTools.createDraft(
                        to = args.getString("to"),
                        subject = args.getString("subject"),
                        body = args.getString("body")
                    )
                    else -> error("Unreachable")
                }
            }
            "calendar_list_events", "calendar_create_event" -> {
                if (!com.clawdroid.app.core.service.GoogleAuthManager.isGoogleConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.googleConnectorEnabled ||
                    !com.clawdroid.app.core.config.AppConfigManager.googleCalendarEnabled) {
                    throw IllegalStateException("Calendar tools are currently disabled or Google account is disconnected.")
                }
                when (call.name) {
                    "calendar_list_events" -> GoogleTools.listCalendarEvents(
                        timeMin = args.optString("time_min").takeIf { it.isNotBlank() },
                        timeMax = args.optString("time_max").takeIf { it.isNotBlank() },
                        maxResults = args.optInt("max_results", 15)
                    )
                    "calendar_create_event" -> GoogleTools.createCalendarEvent(
                        summary = args.getString("summary"),
                        description = args.optString("description").takeIf { it.isNotBlank() },
                        startTime = args.getString("start_time"),
                        endTime = args.getString("end_time")
                    )
                    else -> error("Unreachable")
                }
            }
            else -> {
                McpServerLauncher.executeMcpTool(call.name, args)?.toString()
                    ?: error("Unsupported tool: ${call.name}")
            }
        }.toString()

    }.fold(
        onSuccess = { content -> ToolExecutionResult(callId = call.id, content = content) },
        onFailure = { error ->
            ToolExecutionResult(
                callId = call.id,
                content = JSONObject()
                    .put("error", error.message ?: error::class.java.simpleName)
                    .toString(),
                isError = true,
            )
        },
    )

    private suspend fun executeCommand(
        context: Context,
        args: JSONObject,
        onProgress: (suspend (String) -> Unit)?,
    ): JSONObject {
        val result = CommandTool.execute(
            context = context,
            command = args.getString("command"),
            cwd = args.optString("cwd").takeIf { it.isNotBlank() },
            timeoutSeconds = args.optLong("timeout_seconds", 30),
            onProgress = onProgress,
        )

        return JSONObject()
            .put("exit_code", result.exitCode)
            .put("output", result.output)
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
}
