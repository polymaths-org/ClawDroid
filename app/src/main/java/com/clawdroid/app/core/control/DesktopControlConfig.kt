package com.clawdroid.app.core.control

import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.tools.InterpoleTools

/**
 * Desktop-input policy on top of the INTERPOLE bridge (see core.interpole).
 *
 * - Master switch + pairing live in Settings > INTERPOLE ([InterpoleTools.isReady]).
 * - Hyprland easy-mode ([AppConfigManager.interpoleHyprlandEnabled]) picks
 *   hyprctl + ydotool + grim command shapes for the Arch Hyprland demo.
 *   OFF = generic Wayland/X11 shapes, no hyprctl calls.
 * - Explicit per-call OS target ([AppConfigManager.desktopOsTarget]) wins.
 */
object DesktopControlConfig {

    /** True when the bridge is enabled AND fully paired. */
    fun isReady(): Boolean = InterpoleTools.isReady()

    fun isHyprlandEasy(): Boolean =
        InterpoleTools.isReady() && AppConfigManager.interpoleHyprlandEnabled

    fun describe(): String = when {
        !AppConfigManager.interpoleEnabled ->
            "Interpole is disabled. Enable it in Settings > INTERPOLE first."
        !isReady() ->
            "Interpole is not paired. Ask the user to connect in Settings > INTERPOLE."
        AppConfigManager.interpoleHyprlandEnabled ->
            "Interpole Hyprland easy-mode ON. Uses hyprctl dispatch + ydotool/wtype + grim."
        else ->
            "Interpole generic mode (Hyprland easy-mode OFF). Uses wtype/ydotool/xdotool, no hyprctl."
    }

    fun guardOrError(): String? = when {
        !AppConfigManager.interpoleEnabled ->
            "interpole_disabled: Enable Interpole in Settings > INTERPOLE first."
        !isReady() ->
            "interpole_unpaired: Ask the user to connect in Settings > INTERPOLE first."
        else -> null
    }

    /** Shell hint for the Arch Hyprland demo desktop. */
    fun archHyprlandSetupScript(): String = buildString {
        appendLine("# Interpole Arch Hyprland setup (run on desktop)")
        appendLine("sudo pacman -S --needed ydotool wtype grim slurp wl-clipboard socat")
        appendLine("systemctl --user enable --now ydotoold.service || sudo systemctl enable --now ydotoold")
        appendLine("# Verify:")
        appendLine("hyprctl version && ydotool --help | head -5 && grim -h | head -3")
    }
}
