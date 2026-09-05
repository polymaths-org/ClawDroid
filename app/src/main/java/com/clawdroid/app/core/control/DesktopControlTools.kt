package com.clawdroid.app.core.control

import android.content.Context
import com.clawdroid.app.core.bootstrap.SharedFolderManager
import com.clawdroid.app.core.interpole.FileTransferClient
import com.clawdroid.app.core.interpole.InterpoleConfigRepository
import com.clawdroid.app.core.tools.InterpoleTools
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Desktop control through the INTERPOLE bridge (see core.interpole): mouse,
 * clicks, drag, scroll, keys and screenshots on the paired desktop —
 * Linux (Hyprland / Wayland / X11), Debian and Windows.
 *
 * Transport: per-OS shell commands are executed by the paired desktop daemon
 * via signed RPC ([InterpoleTools.execute]); screenshots come back through
 * the desktop file server ([FileTransferClient.pullFile]) and are saved into
 * Documents/ClawDroid/Output so the user gets visual proof.
 *
 * Gating: everything no-ops unless the bridge is enabled and paired
 * ([DesktopControlConfig]). Hyprland easy-mode picks hyprctl/ydotool/grim
 * command shapes for the Arch Hyprland demo.
 */
object DesktopControlTools {

    enum class OsTarget {
        AUTO,
        HYPRLAND,
        WAYLAND,
        X11,
        DEBIAN,
        WINDOWS,
    }

    fun resolveTarget(explicit: String?): OsTarget {
        val parsed = runCatching {
            if (explicit.isNullOrBlank()) null else OsTarget.valueOf(explicit.uppercase())
        }.getOrNull()
        if (parsed != null && parsed != OsTarget.AUTO) return parsed
        val pref = runCatching {
            OsTarget.valueOf(
                com.clawdroid.app.core.config.AppConfigManager.desktopOsTarget.uppercase(),
            )
        }.getOrDefault(OsTarget.AUTO)
        if (pref != OsTarget.AUTO) return pref
        return if (DesktopControlConfig.isHyprlandEasy()) OsTarget.HYPRLAND else OsTarget.WAYLAND
    }

    // ── Public tool entry points ────────────────────────────────────────────

