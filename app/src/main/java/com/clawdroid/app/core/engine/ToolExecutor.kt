package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.core.assistant.overlay.AssistantOverlayCoordinator
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
import com.clawdroid.app.core.tools.GoogleDriveTools
import com.clawdroid.app.core.tools.GithubTools
import com.clawdroid.app.core.tools.NotionTools
import com.clawdroid.app.core.tools.SpotifyTools
import com.clawdroid.app.core.tools.checkAndRequestStoragePermission
import com.clawdroid.app.core.control.AndroidControlTools
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
            "google_drive_create_file", "google_drive_search_files", "google_docs_write_doc" -> {
                if (!com.clawdroid.app.core.service.GoogleAuthManager.isGoogleConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.googleConnectorEnabled) {
                    throw IllegalStateException("Google Drive/Docs tools are currently disabled or Google account is disconnected.")
                }
                when (call.name) {
                    "google_drive_create_file" -> GoogleDriveTools.createDriveFile(
                        name = args.getString("name"),
                        mimeType = args.getString("mimeType"),
                        content = args.getString("content")
                    )
                    "google_drive_search_files" -> GoogleDriveTools.searchDriveFiles(
                        query = args.getString("query")
                    )
                    "google_docs_write_doc" -> GoogleDriveTools.writeGoogleDoc(
                        title = args.getString("title"),
                        body = args.getString("body")
                    )
                    else -> error("Unreachable")
                }
            }
            "github_list_repos", "github_create_issue", "github_create_pr" -> {
                if (!com.clawdroid.app.core.service.GithubAuthManager.isConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.githubConnectorEnabled) {
                    throw IllegalStateException("GitHub tools are currently disabled or GitHub account is disconnected.")
                }
                when (call.name) {
                    "github_list_repos" -> GithubTools.listRepos()
                    "github_create_issue" -> GithubTools.createIssue(
                        repo = args.getString("repo"),
                        title = args.getString("title"),
                        body = args.getString("body")
                    )
                    "github_create_pr" -> GithubTools.createPullRequest(
                        repo = args.getString("repo"),
                        title = args.getString("title"),
                        head = args.getString("head"),
                        base = args.getString("base"),
                        body = args.getString("body")
                    )
                    else -> error("Unreachable")
                }
            }
            "notion_create_page", "notion_append_block" -> {
                if (!com.clawdroid.app.core.service.NotionAuthManager.isConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.notionConnectorEnabled) {
                    throw IllegalStateException("Notion tools are currently disabled or Notion account is disconnected.")
                }
                when (call.name) {
                    "notion_create_page" -> NotionTools.createPage(
                        parentPageId = args.getString("parentPageId"),
                        title = args.getString("title"),
                        content = args.getString("content")
                    )
                    "notion_append_block" -> NotionTools.appendBlock(
                        pageId = args.getString("pageId"),
                        content = args.getString("content")
                    )
                    else -> error("Unreachable")
                }
            }
            "spotify_playback_control", "spotify_get_current_track", "spotify_search_and_play" -> {
                if (!com.clawdroid.app.core.service.SpotifyAuthManager.isConnected ||
                    !com.clawdroid.app.core.config.AppConfigManager.spotifyConnectorEnabled) {
                    throw IllegalStateException("Spotify tools are currently disabled or Spotify account is disconnected.")
                }
                when (call.name) {
                    "spotify_playback_control" -> SpotifyTools.controlPlayback(
                        context = context,
                        action = args.getString("action")
                    )
                    "spotify_get_current_track" -> SpotifyTools.getCurrentTrack()
                    "spotify_search_and_play" -> SpotifyTools.searchAndPlay(
                        context = context,
                        query = args.getString("query")
                    )
                    else -> error("Unreachable")
                }
            }
            "get_screen" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.getScreen(context) }
            "tap" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.tap(
                args.getDouble("x").toFloat(),
                args.getDouble("y").toFloat(),
            ) }
            "tap_text" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.tapText(args.getString("label")) }
            "tap_resource_id" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.tapResourceId(args.getString("id")) }
            "long_press" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.longPress(
                args.getDouble("x").toFloat(),
                args.getDouble("y").toFloat(),
            ) }
            "swipe" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.swipe(
                x1 = args.getDouble("x1").toFloat(),
                y1 = args.getDouble("y1").toFloat(),
                x2 = args.getDouble("x2").toFloat(),
                y2 = args.getDouble("y2").toFloat(),
                durationMs = args.optInt("duration_ms", 400),
            ) }
            "scroll" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.scroll(args.getString("direction")) }
            "type_text" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.typeText(args.getString("text")) }
            "clear_text" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.clearText() }
            "press_back" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.pressBack() }
            "press_home" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.pressHome() }
            "press_recents" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.pressRecents() }
            "open_notifications" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.openNotifications() }
            "launch_app" -> withOverlayHiddenForTool(call.name) {
                val appQuery = args.optString("package_name").ifBlank { args.optString("app_name") }
                AndroidControlTools.launchApp(appQuery, context)
            }
            "get_installed_apps" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.getInstalledApps(context) }
            "screenshot" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.screenshot(context) }
            "wait" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.wait(args.optInt("ms", 500)) }
            "perform_android_actions" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.performActions(
                context = context,
                actions = args.getJSONArray("actions"),
                verify = args.optBoolean("verify", true),
            ) }
            "send_message_in_current_chat" -> withOverlayHiddenForTool(call.name) { AndroidControlTools.sendMessageInCurrentChat(
                context = context,
                text = args.getString("text"),
                count = args.optInt("count", 1),
            ) }
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

    private suspend fun withOverlayHiddenForTool(
        name: String,
        block: suspend () -> JSONObject,
    ): JSONObject {
        val settleMs = when (name) {
            "get_screen", "screenshot" -> 3_000L
            "wait" -> 150L
            else -> 350L
        }
        return AssistantOverlayCoordinator.withOverlayHiddenForExternalUi(
            reason = name,
            settleMs = settleMs,
            block = block,
        )
    }
}
