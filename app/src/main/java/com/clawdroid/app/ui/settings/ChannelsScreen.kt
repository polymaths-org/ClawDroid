package com.clawdroid.app.ui.settings

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.GlowText
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var whatsappEnabled by remember { mutableStateOf(AppConfigManager.whatsappEnabled) }
    var whatsappAllowedContacts by remember { mutableStateOf(AppConfigManager.whatsappAllowedContacts) }
    var smsEnabled by remember { mutableStateOf(AppConfigManager.smsEnabled) }
    var notificationAccessGranted by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cn = ComponentName(context, com.clawdroid.app.core.channels.ClawNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                notificationAccessGranted = flat != null && flat.contains(cn.flattenToString())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save() {
        AppConfigManager.whatsappEnabled = whatsappEnabled
        AppConfigManager.whatsappAllowedContacts = whatsappAllowedContacts.trim()
        AppConfigManager.smsEnabled = smsEnabled
        AppConfigManager.syncToSandbox(context)
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = { Text("Channels", color = SoftWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlowText("Service Channels", style = MaterialTheme.typography.titleLarge)
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingSwitchRow(
                        title = "WhatsApp Automation",
                        subtitle = "Draft and send responses through notification actions",
                        checked = whatsappEnabled,
                        onCheckedChange = { whatsappEnabled = it },
                    )
                    if (whatsappEnabled) {
                        if (!notificationAccessGranted) {
                            GlassButton(onClick = {
                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }) {
                                Text("Grant Notification Access", color = SoftWhite, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("Notification access granted", color = EmberOrange, fontWeight = FontWeight.Medium)
                        }
                        GlassTextField(
                            value = whatsappAllowedContacts,
                            onValueChange = { whatsappAllowedContacts = it },
                            placeholder = "Allowed contacts, comma separated",
                        )
                    }
                }
            }

            GlassCard {
                SettingSwitchRow(
                    title = "SMS Channel",
                    subtitle = "Read and reply to SMS with user-controlled rules",
                    checked = smsEnabled,
                    onCheckedChange = { smsEnabled = it },
                )
            }

            GlassButton(onClick = { save() }) {
                Text("Save Channels", color = SoftWhite, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedGray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = EmberOrange),
        )
    }
}
