package com.clawdroid.app.ui.navigation

sealed class NavRoutes(val route: String, val label: String) {
    data object Splash : NavRoutes("splash", "Splash")
    data object Setup : NavRoutes("setup", "Setup")
    data object Chat : NavRoutes("chat", "Chat")
    data object Terminal : NavRoutes("terminal", "Terminal")
    data object Settings : NavRoutes("settings", "Settings")
    data object SettingsVoice : NavRoutes("settings/voice", "Voice")
    data object SettingsAgent : NavRoutes("settings/agent", "Agent")
    data object SettingsChannels : NavRoutes("settings/channels", "Channels")
    data object SettingsSkills : NavRoutes("settings/skills", "Skills")
    data object SettingsMCP : NavRoutes("settings/mcp", "MCP")
    data object SettingsConfig : NavRoutes("settings/config", "Config")
    data object SettingsAutomations : NavRoutes("settings/automations", "Automations")
    data object Hatching : NavRoutes("hatching", "Hatching")
    data object PostSetup : NavRoutes("post_setup", "Post Setup")
    data object CronJobs : NavRoutes("cron_jobs", "Cron Jobs")
    data object ConfigEditor : NavRoutes("config_editor/{fileType}", "Config Editor") {
        fun create(fileType: String) = "config_editor/$fileType"
    }
}
