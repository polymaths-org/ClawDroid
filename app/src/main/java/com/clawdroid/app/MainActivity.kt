package com.clawdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.automation.AutomationScheduler
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.ui.chat.ChatScreen
import com.clawdroid.app.ui.settings.SettingsScreen
import com.clawdroid.app.ui.setup.SetupScreen
import com.clawdroid.app.ui.sidebar.SidebarContent
import com.clawdroid.app.ui.splash.SplashScreen
import com.clawdroid.app.ui.theme.ClawDroidTheme
import com.clawdroid.app.ui.theme.CardDark
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MoltenYellow
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.launch

import android.content.Intent
import com.clawdroid.app.core.service.ServiceManager

class MainActivity : ComponentActivity() {
    private val startVoiceSessionTrigger = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfigManager.init(applicationContext)
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
    }
}

enum class Screen { Splash, Setup, Chat, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClawDroidApp(
    startVoiceTrigger: Boolean,
    onVoiceTriggerHandled: () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Splash) }

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

            Screen.Chat -> {
                ChatScreen(
                    onNavigateToSettings = { currentScreen = Screen.Settings },
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
