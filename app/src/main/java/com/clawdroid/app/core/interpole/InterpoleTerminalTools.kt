package com.clawdroid.app.core.interpole

import android.content.Context
import org.json.JSONObject

object InterpoleTerminalTools {
    suspend fun create(
        context: Context,
        name: String,
        cwd: String?,
        command: String?,
        shell: String?,
        cols: Int,
        rows: Int,
    ): JSONObject = InterpoleClient(context).rpc(
        action = "terminal_create",
        params = JSONObject()
            .put("name", name)
            .put("cwd", cwd ?: "")
            .put("command", command ?: "")
            .put("shell", shell ?: "/bin/bash")
            .put("cols", cols)
            .put("rows", rows),
    )

    suspend fun send(context: Context, name: String, keys: String, enter: Boolean): JSONObject =
        InterpoleClient(context).rpc(
            action = "terminal_send",
            params = JSONObject()
                .put("name", name)
                .put("keys", keys)
                .put("enter", enter),
        )

    suspend fun read(context: Context, name: String, lines: Int): JSONObject =
        InterpoleClient(context).rpc(
            action = "terminal_read",
            params = JSONObject()
                .put("name", name)
                .put("lines", lines),
        )

    suspend fun resize(context: Context, name: String, cols: Int, rows: Int): JSONObject =
        InterpoleClient(context).rpc(
            action = "terminal_resize",
            params = JSONObject()
                .put("name", name)
                .put("cols", cols)
                .put("rows", rows),
        )

    suspend fun list(context: Context): JSONObject =
        InterpoleClient(context).rpc(action = "terminal_list")

    suspend fun kill(context: Context, name: String): JSONObject =
        InterpoleClient(context).rpc(
            action = "terminal_kill",
            params = JSONObject().put("name", name),
        )
}
