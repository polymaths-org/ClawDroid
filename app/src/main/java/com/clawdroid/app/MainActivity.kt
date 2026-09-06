package com.clawdroid.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.clawdroid.app.core.automation.AutomationScheduler
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.ui.navigation.AppNavHost
import com.clawdroid.app.ui.theme.ClawDroidTheme
import kotlinx.coroutines.launch

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

        if (AppConfigManager.ultraAgentEnabled) {
            ServiceManager.start(this)
        }

        if (intent?.getBooleanExtra("START_VOICE_SESSION", false) == true) {
            startVoiceSessionTrigger.value = true
        }

        handleDeepLink(intent)

        setContent {
            ClawDroidTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    startVoiceTrigger = startVoiceSessionTrigger.value,
                    onVoiceTriggerHandled = { startVoiceSessionTrigger.value = false },
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
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Failed to exchange token for $host",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }
}
