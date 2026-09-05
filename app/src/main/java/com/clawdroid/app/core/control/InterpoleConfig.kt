package com.clawdroid.app.core.control

import com.clawdroid.app.core.config.AppConfigManager

/**
 * Task 6: Interpole desktop control made easy on Hyprland, or fully disabled.
 *
 * - Master switch: [AppConfigManager.interpoleEnabled]
 * - Hyprland easy mode: [AppConfigManager.interpoleHyprlandEnabled]
 *   - ON  -> prefer `hyprctl dispatch` for focus/launch + ydotool/wtype for input,
 *            grim/slurp + wl-clipboard for screenshots on Arch Hyprland.
 *   - OFF -> generic Wayland/X11 path (wtype/ydotool/xdotool), no hyprctl.
 * - If master switch is OFF, backend is DISABLED and desktop tools must no-op
 *   with a clear error instead of attempting input.
 */
object InterpoleConfig {

    enum class Backend {
        DISABLED,
        GENERIC,
        HYPRLAND_EASY,
    }

    fun backend(): Backend {
        if (!AppConfigManager.interpoleEnabled) return Backend.DISABLED
        return if (AppConfigManager.interpoleHyprlandEnabled) {
            Backend.HYPRLAND_EASY
        } else {
            Backend.GENERIC
        }
    }

    fun isEnabled(): Boolean = AppConfigManager.interpoleEnabled

    fun isHyprlandEasy(): Boolean =
        AppConfigManager.interpoleEnabled && AppConfigManager.interpoleHyprlandEnabled

    fun describe(): String = when (backend()) {
        Backend.DISABLED -> "Interpole is disabled. Enable it in Interpole settings to control a desktop."
        Backend.GENERIC -> "Interpole generic mode (Hyprland easy-mode OFF). Uses wtype/ydotool/xdotool, no hyprctl."
        Backend.HYPRLAND_EASY -> "Interpole Hyprland easy-mode ON. Uses hyprctl dispatch + ydotool/wtype + grim for Arch Hyprland demo."
    }

    /** Shell hint for Arch Hyprland demo setup (run on the desktop, not the phone). */
    fun archHyprlandSetupScript(): String = buildString {
        appendLine("# Interpole Arch Hyprland setup (run on desktop)")
        appendLine("sudo pacman -S --needed ydotool wtype grim slurp wl-clipboard socat")
        appendLine("systemctl --user enable --now ydotoold.service || sudo systemctl enable --now ydotoold")
        appendLine("# Verify:")
        appendLine("hyprctl version && ydotool --help | head -5 && grim -h | head -3")
    }

    /** Easy-mode click command builder for the paired desktop shell. */
    fun clickCommand(x: Int, y: Int): String = when (backend()) {
        Backend.DISABLED -> "echo 'interpole disabled'"
        Backend.HYPRLAND_EASY -> "ydotool mousemove -- $x $y && ydotool click 0xC0"
        Backend.GENERIC -> "ydotool mousemove -- $x $y && ydotool click 0xC0"
    }

    fun rightClickCommand(x: Int, y: Int): String = when (backend()) {
        Backend.DISABLED -> "echo 'interpole disabled'"
        Backend.HYPRLAND_EASY -> "ydotool mousemove -- $x $y && ydotool click 0xC1"
        Backend.GENERIC -> "ydotool mousemove -- $x $y && ydotool click 0xC1"
    }

    fun screenshotCommand(output: String = "/tmp/interpole-shot.png"): String = when (backend()) {
        Backend.DISABLED -> "echo 'interpole disabled'"
        Backend.HYPRLAND_EASY -> "grim -- " + dq(output) + " && echo " + dq(output)
        Backend.GENERIC -> "grim -- " + dq(output) + " && echo " + dq(output)
    }

    private fun dq(s: String): String = '"' + s + '"'

    fun guardOrError(): String? = when (backend()) {
        Backend.DISABLED -> "interpole_disabled: Enable Interpole in Settings > Interpole first."
        else -> null
    }
}
