package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.terminal.ProcessManagerProvider
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

object StartProcessTool {
    suspend fun execute(context: Context, command: String, cwd: String?, timeoutSeconds: Long): JSONObject {
        val result = ProcessManagerProvider.get(context).startProcess(
            command = command,
            cwd = cwd,
            timeout = timeoutSeconds.coerceIn(1, 10_800).seconds,
        )
        return JSONObject()
            .put("process_id", result.processId)
            .put("initial_output", result.initialOutput)
    }
}

object CheckProcessTool {
    suspend fun execute(context: Context, processId: String): JSONObject {
        val status = ProcessManagerProvider.get(context).checkProcess(processId)
        return status.toJson()
    }
}

object SendInputTool {
    suspend fun execute(context: Context, processId: String, input: String): JSONObject {
        val status = ProcessManagerProvider.get(context).sendInput(processId, input)
        return status.toJson()
            .put("sent", input)
    }
}

object KillProcessTool {
    suspend fun execute(context: Context, processId: String): JSONObject {
        val status = ProcessManagerProvider.get(context).killProcess(processId)
        return status.toJson()
    }
}

object ListProcessesTool {
    suspend fun execute(context: Context): JSONObject {
        val statuses = ProcessManagerProvider.get(context).listProcesses()
        return JSONObject().put(
            "processes",
            JSONArray().apply { statuses.forEach { put(it.toJson()) } },
        )
    }
}

private fun com.clawdroid.app.core.terminal.ProcessStatus.toJson(): JSONObject = JSONObject()
    .put("process_id", processId)
    .put("command", command)
    .put("cwd", cwd)
    .put("state", state.name)
    .put("exit_code", exitCode)
    .put("recent_output", recentOutput)
    .put("waiting_for_input", waitingForInput)
    .put("prompt", prompt)
    .put("started_at", startedAt)
