package com.clawdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.clawdroid.app.core.automation.AutomationScheduler
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.ui.chat.ChatScreen
import com.clawdroid.app.ui.theme.ClawDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        AutomationScheduler.schedule(this)
        setContent {
            ClawDroidTheme {
                ClawDroidApp()
            }
        }
    }
}

@Composable
private fun ClawDroidApp() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        ChatScreen()
    }
}

@Preview(showBackground = true)
@Composable
private fun ClawDroidAppPreview() {
    ClawDroidTheme {
        ClawDroidApp()
    }
}
