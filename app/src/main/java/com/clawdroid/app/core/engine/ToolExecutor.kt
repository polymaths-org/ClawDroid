package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.core.tools.CommandTool
import com.clawdroid.app.data.api.CompletedToolCall
import org.json.JSONObject

data class ToolExecutionResult(
    val callId: String,
    val content: String,
    val isError: Boolean = false,
)

object ToolExecutor {
    suspend fun execute(context: Context, call: CompletedToolCall): ToolExecutionResult = when (call.name) {
        "execute_command" -> executeCommand(context, call)
        else -> ToolExecutionResult(
            callId = call.id,
            content = "Unsupported tool: ${call.name}",
            isError = true,
        )
    }

    private suspend fun executeCommand(context: Context, call: CompletedToolCall): ToolExecutionResult = runCatching {
        val args = JSONObject(call.arguments)
        val result = CommandTool.execute(
            context = context,
            command = args.getString("command"),
            cwd = args.optString("cwd").takeIf { it.isNotBlank() },
            timeoutSeconds = args.optLong("timeout_seconds", 30),
        )

        JSONObject()
            .put("exit_code", result.exitCode)
            .put("output", result.output)
            .toString()
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
}
