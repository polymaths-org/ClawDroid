package com.clawdroid.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.clawdroid.app.core.automation.AutomationScheduler
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.reminders.ReminderManager
import com.clawdroid.app.core.service.ServiceManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.clawdroid.app.ui.chat.ChatScreen
import com.clawdroid.app.ui.settings.AgentConfigScreen
import com.clawdroid.app.ui.settings.AudioConfigScreen
import com.clawdroid.app.ui.settings.AutomationsConfigScreen
import com.clawdroid.app.ui.settings.ChannelsConfigScreen
import com.clawdroid.app.ui.settings.ConfigEditorScreen
import com.clawdroid.app.ui.settings.ConfigFileType
import com.clawdroid.app.ui.settings.InterpoleConfigScreen
import com.clawdroid.app.ui.settings.SettingsScreen
import com.clawdroid.app.ui.settings.McpScreen
import com.clawdroid.app.ui.settings.NotificationConfigScreen
import com.clawdroid.app.ui.settings.PermissionManagerScreen
import com.clawdroid.app.ui.settings.ProviderConfigScreen
import com.clawdroid.app.ui.settings.SkillsConfigScreen
import com.clawdroid.app.ui.settings.ThemeConfigScreen
import com.clawdroid.app.ui.setup.SetupScreen
import com.clawdroid.app.ui.setup.PostSetupScreen
import com.clawdroid.app.ui.splash.HatchingScreen
import com.clawdroid.app.ui.splash.SplashScreen
import com.clawdroid.app.ui.terminal.TerminalScreen
import com.clawdroid.app.ui.theme.ClawDroidTheme

class MainActivity : ComponentActivity() {
    companion object {
        var isFreshLaunch = true
    }

    private val startVoiceSessionTrigger = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        AutomationScheduler.scheduleOrCancel(this)
        if (AppConfigManager.heartbeatEnabled) {
            AutomationScheduler.runNow(this)
        }
        ReminderManager.rescheduleAll(this)

        // Start Foreground Service if Ultra Agent Mode is enabled
        if (AppConfigManager.ultraAgentEnabled) {
            ServiceManager.start(this)
        }
        if (AppConfigManager.wakeOnVoiceEnabled && AppConfigManager.wakeDetectionMode == "background") {
            com.clawdroid.app.core.voice.WakeVoiceService.start(this)
        }
        val handledAssistIntent = handleAssistOverlayIntent(intent)
        // Check launch intent for background voice trigger.
        if (!handledAssistIntent && intent?.getBooleanExtra("START_VOICE_SESSION", false) == true) {
            startVoiceSessionTrigger.value = true
        }

        handleDeepLink(intent)

