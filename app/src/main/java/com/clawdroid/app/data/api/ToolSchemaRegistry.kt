package com.clawdroid.app.data.api

import org.json.JSONArray
import org.json.JSONObject

object ToolSchemaRegistry {
    fun allTools(): JSONArray = JSONArray()
        .put(tool("execute_command", "Run a short shell command and wait for completion.") {
            putString("command", "Command to run via bash -c.")
            putString("cwd", "Working directory inside the sandbox.")
            putInteger("timeout_seconds", "Maximum time to wait before returning.")
            required("command", "cwd")
        })
        .put(tool("start_process", "Start a long-running shell command and return a process id.") {
            putString("command", "Command to run via bash -c.")
            putString("cwd", "Working directory inside the sandbox.")
            putInteger("timeout_seconds", "Maximum process lifetime.")
            required("command", "cwd")
        })
        .put(tool("check_process", "Check status and recent output for a running process.") {
            putString("process_id", "Process id returned by start_process.")
            required("process_id")
        })
        .put(tool("send_input", "Send text or special key tokens to a running process.") {
            putString("process_id", "Process id returned by start_process.")
            putString("input", "Input text; supports tokens like [ENTER] and [CTRL+C].")
            required("process_id", "input")
        })
        .put(tool("kill_process", "Kill a running process and its children.") {
            putString("process_id", "Process id returned by start_process.")
            required("process_id")
        })
        .put(tool("list_processes", "List active and recently completed processes.") {
            required()
        })
        .put(tool("read_file", "Read file contents, optionally with a line range.") {
            putString("path", "Absolute path or sandbox-relative path to read.")
            putInteger("start_line", "Optional first 1-based line to read.")
            putInteger("end_line", "Optional last 1-based line to read.")
            required("path")
        })
        .put(tool("write_file", "Create or overwrite a file.") {
            putString("path", "Absolute path or sandbox-relative path to write.")
            putString("content", "Full file content.")
            required("path", "content")
        })
        .put(tool("edit_file", "Apply a targeted search and replace edit to a file.") {
            putString("path", "Absolute path or sandbox-relative path to edit.")
            putString("search", "Exact text to replace.")
            putString("replace", "Replacement text.")
            required("path", "search", "replace")
        })
        .put(tool("list_directory", "List directory contents with sizes and types.") {
            putString("path", "Absolute path or sandbox-relative path to list.")
            required("path")
        })
        .put(tool("browse_web", "Navigate to a URL and extract page content.") {
            putString("url", "URL to browse.")
            required("url")
        })
        .put(tool("web_search", "Search the web and return relevant results.") {
            putString("query", "Search query.")
            required("query")
        })
        .put(tool("send_notification", "Send a concise user notification.") {
            putString("title", "Notification title.")
            putString("body", "Notification body.")
            required("title", "body")
        })

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
