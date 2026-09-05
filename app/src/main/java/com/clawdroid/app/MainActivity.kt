package com.clawdroid.app

import android.content.Intent
import android.os.Bundle
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
import com.clawdroid.app.core.service.ServiceManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.clawdroid.app.ui.chat.ChatScreen
import com.clawdroid.app.ui.settings.SettingsScreen
import com.clawdroid.app.ui.settings.McpScreen
import com.clawdroid.app.ui.setup.SetupScreen
import com.clawdroid.app.ui.splash.SplashScreen
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
        AutomationScheduler.schedule(this)

        // Start Foreground Service if Ultra Agent Mode is enabled
        if (AppConfigManager.ultraAgentEnabled) {
            ServiceManager.start(this)
        }

        // Check launch intent for background voice trigger
        if (intent?.getBooleanExtra("START_VOICE_SESSION", false) == true) {
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
        if (intent.getBooleanExtra("START_VOICE_SESSION", false)) {
            startVoiceSessionTrigger.value = true
        }
        handleDeepLink(intent)
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
}

enum class Screen { Splash, Setup, Chat, Settings, Mcp }

@Composable
private fun ClawDroidApp(
    startVoiceTrigger: Boolean,
    onVoiceTriggerHandled: () -> Unit,
) {
    var currentScreen by remember {
        mutableStateOf(
            if (MainActivity.isFreshLaunch) Screen.Splash else {
                if (AppConfigManager.isOnboardingComplete) Screen.Chat else Screen.Setup
            }
        )
    }

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
                            Screen.Chat
                        } else {
                            Screen.Setup
                        }
                    },
                )
            }

            Screen.Setup -> {
                SetupScreen(
                    onSetupComplete = { currentScreen = Screen.Chat },
                )
            }

            Screen.Settings -> {
                SettingsScreen(onBack = { currentScreen = Screen.Chat })
            }

            Screen.Mcp -> {
                McpScreen(onBack = { currentScreen = Screen.Chat })
            }

            Screen.Chat -> {
                ChatScreen(
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToMcp = { currentScreen = Screen.Mcp },
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
