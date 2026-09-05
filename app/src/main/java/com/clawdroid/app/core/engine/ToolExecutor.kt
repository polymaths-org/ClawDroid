package com.clawdroid.app.core.engine

import android.content.Context
import com.clawdroid.app.core.tools.CheckProcessTool
import com.clawdroid.app.core.tools.CommandTool
import com.clawdroid.app.core.tools.KillProcessTool
import com.clawdroid.app.core.tools.ListProcessesTool
import com.clawdroid.app.core.tools.SendInputTool
import com.clawdroid.app.core.tools.StartProcessTool
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.DefensiveJsonParser
import org.json.JSONObject

data class ToolExecutionResult(
    val callId: String,
    val content: String,
    val isError: Boolean = false,
)

object ToolExecutor {
    suspend fun execute(context: Context, call: CompletedToolCall): ToolExecutionResult = runCatching {
        val args = DefensiveJsonParser.parseObjectOrError(call.arguments).getOrThrow()
        when (call.name) {
            "execute_command" -> executeCommand(context, args)
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
            else -> error("Unsupported tool: ${call.name}")
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

    private suspend fun executeCommand(context: Context, args: JSONObject): JSONObject {
        val result = CommandTool.execute(
            context = context,
            command = args.getString("command"),
            cwd = args.optString("cwd").takeIf { it.isNotBlank() },
            timeoutSeconds = args.optLong("timeout_seconds", 30),
        )

        return JSONObject()
            .put("exit_code", result.exitCode)
            .put("output", result.output)
    }
}
