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