        setContent {
            ClawDroidTheme {
                ClawDroidApp(
                    startVoiceTrigger = startVoiceSessionTrigger.value,
                    onVoiceTriggerHandled = { startVoiceSessionTrigger.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val handledAssistIntent = handleAssistOverlayIntent(intent)
        if (!handledAssistIntent && intent.getBooleanExtra("START_VOICE_SESSION", false)) {
            startVoiceSessionTrigger.value = true
        }
        handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        NotificationHelper.setAppVisible(true)
    }

    override fun onStop() {
        NotificationHelper.setAppVisible(false)
        super.onStop()
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "clawdroid") {
            val code = uri.getQueryParameter("code")
            if (code != null) {
                lifecycleScope.launch {
                    val host = uri.host
                    val success = when (host) {
                        "github-auth" -> com.clawdroid.app.core.service.GithubAuthManager.exchangeAuthCode(code)
                        "notion-auth" -> com.clawdroid.app.core.service.NotionAuthManager.exchangeAuthCode(code)
                        "spotify-auth" -> com.clawdroid.app.core.service.SpotifyAuthManager.exchangeAuthCode(code)
                        else -> false
                    }
                    if (success) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Connected to ${host?.replace("-auth", "")?.replaceFirstChar { it.uppercaseChar() }} successfully!",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Failed to exchange token for $host",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun handleAssistOverlayIntent(intent: Intent?): Boolean {
        val action = intent?.action
        val isAssistAction = action == Intent.ACTION_VOICE_COMMAND ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_SEARCH_LONG_PRESS
        if (!isAssistAction) return false
        val canDrawOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDrawOverlay) {
            com.clawdroid.app.core.assistant.overlay.OverlayWindowService.startVoice(this)
            moveTaskToBack(true)
        } else {
            startVoiceSessionTrigger.value = true
        }
        return true
    }
}

enum class Screen {
    Splash,
    Setup,
    Hatching,
    PostSetup,
    Chat,
    Settings,
    Provider,
    Audio,
    Notifications,
    Agent,
    Automations,
    Connections,
    Channels,
    Skills,
    Mcp,
    Interpole,
    Themes,
    Permissions,
    ConfigEditor,
    Terminal,
}

@Composable
private fun ClawDroidApp(
    startVoiceTrigger: Boolean,
    onVoiceTriggerHandled: () -> Unit,
) {
    var currentScreen by remember {
        mutableStateOf(
            if (MainActivity.isFreshLaunch) Screen.Splash else {
                if (AppConfigManager.isOnboardingComplete) {
                    when {
                        !AppConfigManager.hasSeenHatching -> Screen.Hatching
                        !AppConfigManager.hasCompletedPostHatchIntro -> Screen.PostSetup
                        else -> Screen.Chat
                    }
                } else {
                    Screen.Setup
                }
            }
        )
    }
    var selectedConfigFileType by remember { mutableStateOf(ConfigFileType.AGENTS) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(300))
        },
        label = "screen_transition",
    ) { screen ->
        when (screen) {
            Screen.Splash -> {
                SplashScreen(
                    onSplashComplete = {
                        MainActivity.isFreshLaunch = false
                        currentScreen = if (AppConfigManager.isOnboardingComplete) {
                            when {
                                !AppConfigManager.hasSeenHatching -> Screen.Hatching
                                !AppConfigManager.hasCompletedPostHatchIntro -> Screen.PostSetup
                                else -> Screen.Chat
                            }
                        } else {
                            Screen.Setup
                        }
                    },
                )
            }

            Screen.Setup -> {
                SetupScreen(
                    onSetupComplete = { currentScreen = Screen.Hatching },
                )
            }

            Screen.Hatching -> {
                HatchingScreen(onComplete = { currentScreen = Screen.PostSetup })
            }

            Screen.PostSetup -> {
                PostSetupScreen(
                    onComplete = { currentScreen = Screen.Chat },
                    onOpenProviderSettings = { currentScreen = Screen.Provider },
                )
            }

            Screen.Settings -> {
                SettingsScreen(
                    onBack = { currentScreen = Screen.Chat },
                    onNavigateToProvider = { currentScreen = Screen.Provider },
                    onNavigateToAudio = { currentScreen = Screen.Audio },
                    onNavigateToNotifications = { currentScreen = Screen.Notifications },
                    onNavigateToAutomations = { currentScreen = Screen.Automations },
                    onNavigateToConnections = { currentScreen = Screen.Connections },
                    onNavigateToChannels = { currentScreen = Screen.Channels },
                    onNavigateToSkills = { currentScreen = Screen.Skills },
                    onNavigateToMcp = { currentScreen = Screen.Mcp },
                    onNavigateToInterpole = { currentScreen = Screen.Interpole },
                    onNavigateToAgentConfig = { currentScreen = Screen.Agent },
                    onNavigateToThemes = { currentScreen = Screen.Themes },
                    onNavigateToPermissions = { currentScreen = Screen.Permissions },
                    onNavigateToConfigEditor = {
                        selectedConfigFileType = it
                        currentScreen = Screen.ConfigEditor
                    },
                )
            }

            Screen.Audio -> AudioConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Notifications -> NotificationConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Provider -> ProviderConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Agent -> AgentConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Automations -> AutomationsConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Connections -> {
                McpScreen(
                    onBack = { currentScreen = Screen.Settings },
                    title = "Connections",
                    showConnectors = true,
                    showServers = false,
                )
            }

            Screen.Channels -> ChannelsConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Skills -> SkillsConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Mcp -> {
                McpScreen(
                    onBack = { currentScreen = Screen.Settings },
                    title = "MCP Servers",
                    showConnectors = false,
                    showServers = true,
                )
            }

            Screen.Interpole -> InterpoleConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Themes -> ThemeConfigScreen(onBack = { currentScreen = Screen.Settings })

            Screen.Permissions -> PermissionManagerScreen(onBack = { currentScreen = Screen.Settings })

            Screen.ConfigEditor -> ConfigEditorScreen(
                fileType = selectedConfigFileType,
                onBack = { currentScreen = Screen.Settings },
            )

            Screen.Terminal -> TerminalScreen(onBack = { currentScreen = Screen.Chat })

            Screen.Chat -> {
                ChatScreen(
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToMcp = { currentScreen = Screen.Mcp },
                    onNavigateToTerminal = { currentScreen = Screen.Terminal },
                    startVoiceTrigger = startVoiceTrigger,
                    onVoiceTriggerHandled = onVoiceTriggerHandled
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClawDroidAppPreview() {
    ClawDroidTheme {
        ClawDroidApp(
            startVoiceTrigger = false,
            onVoiceTriggerHandled = {}
        )
    }
}
