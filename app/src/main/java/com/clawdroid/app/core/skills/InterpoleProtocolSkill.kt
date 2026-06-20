package com.clawdroid.app.core.skills

import com.clawdroid.app.core.bootstrap.LinuxEnvironment
import java.io.File

object InterpoleProtocolSkill {
    const val NAME = "interpole_protocol"
    const val DESCRIPTION = "Secure INTERPOLE desktop bridge protocol for using a paired desktop through stable tools only."

    val SYSTEM_PROMPT: String = """
        # INTERPOLE Protocol Skill

        INTERPOLE is a paired desktop bridge. Use it only through the configured `interpole_*` tools.

        Security:
        - Never scan, brute-force, curl, browse, or guess INTERPOLE endpoints during normal work.
        - Never invent host, port, device_id, device_token, nonce, signature, approval_id, or trusted folders. Pairing and discovery live only in Settings > INTERPOLE.
        - Never reveal, summarize, log, or store the device token.
        - Keep desktop paths inside trusted folders. On `untrusted_path`, stop and tell the user which folder to trust.
        - On `approval_required`, surface the returned `approval_id`, wait for the user, then retry the SAME call with that `approval_id`. Never bypass `execute_disabled`, `unauthorized`, or `forbidden_remote`.

        Context + speed (prefer these):
        - Call `interpole_status` once when desktop availability or permissions matter; it returns trust mode, execute permission, and trusted folders, so do not guess them. Cache it for the turn.
        - Use `interpole_batch` for 2+ related desktop operations: it runs them in one signed round-trip and one activity step.
        - For reads, pass `start_line`/`end_line` to fetch only the range you need (`total_lines` tells you the size).
        - `interpole_execute` output is already summarized (head + tail with line/byte counts); only raise `max_output_lines` when you truly need more. Prefer non-interactive flags.

        Fallback:
        - Every `interpole_*` result carries `ok` plus, on failure, an `error` and `advice`. Read `advice`.
        - If INTERPOLE is unpaired, disabled, offline, or unauthorized, switch to Android sandbox tools when the task allows and clearly report the fallback. Retry a transient network failure at most once.
    """.trimIndent()

    val MARKDOWN: String = """
        # INTERPOLE Protocol Skill

        Purpose: let the agent use a paired desktop quickly and securely without discovering ad hoc endpoints or inventing access paths.

        ## Capability Boundary

        INTERPOLE is not general network access. It is a named desktop capability backed by Settings-stored connection data and HMAC credentials.

        The agent may use INTERPOLE only through these stable tools:

        - `interpole_status` - paired desktop state and current permissions
        - `interpole_notify` - desktop notification to the user
        - `interpole_list_dir` - list a trusted folder
        - `interpole_read_file` - read a file, optionally a line range
        - `interpole_write_file` - create or overwrite a file
        - `interpole_execute` - run a shell command (summarized output)
        - `interpole_batch` - run several of the above in one signed round-trip

        The agent must not call `curl`, `wget`, browser tools, raw sockets, shell scripts, or guessed HTTP paths to reach the desktop bridge.

        ## Connection Source

        The configured host, port, connection type, device id, and device token come from Settings > INTERPOLE.

        The agent must never request or supply host, port, token, nonce, signature, or device id as task parameters. Pairing and discovery are user-facing Settings flows.

        ## Normal Flow

        1. Use `interpole_status` when desktop availability or permissions affect the task. It returns trust mode, whether execute is allowed, and trusted folders, so you never have to guess them.
        2. Use the narrowest tool for the operation.
        3. Prefer `interpole_list_dir` and `interpole_read_file` before write or execute.
        4. When you need two or more related desktop operations, send one `interpole_batch` instead of several separate calls.
        5. Keep paths inside daemon trusted folders.
        6. For `approval_required`, show the approval id and wait for user approval before retrying the same call with that exact id.
        7. If unavailable, fall back to Android sandbox tools only when the task can still be completed safely.

        ## Result Shape

        Every `interpole_*` result is JSON with an `ok` field and `environment: desktop`.

        - On success, the daemon's fields are returned as-is (for example `stdout`, `stderr`, `exit_code`, `stdout_total_lines` for execute; `content`, `total_lines` for ranged reads; `results` for batch).
        - On failure, the result has `ok: false`, an `error` code, and a human `advice` string. Approval failures also include `approval_id`. Read `advice` and act on it.

        ## Security Rules

        - Never invent secrets or identifiers.
        - Never expose the device token.
        - Never probe arbitrary LAN, Tailscale, localhost, or private IP ranges from the agent loop.
        - Never bypass `execute_disabled`, `untrusted_path`, `unauthorized`, or `approval_required`.
        - Treat the desktop daemon as policy owner for trusted folders, approvals, and command execution.
        - Keep user-visible activity clear when switching between Android and desktop execution.

        ## Performance Rules

        - Cache one successful `interpole_status` result for the current turn.
        - Use `interpole_batch` for 2+ related desktop operations: one signed round-trip and one activity step instead of many.
        - Read with `start_line`/`end_line` so only the needed window leaves the desktop; use `total_lines` to decide what to fetch next.
        - `interpole_execute` already returns a head + tail summary with line/byte counts. Only raise `max_output_lines` when you genuinely need more, and prefer non-interactive flags (`-y`, `-q`).
        - Avoid command loops when one command can produce the needed result.
        - Retry a transient network failure at most once, then report the status.

        ## Error Handling

        - `unpaired` or `disabled`: tell the user to connect in Settings > INTERPOLE.
        - `offline` or network timeout: tell the user to start `interpole` or `interpole auth local` / `interpole auth tailscale` on desktop.
        - `unauthorized`: tell the user to re-pair the device.
        - `invalid_or_expired_pin`: tell the user to restart pairing and enter the new desktop code.
        - `approval_required`: surface the approval id and wait.
        - `untrusted_path`: ask the user to add the folder to desktop trusted folders.
        - `execute_disabled`: ask the user to enable desktop execute in INTERPOLE settings if they want commands.
    """.trimIndent()

    fun installInto(env: LinuxEnvironment) {
        val skillsDir = File(env.home, "skills")
        val file = File(skillsDir, "$NAME.md")
        if (file.exists()) return
        skillsDir.mkdirs()
        file.writeText(MARKDOWN)
    }
}