    suspend fun mouseMove(context: Context, x: Int, y: Int, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val cmd = moveCmd(t, x, y)
            execOk(context, cmd, "moved", t).put("x", x).put("y", y)
        }
    }

    suspend fun leftClick(context: Context, x: Int?, y: Int?, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val cmd = if (x != null && y != null) {
                moveCmd(t, x, y) + " && " + clickCmd(t, ClickButton.LEFT)
            } else {
                clickCmd(t, ClickButton.LEFT)
            }
            execOk(context, cmd, "clicked", t).apply {
                if (x != null && y != null) {
                    put("x", x).put("y", y)
                }
                put("button", "left")
            }
        }
    }

    suspend fun rightClick(context: Context, x: Int?, y: Int?, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val cmd = if (x != null && y != null) {
                moveCmd(t, x, y) + " && " + clickCmd(t, ClickButton.RIGHT)
            } else {
                clickCmd(t, ClickButton.RIGHT)
            }
            execOk(context, cmd, "right_clicked", t).apply {
                if (x != null && y != null) {
                    put("x", x).put("y", y)
                }
                put("button", "right")
            }
        }
    }

    suspend fun doubleClick(context: Context, x: Int?, y: Int?, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val cmd = if (x != null && y != null) {
                moveCmd(t, x, y) + " && " + doubleCmd(t)
            } else {
                doubleCmd(t)
            }
            execOk(context, cmd, "double_clicked", t).apply {
                if (x != null && y != null) {
                    put("x", x).put("y", y)
                }
            }
        }
    }

    suspend fun drag(
        context: Context,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        os: String?,
    ): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            execOk(context, dragCmd(t, x1, y1, x2, y2), "dragged", t)
                .put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
        }
    }

    suspend fun scroll(
        context: Context,
        direction: String,
        amount: Int,
        os: String?,
    ): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val bounded = amount.coerceIn(1, 20)
            execOk(context, scrollCmd(t, direction.lowercase(), bounded), "scrolled", t)
                .put("direction", direction).put("amount", bounded)
        }
    }

    suspend fun keyPress(context: Context, key: String, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            execKey(context, t, key)
        }
    }

    suspend fun typeText(context: Context, text: String, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            execOk(context, typeCmd(t, text), "typed", t).put("chars", text.length)
        }
    }

    suspend fun openApp(context: Context, app: String, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            execOk(context, openCmd(t, app), "launched", t).put("app", app)
        }
    }

    suspend fun screenshot(context: Context, os: String?): JSONObject = runTool {
        guard() ?: run {
            val t = resolveTarget(os)
            val stamp = System.currentTimeMillis()
            val localPath = fetchScreenshot(context, os, stamp)
                ?: return@run errorResult(
                    "capture_failed",
                    "Desktop screenshot failed or could not be pulled back. " +
                        "Check the desktop has grim/scrot and the download folder is trusted.",
                )
            JSONObject()
                .put("success", true)
                .put("action", "screenshot")
                .put("path", localPath)
                .put("os", t.name.lowercase())
        }
    }

    /**
     * Smart batch: run many desktop actions in ONE tool call, retry a failing step,
     * and capture before/after screenshots when [verify] is true so the user gets
     * visual proof. Screenshot paths are returned (and rendered as chat previews).
     */
    suspend fun smartAction(
        context: Context,
        actions: JSONArray,
        verify: Boolean,
        retries: Int,
        os: String?,
    ): JSONObject = runTool {
        guard() ?: run {
            val before = if (verify) {
                screenshot(context, os).optString("path").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val results = JSONArray()
            val boundedRetries = retries.coerceIn(0, 3)
            for (i in 0 until actions.length()) {
                val action = actions.optJSONObject(i)
                    ?: return@run errorResult("invalid_action", "Action at index $i must be an object")
                val name = action.optString("action").ifBlank { action.optString("type") }
                var attempt = 0
                var stepResult: JSONObject
                while (true) {
                    stepResult = runSingleAction(context, name, action, os)
                    if (stepResult.optBoolean("success") || attempt >= boundedRetries) break
                    attempt++
                    delay(400)
                }
                results.put(
                    JSONObject()
                        .put("index", i)
                        .put("action", name)
                        .put("attempts", attempt + 1)
                        .put("result", stepResult),
                )
                if (!stepResult.optBoolean("success")) {
                    return@run JSONObject()
                        .put("success", false)
                        .put("stopped_at", i)
                        .put("results", results)
                        .apply { before?.let { put("before_path", it) } }
                }
                delay(150)
            }
            val after = if (verify) {
                screenshot(context, os).optString("path").takeIf { it.isNotBlank() }
            } else {
                null
            }
            JSONObject()
                .put("success", true)
                .put("count", actions.length())
                .put("results", results)
                .apply {
                    before?.let { put("before_path", it) }
                    after?.let { put("after_path", it) }
                }
        }
    }

    // ── Single-action dispatcher (shared by smartAction) ────────────────────

    private suspend fun runSingleAction(
        context: Context,
        name: String,
        action: JSONObject,
        os: String?,
    ): JSONObject = when (name) {
        "mouse_move" -> mouseMove(context, action.getInt("x"), action.getInt("y"), os)
        "left_click" -> leftClick(
            context,
            action.optIntOrNull("x"),
            action.optIntOrNull("y"),
            os,
        )
        "right_click" -> rightClick(
            context,
            action.optIntOrNull("x"),
            action.optIntOrNull("y"),
            os,
        )
        "double_click" -> doubleClick(
            context,
            action.optIntOrNull("x"),
            action.optIntOrNull("y"),
            os,
        )
        "drag" -> drag(
            context,
            action.getInt("x1"),
            action.getInt("y1"),
            action.getInt("x2"),
            action.getInt("y2"),
            os,
        )
        "scroll" -> scroll(
            context,
            action.optString("direction", "down"),
            action.optInt("amount", 3),
            os,
        )
        "key_press" -> keyPress(context, action.getString("key"), os)
        "type_text" -> typeText(context, action.getString("text"), os)
        "open_app" -> openApp(context, action.getString("app"), os)
        "screenshot" -> screenshot(context, os)
        "wait" -> {
            val ms = action.optInt("ms", 500).coerceIn(0, 10_000)
            delay(ms.toLong())
            JSONObject().put("success", true).put("action", "wait").put("waited_ms", ms)
        }
        else -> errorResult("unsupported_batch_action", "Unsupported desktop action '$name'")
    }

    // ── Per-OS command builders ─────────────────────────────────────────────

    private enum class ClickButton { LEFT, RIGHT }

    private fun moveCmd(t: OsTarget, x: Int, y: Int): String = when (t) {
        OsTarget.X11, OsTarget.DEBIAN -> "xdotool mousemove -- $x $y"
        OsTarget.WINDOWS -> powershell(
            "Add-Type -AssemblyName System.Windows.Forms; " +
                "[System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point($x, $y)",
        )
        else -> "ydotool mousemove -- $x $y"
    }

    private fun clickCmd(t: OsTarget, button: ClickButton): String = when (t) {
        OsTarget.X11, OsTarget.DEBIAN ->
            if (button == ClickButton.LEFT) "xdotool click 1" else "xdotool click 3"
        OsTarget.WINDOWS -> {
            val down = if (button == ClickButton.LEFT) "0x0002" else "0x0008"
            val up = if (button == ClickButton.LEFT) "0x0004" else "0x0010"
            powershell(
                "\$c = '[DllImport(''user32.dll'')]public static extern void mouse_event(int f,int x,int y,int d,int e);';" +
                    "Add-Type -MemberDefinition \$c -Name Win -NamespaceTmp;" +
                    "[Tmp.Win]::mouse_event($down,0,0,0,0);" +
                    "[Tmp.Win]::mouse_event($up,0,0,0,0)",
            )
        }
        else -> if (button == ClickButton.LEFT) "ydotool click 0xC0" else "ydotool click 0xC1"
    }

    private fun doubleCmd(t: OsTarget): String {
        val single = clickCmd(t, ClickButton.LEFT)
        return when (t) {
            OsTarget.X11, OsTarget.DEBIAN -> "xdotool click --repeat 2 --delay 120 1"
            else -> "$single && sleep 0.12 && $single"
        }
    }

    private fun dragCmd(t: OsTarget, x1: Int, y1: Int, x2: Int, y2: Int): String = when (t) {
        OsTarget.X11, OsTarget.DEBIAN ->
            "xdotool mousemove $x1 $y1 mousedown 1 mousemove $x2 $y2 mouseup 1"
        OsTarget.WINDOWS -> powershell(
            "Add-Type -AssemblyName System.Windows.Forms;" +
                "[System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point($x1, $y1)",
        )
        else ->
            "ydotool mousemove -- $x1 $y1 && ydotool mousedown 0xC0 && " +
                "ydotool mousemove -- $x2 $y2 && ydotool mouseup 0xC0"
    }

    private fun scrollCmd(t: OsTarget, direction: String, amount: Int): String = when (t) {
        OsTarget.X11, OsTarget.DEBIAN -> {
            val btn = if (direction == "up" || direction == "left") "4" else "5"
            "xdotool click --repeat $amount --delay 60 $btn"
        }
        OsTarget.WINDOWS -> {
            val delta = (if (direction == "up" || direction == "left") 120 else -120) * amount
            powershell(
                "\$c='[DllImport(''user32.dll'')]public static extern void mouse_event(int f,int x,int y,int d,int e);';" +
                    "Add-Type -MemberDefinition \$c -Name Wh -NamespaceTmp;" +
                    "[Tmp.Wh]::mouse_event(0x0800,0,0," + delta + ",0)",
            )
        }
        else -> {
            // Wayland has no universal scroll injection: page/arrow keys via ydotool.
            val code = when (direction) {
                "up" -> YDOTOl_KEYCODES["Page_Up"]
                "down" -> YDOTOl_KEYCODES["Page_Down"]
                "left" -> YDOTOl_KEYCODES["Left"]
                else -> YDOTOl_KEYCODES["Right"]
            } ?: 109
            (1..amount).joinToString(" && ") { "ydotool key $code:1 $code:0" }
        }
    }

    private suspend fun execKey(context: Context, t: OsTarget, key: String): JSONObject {
        val parts = key.split("+").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return errorResult("invalid_key", "Key must not be blank")
        val command = when (t) {
            OsTarget.X11, OsTarget.DEBIAN ->
                "xdotool key ${parts.joinToString("+") { xdotoolKey(it) }}"
            OsTarget.WINDOWS ->
                powershell(
                    "[System.Windows.Forms.SendKeys]::SendWait('${parts.joinToString("") { sendKeysToken(it) }}')",
                )
            else -> {
                val codes = parts.map { name ->
                    YDOTOl_KEYCODES[name] ?: YDOTOl_KEYCODES[name.lowercase()] ?: return errorResult(
                        "unsupported_key",
                        "No ydotool keycode for '$name' on Wayland/Hyprland",
                    )
                }
                val down = codes.joinToString(" ") { "$it:1" }
                val up = codes.asReversed().joinToString(" ") { "$it:0" }
                "ydotool key $down $up"
            }
        }
        return execOk(context, command, "key_pressed", t).put("key", key)
    }

    private fun typeCmd(t: OsTarget, text: String): String {
        val q = shQuote(text)
        return when (t) {
            OsTarget.X11, OsTarget.DEBIAN -> "xdotool type --delay 12 -- $q"
            OsTarget.WINDOWS -> powershell(
                "Add-Type -AssemblyName System.Windows.Forms;" +
                    "[System.Windows.Forms.SendKeys]::SendWait($q)",
            )
            else -> "wtype -- $q || ydotool type -- $q"
        }
    }

    private fun openCmd(t: OsTarget, app: String): String = when (t) {
        OsTarget.HYPRLAND -> "hyprctl dispatch exec -- ${shQuote(app)}"
        OsTarget.WINDOWS -> powershell("Start-Process ${shQuote(app)}")
        else -> "nohup $app >/dev/null 2>&1 & echo started"
    }

    private fun screenshotCmd(t: OsTarget, out: String): String = when (t) {
        OsTarget.HYPRLAND, OsTarget.WAYLAND -> "grim -- " + dq(out) + " && echo " + dq(out)
        OsTarget.X11, OsTarget.DEBIAN ->
            "{ grim -- " + dq(out) + " || scrot -- " + dq(out) + " || import -window root " + dq(out) + "; } && echo " + dq(out)
        OsTarget.WINDOWS -> powershell(
            "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;" +
                "\$b=New-Object System.Drawing.Rectangle([System.Windows.Forms.Screen]::PrimaryScreen.Bounds);" +
                "\$bmp=New-Object System.Drawing.Bitmap(\$b.Width,\$b.Height);" +
                "[System.Drawing.Graphics]::FromImage(\$bmp).CopyFromScreen(\$b.Location,[System.Drawing.Point]::Empty,\$b.Size);" +
                "\$bmp.Save(" + psq(out) + ");" + psq(out),
        )
        OsTarget.AUTO -> "grim -- " + dq(out) + " && echo " + dq(out)
    }

    // ── Transport + execution (paired-desktop RPC) ──────────────────────────

    private data class ExecOutcome(val exitCode: Int, val output: String)

    private suspend fun runShell(context: Context, command: String, timeoutSeconds: Int): ExecOutcome {
        val raw = InterpoleTools.execute(
            command = command,
            cwd = null,
            timeoutSeconds = timeoutSeconds.coerceIn(1, 300),
            maxOutputLines = null,
            approvalId = null,
        )
        return runCatching {
            val json = JSONObject(raw)
            val ok = json.optBoolean("ok", false)
            val output = json.optString("output").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: raw
            ExecOutcome(
                exitCode = json.optInt("exit_code", if (ok) 0 else 1),
                output = output,
            )
        }.getOrElse { ExecOutcome(exitCode = 1, output = raw.take(500)) }
    }

    private suspend fun execOk(
        context: Context,
        command: String,
        action: String,
        target: OsTarget,
    ): JSONObject {
        val result = runShell(context, command, 30)
        if (result.exitCode != 0) {
            return errorResult(
                "desktop_action_failed",
                "Exit ${result.exitCode}: ${result.output.take(300)}",
            )
        }
        return JSONObject()
            .put("success", true)
            .put("action", action)
            .put("os", target.name.lowercase())
    }

    /**
     * Capture on the desktop into its download folder, pull the PNG back over
     * the file server, and stash it in Output for chat previews.
     */
    private suspend fun fetchScreenshot(context: Context, os: String?, stamp: Long): String? {
        val downloadDir = runCatching {
            InterpoleConfigRepository(context).getConfig().downloadPath
        }.getOrDefault("/tmp").trimEnd('/').takeIf { it.isNotBlank() } ?: "/tmp"
        val remotePath = "$downloadDir/interpole-$stamp.png"
        val shot = runShell(context, screenshotCmd(resolveTarget(os), remotePath), 60)
        if (shot.exitCode != 0) return null
        val destDir = runCatching { SharedFolderManager.ensureSharedFolders() }
            .map { File(it, "Output") }
            .getOrElse { File(context.cacheDir, "interpole") }
        runCatching { if (!destDir.exists()) destDir.mkdirs() }
        val dest = File(destDir, "interpole-$stamp.png")
        return runCatching {
            val ok = FileTransferClient(context).pullFile(remotePath, dest.absolutePath)
            if (!ok) return null
            dest.takeIf { it.exists() && it.length() > 0 }?.absolutePath
        }.getOrNull()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun guard(): JSONObject? {
        val err = DesktopControlConfig.guardOrError() ?: return null
        val code = err.substringBefore(":")
        return errorResult(code, err)
    }

    private fun errorResult(error: String, message: String): JSONObject = JSONObject()
        .put("success", false)
        .put("error", error)
        .put("message", message)

    private suspend inline fun runTool(block: () -> JSONObject): JSONObject = try {
        block()
    } catch (error: Exception) {
        errorResult(error::class.java.simpleName, error.message ?: "Unknown error")
    }

    private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun dq(s: String): String = '"' + s + '"'

    private fun psq(s: String): String = "'" + s.replace("'", "''") + "'"

    private fun powershell(script: String): String =
        "powershell -NoProfile -Command " + dq(script.replace("\"", "`\""))

    private fun xdotoolKey(name: String): String = when (name.lowercase()) {
        "super", "win", "meta" -> "Super_L"
        "enter", "return" -> "Return"
        "esc", "escape" -> "Escape"
        "pgup", "pageup", "page_up" -> "Page_Up"
        "pgdn", "pagedown", "page_down" -> "Page_Down"
        "up", "down", "left", "right", "tab", "space", "delete", "home", "end" ->
            name.lowercase().replaceFirstChar { it.titlecase() }
        else -> name
    }

    private fun sendKeysToken(name: String): String = when (name.lowercase()) {
        "enter", "return" -> "{ENTER}"
        "tab" -> "{TAB}"
        "escape", "esc" -> "{ESC}"
        "backspace" -> "{BACKSPACE}"
        "delete" -> "{DELETE}"
        "up" -> "{UP}"
        "down" -> "{DOWN}"
        "left" -> "{LEFT}"
        "right" -> "{RIGHT}"
        "home" -> "{HOME}"
        "end" -> "{END}"
        "page_up", "pgup" -> "{PGUP}"
        "page_down", "pgdn" -> "{PGDN}"
        else -> name
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) {
            runCatching { getInt(name) }.getOrNull()
                ?: runCatching { getDouble(name).toInt() }.getOrNull()
        } else {
            null
        }

    /** Linux evdev keycodes for ydotool on Wayland/Hyprland. */
    private val YDOTOl_KEYCODES: Map<String, Int> = mapOf(
            "Escape" to 1, "1" to 2, "2" to 3, "3" to 4, "4" to 5, "5" to 6,
            "6" to 7, "7" to 8, "8" to 9, "9" to 10, "0" to 11,
            "Backspace" to 14, "Tab" to 15,
            "q" to 16, "w" to 17, "e" to 18, "r" to 19, "t" to 20, "y" to 21,
            "u" to 22, "i" to 23, "o" to 24, "p" to 25,
            "Enter" to 28, "ctrl" to 29,
            "a" to 30, "s" to 31, "d" to 32, "f" to 33, "g" to 34, "h" to 35,
            "j" to 36, "k" to 37, "l" to 38,
            "shift" to 42,
            "z" to 44, "x" to 45, "c" to 46, "v" to 47, "b" to 48, "n" to 49, "m" to 50,
            "alt" to 56, "space" to 57, "super" to 125,
            "F1" to 59, "F2" to 60, "F3" to 61, "F4" to 62, "F5" to 63, "F6" to 64,
            "F7" to 65, "F8" to 66, "F9" to 67, "F10" to 68, "F11" to 69, "F12" to 70,
            "Up" to 103, "Page_Up" to 104, "Left" to 105, "Right" to 106,
            "End" to 107, "Down" to 108, "Page_Down" to 109, "Delete" to 111,
        )
}
