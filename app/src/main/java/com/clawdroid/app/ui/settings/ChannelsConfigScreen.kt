package com.clawdroid.app.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.channel.ChannelQrGenerator

data class ChannelIntegration(
    val id: String,
    val name: String,
    val icon: String,  // Emoji or icon name
    val description: String,
    val isConnected: Boolean,
    val authStatus: String = if (isConnected) "Connected" else "Not connected"
)

/**
 * Channels Configuration Screen - Connect WhatsApp, Telegram, Discord, Slack, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var channels by remember { mutableStateOf(listOf(
        ChannelIntegration(
            "whatsapp",
            "WhatsApp",
            "💬",
            "Receive messages and respond via WhatsApp using WaCLI",
            isConnected = false
        ),
        ChannelIntegration(
            "telegram",
            "Telegram",
            "📱",
            "Receive Telegram messages and send responses",
            isConnected = true,
            "Connected as @clawdroid_bot"
        ),
        ChannelIntegration(
            "slack",
            "Slack",
            "💼",
            "Integrate with Slack workspace for team collaboration",
            isConnected = true,
            "Connected to Workspace"
        ),
        ChannelIntegration(
            "discord",
            "Discord",
            "🎮",
            "Connect to Discord server for community interaction",
            isConnected = false
        ),
        ChannelIntegration(
            "email",
            "Email",
            "📧",
            "Send and receive emails through configured account",
            isConnected = false
        ),
        ChannelIntegration(
            "webhook",
            "Webhooks",
            "🔌",
            "Generic webhook for any HTTP service integration",
            isConnected = false
        )
    )) }

    var selectedChannel by remember { mutableStateOf<ChannelIntegration?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connected Channels") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Connected channels header
            item {
                Text(
                    "Connected Channels",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(channels.filter { it.isConnected }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { selectedChannel = channel }
                )
            }

            // Available channels header
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Available Channels",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(channels.filter { !it.isConnected }) { channel ->
                ChannelCard(
                    channel = channel,
                    onClick = { selectedChannel = channel }
                )
            }
        }
    }

    // Show configuration dialog for selected channel
    if (selectedChannel != null) {
        ChannelConfigDialog(
            channel = selectedChannel!!,
            onDismiss = { selectedChannel = null },
            onConnect = { newStatus ->
                channels = channels.map {
                    if (it.id == selectedChannel!!.id)
                        it.copy(
                            isConnected = true,
                            authStatus = newStatus
                        )
                    else it
                }
                selectedChannel = null
            },
            onDisconnect = {
                channels = channels.map {
                    if (it.id == selectedChannel!!.id)
                        it.copy(
                            isConnected = false,
                            authStatus = "Not connected"
                        )
                    else it
                }
                selectedChannel = null
            }
        )
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelIntegration,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Channel icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        channel.icon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        channel.authStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (channel.isConnected)
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (channel.isConnected) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
            )
        }
    }
}

@Composable
private fun ChannelConfigDialog(
    channel: ChannelIntegration,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showQrCode by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (showQrCode && qrBitmap != null) {
        QrCodeDialog(
            title = "${channel.icon} ${channel.name} - QR Code",
            qrBitmap = qrBitmap!!,
            onDismiss = { showQrCode = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${channel.icon} ${channel.name}") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    channel.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                when (channel.id) {
                    "whatsapp" -> WhatsAppConfig(
                        onShowQr = {
                            qrBitmap = ChannelQrGenerator.generateWhatsAppQr()
                            showQrCode = true
                        }
                    )
                    "telegram" -> TelegramConfig(
                        isConnected = channel.isConnected,
                        onShowQr = {
                            qrBitmap = ChannelQrGenerator.generateTelegramQr()
                            showQrCode = true
                        }
                    )
                    "slack" -> SlackConfig(isConnected = channel.isConnected)
                    "discord" -> DiscordConfig(
                        isConnected = channel.isConnected,
                        onShowQr = {
                            qrBitmap = ChannelQrGenerator.generateDiscordQr()
                            showQrCode = true
                        }
                    )
                    "email" -> EmailConfig(isConnected = channel.isConnected)
                    "webhook" -> WebhookConfig(
                        isConnected = channel.isConnected,
                        onShowQr = {
                            qrBitmap = ChannelQrGenerator.generateWebhookQr("https://clawdroid.local/webhook")
                            showQrCode = true
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (channel.isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(onClick = { onConnect("Connected") }) {
                    Text("Connect")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WhatsAppConfig(onShowQr: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Setup Instructions:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "1. Install WaCLI\n2. Scan QR code to authenticate\n3. Grant permissions",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show QR Code")
        }
    }
}

@Composable
private fun TelegramConfig(isConnected: Boolean, onShowQr: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isConnected) {
            Text("Connected as @clawdroid_bot", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show QR Code")
        }
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SlackConfig(isConnected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isConnected) {
            Text("Connected to workspace", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { /* OAuth flow */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Authorize with Slack")
        }
    }
}

@Composable
private fun DiscordConfig(isConnected: Boolean, onShowQr: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show QR Code")
        }
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Bot Token") },
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Server ID (Guild ID)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmailConfig(isConnected: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("App Password / Token") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WebhookConfig(isConnected: Boolean, onShowQr: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show QR Code")
        }
        TextField(
            value = "",
            onValueChange = {},
            label = { Text("Webhook URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Your webhook endpoint:\nhttps://clawdroid.local/webhook",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QrCodeDialog(
    title: String,
    qrBitmap: Bitmap,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentScale = ContentScale.Fit
                )
                Text(
                    "Scan this QR code with your mobile device to authenticate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
