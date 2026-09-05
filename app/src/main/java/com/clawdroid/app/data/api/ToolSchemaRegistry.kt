package com.clawdroid.app.data.api

import org.json.JSONArray
import org.json.JSONObject

object ToolSchemaRegistry {
    fun allTools(): JSONArray {
        val array = JSONArray()

        array.put(tool("execute_command", "Run a short shell command and wait for completion.") {
            putString("command", "Command to run via bash -c.")
            putString("cwd", "Working directory inside the sandbox.")
            putInteger("timeout_seconds", "Maximum time to wait before returning.")
            required("command", "cwd")
        })
        array.put(tool("start_process", "Start a long-running shell command and return a process id.") {
            putString("command", "Command to run via bash -c.")
            putString("cwd", "Working directory inside the sandbox.")
            putInteger("timeout_seconds", "Maximum process lifetime.")
            required("command", "cwd")
        })
        array.put(tool("check_process", "Check status and recent output for a running process.") {
            putString("process_id", "Process id returned by start_process.")
            required("process_id")
        })
        array.put(tool("send_input", "Send text or special key tokens to a running process.") {
            putString("process_id", "Process id returned by start_process.")
            putString("input", "Input text; supports tokens like [ENTER] and [CTRL+C].")
            required("process_id", "input")
        })
        array.put(tool("kill_process", "Kill a running process and its children.") {
            putString("process_id", "Process id returned by start_process.")
            required("process_id")
        })
        array.put(tool("list_processes", "List active and recently completed processes.") {
            required()
        })
        array.put(tool("read_file", "Read file contents, optionally with a line range.") {
            putString("path", "Absolute path or sandbox-relative path to read.")
            putInteger("start_line", "Optional first 1-based line to read.")
            putInteger("end_line", "Optional last 1-based line to read.")
            required("path")
        })
        array.put(tool("write_file", "Create or overwrite a file.") {
            putString("path", "Absolute path or sandbox-relative path to write.")
            putString("content", "Full file content.")
            required("path", "content")
        })
        array.put(tool("edit_file", "Apply a targeted search and replace edit to a file.") {
            putString("path", "Absolute path or sandbox-relative path to edit.")
            putString("search", "Exact text to replace.")
            putString("replace", "Replacement text.")
            required("path", "search", "replace")
        })
        array.put(tool("list_directory", "List directory contents with sizes and types.") {
            putString("path", "Absolute path or sandbox-relative path to list.")
            required("path")
        })
        array.put(tool("browse_web", "Navigate to a URL and extract page content.") {
            putString("url", "URL to browse.")
            required("url")
        })
        array.put(tool("web_search", "Search the web and return relevant results.") {
            putString("query", "Search query.")
            required("query")
        })
        array.put(tool("send_notification", "Send a concise user notification.") {
            putString("title", "Notification title.")
            putString("body", "Notification body.")
            required("title", "body")
        })

        val isGoogleActive = com.clawdroid.app.core.service.GoogleAuthManager.isGoogleConnected &&
                com.clawdroid.app.core.config.AppConfigManager.googleConnectorEnabled

        if (isGoogleActive) {
            if (com.clawdroid.app.core.config.AppConfigManager.googleGmailEnabled) {
                array.put(tool("gmail_list_messages", "List or search the user's Gmail messages.") {
                    putString("query", "Search query (same format as Gmail search bar, optional).")
                    putInteger("max_results", "Maximum number of results to fetch (default: 10, optional).")
                })
                array.put(tool("gmail_get_message", "Retrieve detail and body of a specific email message.") {
                    putString("id", "The unique email message ID.")
                    required("id")
                })
                array.put(tool("gmail_send_message", "Send an email message to a recipient.") {
                    putString("to", "Recipient email address.")
                    putString("subject", "Email subject.")
                    putString("body", "Email body content.")
                    required("to", "subject", "body")
                })
                array.put(tool("gmail_create_draft", "Create a draft email message.") {
                    putString("to", "Recipient email address.")
                    putString("subject", "Email subject.")
                    putString("body", "Email body content.")
                    required("to", "subject", "body")
                })
            }
            if (com.clawdroid.app.core.config.AppConfigManager.googleCalendarEnabled) {
                array.put(tool("calendar_list_events", "List upcoming calendar events.") {
                    putString("time_min", "Lower bound (exclusive) for an event's start time in ISO-8601 format (optional).")
                    putString("time_max", "Upper bound (exclusive) for an event's end time in ISO-8601 format (optional).")
                    putInteger("max_results", "Maximum number of events to return (default: 15, optional).")
                })
                array.put(tool("calendar_create_event", "Create a new event on the primary calendar.") {
                    putString("summary", "Title of the calendar event.")
                    putString("description", "Description of the calendar event (optional).")
                    putString("start_time", "Start time in ISO-8601 format (e.g. 2026-06-15T15:00:00+05:30).")
                    putString("end_time", "End time in ISO-8601 format (e.g. 2026-06-15T16:00:00+05:30).")
                    required("summary", "start_time", "end_time")
                })
            }
            // Google Drive & Docs Tools
            array.put(tool("google_drive_create_file", "Upload or create a file in Google Drive.") {
                putString("name", "Name of the file.")
                putString("mimeType", "MIME type (e.g. text/plain, application/pdf, image/png).")
                putString("content", "Raw text content of the file.")
                required("name", "mimeType", "content")
            })
            array.put(tool("google_drive_search_files", "Search for files in Google Drive.") {
                putString("query", "Search term or file name query.")
                required("query")
            })
            array.put(tool("google_docs_write_doc", "Create a new Google Doc and write content to it.") {
                putString("title", "Document title.")
                putString("body", "Document content body.")
                required("title", "body")
            })
        }

        // GitHub Tools
        val isGithubActive = com.clawdroid.app.core.service.GithubAuthManager.isConnected &&
                com.clawdroid.app.core.config.AppConfigManager.githubConnectorEnabled
        if (isGithubActive) {
            array.put(tool("github_list_repos", "List repositories for the authenticated GitHub user.") {
                // No parameters required
            })
            array.put(tool("github_create_issue", "Create an issue in a GitHub repository.") {
                putString("repo", "Full repository name, formatted as 'owner/repo' (e.g. 'octocat/Hello-World').")
                putString("title", "Title of the issue.")
                putString("body", "Body content of the issue.")
                required("repo", "title", "body")
            })
            array.put(tool("github_create_pr", "Create a Pull Request in a GitHub repository.") {
                putString("repo", "Full repository name, formatted as 'owner/repo'.")
                putString("title", "Title of the Pull Request.")
                putString("head", "The name of the branch where your changes are implemented (e.g. 'my-feature-branch').")
                putString("base", "The name of the branch you want the changes pulled into (e.g. 'main' or 'master').")
                putString("body", "Description body of the Pull Request.")
                required("repo", "title", "head", "base", "body")
            })
        }

        // Notion Tools
        val isNotionActive = com.clawdroid.app.core.service.NotionAuthManager.isConnected &&
                com.clawdroid.app.core.config.AppConfigManager.notionConnectorEnabled
        if (isNotionActive) {
            array.put(tool("notion_create_page", "Create a new page under a parent page in Notion.") {
                putString("parentPageId", "The UUID of the parent page (e.g. '8b3687595b1a45749f7e8b6ee7bdf354').")
                putString("title", "Title of the new page.")
                putString("content", "Text content block to insert inside the page.")
                required("parentPageId", "title", "content")
            })
            array.put(tool("notion_append_block", "Append a paragraph block of text to an existing Notion page or block.") {
                putString("pageId", "The UUID of the page or block to append to.")
                putString("content", "Text content to append.")
                required("pageId", "content")
            })
        }

        // Spotify Tools
        val isSpotifyActive = com.clawdroid.app.core.service.SpotifyAuthManager.isConnected &&
                com.clawdroid.app.core.config.AppConfigManager.spotifyConnectorEnabled
        if (isSpotifyActive) {
            array.put(tool("spotify_playback_control", "Control media playback on Spotify (PLAY, PAUSE, NEXT, PREV).") {
                putString("action", "Playback control action: PLAY, PAUSE, NEXT, or PREV.")
                required("action")
            })
            array.put(tool("spotify_get_current_track", "Retrieve details of the currently playing track on Spotify.") {
                // No parameters required
            })
            array.put(tool("spotify_search_and_play", "Search for a track on Spotify and play it on the device.") {
                putString("query", "Search query for track name and/or artist (e.g. 'Stairway to Heaven' or 'Blinding Lights').")
                required("query")
            })
        }

        return array
    }


    private fun tool(
        name: String,
        description: String,
        parameters: SchemaBuilder.() -> Unit,
    ): JSONObject {
        val builder = SchemaBuilder().apply(parameters)
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", builder.build())
            )
    }

    private class SchemaBuilder {
        private val properties = JSONObject()
        private val required = JSONArray()

        fun putString(name: String, description: String) {
            properties.put(
                name,
                JSONObject()
                    .put("type", "string")
                    .put("description", description)
            )
        }

        fun putInteger(name: String, description: String) {
            properties.put(
                name,
                JSONObject()
                    .put("type", "integer")
                    .put("description", description)
            )
        }

        fun required(vararg names: String) {
            names.forEach { required.put(it) }
        }

        fun build(): JSONObject = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", required)
            .put("additionalProperties", false)
    }
}
