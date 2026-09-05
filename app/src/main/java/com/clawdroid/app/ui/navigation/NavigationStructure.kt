package com.clawdroid.app.ui.navigation

sealed class NavigationRoute(val route: String, val label: String) {
    data object Chat : NavigationRoute("chat", "Chat")
    data object Terminal : NavigationRoute("terminal", "Terminal")
    data object Settings : NavigationRoute("settings", "Settings")
    data object SettingsVoice : NavigationRoute("settings/voice", "Voice Settings")
    data object SettingsAgent : NavigationRoute("settings/agent", "Agent Control")
    data object SettingsChannels : NavigationRoute("settings/channels", "Channels")
    data object SettingsSkills : NavigationRoute("settings/skills", "Skills")
    data object SettingsMCP : NavigationRoute("settings/mcp", "MCP Connectors")
    data object SettingsConfig : NavigationRoute("settings/config", "Configuration")
}
