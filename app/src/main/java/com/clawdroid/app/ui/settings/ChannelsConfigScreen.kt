package com.clawdroid.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.channel.ChannelQrGenerator
import com.clawdroid.app.core.channels.discord.DiscordChannel
import com.clawdroid.app.core.channels.slack.SlackChannel
import com.clawdroid.app.core.channels.telegram.TelegramChannel
import com.clawdroid.app.core.channels.whatsapp.WacliBridge
import com.clawdroid.app.core.channels.whatsapp.WhatsAppListenerStatus
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.terminal.ProcessManagerProvider
import com.clawdroid.app.core.terminal.ProcessState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val processManager = remember { ProcessManagerProvider.get(context) }

    var smsPermission by remember { mutableStateOf(hasSmsPermissions(context)) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrTitle by remember { mutableStateOf("") }

    var whatsappEnabled by remember { mutableStateOf(AppConfigManager.whatsappEnabled) }
    var whatsappPhone by remember { mutableStateOf(AppConfigManager.whatsappPhone) }
    var whatsappContacts by remember { mutableStateOf(AppConfigManager.whatsappAllowedContacts) }
    var whatsappMonitoredChats by remember { mutableStateOf(AppConfigManager.whatsappMonitoredChats) }
    var whatsappAutoReply by remember { mutableStateOf(AppConfigManager.whatsappAutoReply) }
    var whatsappNotifyOnMessage by remember { mutableStateOf(AppConfigManager.whatsappNotifyOnMessage) }
    var whatsappAutoDownloadMedia by remember { mutableStateOf(AppConfigManager.whatsappAutoDownloadMedia) }
    var whatsappSendReadReceipts by remember { mutableStateOf(AppConfigManager.whatsappSendReadReceipts) }
    var whatsappShowTypingIndicator by remember { mutableStateOf(AppConfigManager.whatsappShowTypingIndicator) }
    var whatsappCliCommand by remember { mutableStateOf(AppConfigManager.whatsappCliCommand) }
    var whatsappSendCommand by remember { mutableStateOf(AppConfigManager.whatsappSendCommand) }
    var whatsappPollCommand by remember { mutableStateOf(AppConfigManager.whatsappPollCommand) }
    var whatsappQrProcessId by remember { mutableStateOf<String?>(null) }
    var whatsappQrOutput by remember { mutableStateOf("") }
    var whatsappQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var whatsappQrStatus by remember { mutableStateOf("") }
    var whatsappPairingCode by remember { mutableStateOf<String?>(null) }
    var whatsappListenerStatus by remember { mutableStateOf<WhatsAppListenerStatus?>(null) }
    var smsEnabled by remember { mutableStateOf(AppConfigManager.smsEnabled) }

    var telegramEnabled by remember { mutableStateOf(AppConfigManager.telegramEnabled) }
    var telegramToken by remember { mutableStateOf(AppConfigManager.telegramBotToken) }
    var telegramChats by remember { mutableStateOf(AppConfigManager.telegramAllowedChats) }

    var slackEnabled by remember { mutableStateOf(AppConfigManager.slackEnabled) }
    var slackToken by remember { mutableStateOf(AppConfigManager.slackBotToken) }
    var slackSecret by remember { mutableStateOf(AppConfigManager.slackSigningSecret) }

    var discordEnabled by remember { mutableStateOf(AppConfigManager.discordEnabled) }
    var discordToken by remember { mutableStateOf(AppConfigManager.discordBotToken) }

    var emailEnabled by remember { mutableStateOf(AppConfigManager.emailChannelEnabled) }
    var emailAddress by remember { mutableStateOf(AppConfigManager.emailChannelAddress) }
    var emailPassword by remember { mutableStateOf(AppConfigManager.emailChannelPassword) }

    var webhookEnabled by remember { mutableStateOf(AppConfigManager.webhookEnabled) }
    var webhookUrl by remember { mutableStateOf(AppConfigManager.webhookUrl) }
    var webhookSecret by remember { mutableStateOf(AppConfigManager.webhookSecret) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        smsPermission = grants[Manifest.permission.READ_SMS] == true && grants[Manifest.permission.SEND_SMS] == true
        if (smsPermission) {
            AppConfigManager.smsEnabled = true
            smsEnabled = true
            saveChannelsAndRun(context)
            Toast.makeText(context, "SMS channel enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "SMS permissions are required for the SMS channel.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        smsPermission = hasSmsPermissions(context)
    }

    LaunchedEffect(whatsappQrProcessId) {
        val id = whatsappQrProcessId ?: return@LaunchedEffect
        while (isActive) {
            val status = runCatching { processManager.checkProcess(id) }.getOrNull() ?: break
            whatsappQrOutput = status.recentOutput
            whatsappQrStatus = status.state.name
            WacliBridge.extractPairingCode(status.recentOutput)?.let { code ->
                whatsappPairingCode = code
            }
            extractWhatsAppQrPayload(status.recentOutput)?.let { payload ->
                whatsappQrBitmap = ChannelQrGenerator.generateQr(payload)
            }
            if (status.state in setOf(ProcessState.COMPLETED, ProcessState.FAILED, ProcessState.KILLED, ProcessState.TIMED_OUT)) {
                break
            }
            delay(1_000)
        }
    }

    LaunchedEffect(whatsappEnabled) {
        while (isActive) {
            whatsappListenerStatus = runCatching { WacliBridge.status(context) }.getOrNull()
            delay(5_000)
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun persistBasicChannels() {
        AppConfigManager.whatsappEnabled = whatsappEnabled
        AppConfigManager.whatsappPhone = whatsappPhone.trim()
        AppConfigManager.whatsappAllowedContacts = whatsappContacts.trim()
        AppConfigManager.whatsappMonitoredChats = whatsappMonitoredChats.trim()
        AppConfigManager.whatsappAutoReply = whatsappAutoReply
        AppConfigManager.whatsappNotifyOnMessage = whatsappNotifyOnMessage
        AppConfigManager.whatsappAutoDownloadMedia = whatsappAutoDownloadMedia
        AppConfigManager.whatsappSendReadReceipts = whatsappSendReadReceipts
        AppConfigManager.whatsappShowTypingIndicator = whatsappShowTypingIndicator
        AppConfigManager.whatsappCliCommand = whatsappCliCommand.trim()
        AppConfigManager.whatsappSendCommand = whatsappSendCommand.trim()
        AppConfigManager.whatsappPollCommand = whatsappPollCommand.trim()
        AppConfigManager.smsEnabled = smsEnabled && smsPermission
        saveChannelsAndRun(context)
    }

    qrBitmap?.let { bitmap ->
        QrCodeDialog(
            title = qrTitle,
            qrBitmap = bitmap,
            onDismiss = { qrBitmap = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connected Channels", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {
            item {
                ChannelCard(
                    icon = Icons.Rounded.Chat,
                    title = "WhatsApp",
                    subtitle = when {
                        whatsappListenerStatus?.isRunning == true -> "wacli listener running"
                        whatsappEnabled -> "wacli channel enabled"
                        else -> "Connect with phone-code pairing"
                    },
                    enabled = whatsappEnabled,
                    canToggle = whatsappPhone.isNotBlank() || whatsappCliCommand.isNotBlank(),
                    onToggle = {
                        whatsappEnabled = it
                        persistBasicChannels()
                    },
                ) {
                    ChannelTextField(
                        value = whatsappPhone,
                        onValueChange = { whatsappPhone = it },
                        label = "WhatsApp phone number",
                        placeholder = "+91 98765 43210"
                    )
                    ChannelTextField(
                        value = whatsappContacts,
                        onValueChange = { whatsappContacts = it },
                        label = "Allowed contacts",
                        placeholder = "Blank allows every sender"
                    )
                    ChannelTextField(
                        value = whatsappMonitoredChats,
                        onValueChange = { whatsappMonitoredChats = it },
                        label = "Monitored chat JIDs",
                        placeholder = "Comma separated. Blank lets listener use default sync output."
                    )
                    ChannelSwitchRow("Auto-reply queue", "Queue new messages for agent handling.", whatsappAutoReply) {
                        whatsappAutoReply = it
                        persistBasicChannels()
                    }
                    ChannelSwitchRow("Notify on messages", "Emit listener notifications and alert files.", whatsappNotifyOnMessage) {
                        whatsappNotifyOnMessage = it
                        persistBasicChannels()
                    }
                    ChannelSwitchRow("Auto-download media", "Reserved for wacli media sync.", whatsappAutoDownloadMedia) {
                        whatsappAutoDownloadMedia = it
                        persistBasicChannels()
                    }
                    ChannelSwitchRow("Read receipts", "Mark messages read when the listener reads them.", whatsappSendReadReceipts) {
                        whatsappSendReadReceipts = it
                        persistBasicChannels()
                    }
                    ChannelSwitchRow("Typing indicator", "Show typing while the agent is drafting.", whatsappShowTypingIndicator) {
                        whatsappShowTypingIndicator = it
                        persistBasicChannels()
                    }
                    WhatsAppPairingPanel(
                        code = whatsappPairingCode,
                        output = whatsappQrOutput,
                        status = whatsappQrStatus,
                    )
                    WhatsAppListenerPanel(status = whatsappListenerStatus)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val phone = whatsappPhone.trim()
                                    if (phone.isBlank()) {
                                        toast("Enter a phone number first.")
                                        return@launch
                                    }
                                    whatsappEnabled = true
                                    persistBasicChannels()
                                    WacliBridge.prepare(context, phone)
                                    whatsappQrBitmap = null
                                    whatsappPairingCode = null
                                    whatsappQrOutput = "Generating WhatsApp pairing code..."
                                    whatsappQrStatus = "STARTING"
                                    val started = runCatching {
                                        processManager.startProcess(
                                            command = WacliBridge.authCommand(context, phone),
                                            timeout = 3.hours,
                                        )
                                    }
                                    started
                                        .onSuccess {
                                            whatsappQrProcessId = it.processId
                                            whatsappQrOutput = it.initialOutput
                                            whatsappPairingCode = WacliBridge.extractPairingCode(it.initialOutput)
                                        }
                                        .onFailure {
                                            whatsappQrOutput = it.message ?: "Unable to start WhatsApp pairing."
                                            whatsappQrStatus = "FAILED"
                                        }
                                }
                            },
                            enabled = whatsappPhone.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pair Code")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    whatsappEnabled = true
                                    persistBasicChannels()
                                    val result = WacliBridge.startListener(context)
                                    whatsappListenerStatus = WacliBridge.status(context)
                                    result.onSuccess { toast("WhatsApp listener started.") }
                                        .onFailure { Toast.makeText(context, it.message ?: "Listener failed.", Toast.LENGTH_LONG).show() }
                                }
                            },
                            enabled = whatsappPhone.isNotBlank() || whatsappEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start Listener")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                WacliBridge.stopListener(context)
                                whatsappListenerStatus = WacliBridge.status(context)
                                toast("WhatsApp listener stopped.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Listener")
                    }
                    ChannelTextField(
                        value = whatsappCliCommand,
                        onValueChange = { whatsappCliCommand = it },
                        label = "QR/session command",
                        placeholder = "wacli login --print-qr or open-wa command"
                    )
                    ChannelTextField(
                        value = whatsappSendCommand,
                        onValueChange = { whatsappSendCommand = it },
                        label = "Send command template",
                        placeholder = "Example: wacli send {target} {text}"
                    )
                    ChannelTextField(
                        value = whatsappPollCommand,
                        onValueChange = { whatsappPollCommand = it },
                        label = "Poll command template",
                        placeholder = "Command that prints unread messages"
                    )
                    WhatsAppQrPanel(
                        bitmap = whatsappQrBitmap,
                        output = whatsappQrOutput,
                        status = whatsappQrStatus,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val command = whatsappCliCommand.trim()
                                    if (command.isBlank()) {
                                        toast("Enter a WhatsApp QR/session command first.")
                                        return@launch
                                    }
                                    persistBasicChannels()
                                    whatsappQrBitmap = null
                                    whatsappQrOutput = "Starting WhatsApp CLI bridge..."
                                    whatsappQrStatus = "STARTING"
                                    val started = runCatching {
                                        processManager.startProcess(command = command, timeout = 3.hours)
                                    }
                                    started
                                        .onSuccess {
                                            whatsappQrProcessId = it.processId
                                            whatsappQrOutput = it.initialOutput
                                            extractWhatsAppQrPayload(it.initialOutput)?.let { payload ->
                                                whatsappQrBitmap = ChannelQrGenerator.generateQr(payload)
                                            }
                                        }
                                        .onFailure {
                                            whatsappQrOutput = it.message ?: "Unable to start WhatsApp CLI bridge."
                                            whatsappQrStatus = "FAILED"
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start QR")
                        }
                        Button(
                            onClick = {
                                whatsappEnabled = true
                                persistBasicChannels()
                                toast("WhatsApp channel saved.")
                            },
                            enabled = whatsappPhone.isNotBlank() || whatsappCliCommand.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Sms,
                    title = "SMS",
                    subtitle = if (smsPermission) "SMS permissions ready" else "SMS permissions required",
                    enabled = smsEnabled && smsPermission,
                    canToggle = smsPermission,
                    onToggle = {
                        smsEnabled = it
                        persistBasicChannels()
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                smsPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (smsPermission) "Permissions OK" else "Grant SMS")
                        }
                        Button(
                            onClick = {
                                smsPermission = hasSmsPermissions(context)
                                smsEnabled = smsPermission
                                persistBasicChannels()
                                toast("SMS channel saved.")
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Hub,
                    title = "Telegram",
                    subtitle = if (telegramEnabled) "Bot polling enabled" else "Bot token required",
                    enabled = telegramEnabled,
                    canToggle = telegramToken.isNotBlank(),
                    onToggle = {
                        telegramEnabled = it
                        AppConfigManager.telegramEnabled = it
                        AppConfigManager.telegramBotToken = telegramToken.trim()
                        AppConfigManager.telegramAllowedChats = telegramChats.trim()
                        saveChannelsAndRun(context)
                    },
                ) {
                    ChannelTextField(telegramToken, { telegramToken = it }, "Bot token", secret = true)
                    ChannelTextField(telegramChats, { telegramChats = it }, "Allowed chat IDs", "Comma separated")
                    Button(
                        onClick = {
                            scope.launch {
                                val result = TelegramChannel().authenticate(mapOf("bot_token" to telegramToken.trim()))
                                if (result.isSuccess) {
                                    telegramEnabled = true
                                    AppConfigManager.telegramEnabled = true
                                    AppConfigManager.telegramBotToken = telegramToken.trim()
                                    AppConfigManager.telegramAllowedChats = telegramChats.trim()
                                    saveChannelsAndRun(context)
                                    toast("Telegram connected.")
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Telegram token failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = telegramToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test, Save & Start")
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Hub,
                    title = "Slack",
                    subtitle = if (slackEnabled) "Bot polling enabled" else "Bot token required",
                    enabled = slackEnabled,
                    canToggle = slackToken.isNotBlank(),
                    onToggle = {
                        slackEnabled = it
                        AppConfigManager.slackEnabled = it
                        AppConfigManager.slackBotToken = slackToken.trim()
                        AppConfigManager.slackSigningSecret = slackSecret.trim()
                        saveChannelsAndRun(context)
                    },
                ) {
                    ChannelTextField(slackToken, { slackToken = it }, "Bot token", secret = true)
                    ChannelTextField(slackSecret, { slackSecret = it }, "Signing secret", secret = true)
                    Button(
                        onClick = {
                            scope.launch {
                                val result = SlackChannel().authenticate(
                                    mapOf("bot_token" to slackToken.trim(), "signing_secret" to slackSecret.trim())
                                )
                                if (result.isSuccess) {
                                    slackEnabled = true
                                    AppConfigManager.slackEnabled = true
                                    AppConfigManager.slackBotToken = slackToken.trim()
                                    AppConfigManager.slackSigningSecret = slackSecret.trim()
                                    saveChannelsAndRun(context)
                                    toast("Slack connected.")
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Slack token failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = slackToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test, Save & Start")
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Hub,
                    title = "Discord",
                    subtitle = if (discordEnabled) "Bot polling enabled" else "Bot token required",
                    enabled = discordEnabled,
                    canToggle = discordToken.isNotBlank(),
                    onToggle = {
                        discordEnabled = it
                        AppConfigManager.discordEnabled = it
                        AppConfigManager.discordBotToken = discordToken.trim()
                        saveChannelsAndRun(context)
                    },
                ) {
                    ChannelTextField(discordToken, { discordToken = it }, "Bot token", secret = true)
                    Button(
                        onClick = {
                            scope.launch {
                                val result = DiscordChannel().authenticate(mapOf("bot_token" to discordToken.trim()))
                                if (result.isSuccess) {
                                    discordEnabled = true
                                    AppConfigManager.discordEnabled = true
                                    AppConfigManager.discordBotToken = discordToken.trim()
                                    saveChannelsAndRun(context)
                                    toast("Discord connected.")
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Discord token failed.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = discordToken.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test, Save & Start")
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Email,
                    title = "Email",
                    subtitle = if (emailEnabled) "Send intent enabled" else "Email address required",
                    enabled = emailEnabled,
                    canToggle = emailAddress.isNotBlank(),
                    onToggle = {
                        emailEnabled = it
                        AppConfigManager.emailChannelEnabled = it
                        AppConfigManager.emailChannelAddress = emailAddress.trim()
                        AppConfigManager.emailChannelPassword = emailPassword.trim()
                        saveChannelsAndRun(context)
                    },
                ) {
                    ChannelTextField(emailAddress, { emailAddress = it }, "Email address")
                    ChannelTextField(emailPassword, { emailPassword = it }, "App password", secret = true)
                    Button(
                        onClick = {
                            emailEnabled = emailAddress.isNotBlank()
                            AppConfigManager.emailChannelEnabled = emailEnabled
                            AppConfigManager.emailChannelAddress = emailAddress.trim()
                            AppConfigManager.emailChannelPassword = emailPassword.trim()
                            saveChannelsAndRun(context)
                            toast("Email channel saved.")
                        },
                        enabled = emailAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & Start")
                    }
                }
            }

            item {
                ChannelCard(
                    icon = Icons.Rounded.Hub,
                    title = "Webhook",
                    subtitle = if (webhookEnabled) "Outbound webhook enabled" else "Webhook URL required",
                    enabled = webhookEnabled,
                    canToggle = webhookUrl.isNotBlank(),
                    onToggle = {
                        webhookEnabled = it
                        AppConfigManager.webhookEnabled = it
                        AppConfigManager.webhookUrl = webhookUrl.trim()
                        AppConfigManager.webhookSecret = webhookSecret.trim()
                        saveChannelsAndRun(context)
                    },
                ) {
                    ChannelTextField(webhookUrl, { webhookUrl = it }, "Webhook URL")
                    ChannelTextField(webhookSecret, { webhookSecret = it }, "Webhook secret", secret = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (webhookUrl.isNotBlank()) {
                                    qrTitle = "Webhook URL"
                                    qrBitmap = ChannelQrGenerator.generateWebhookQr(webhookUrl.trim())
                                }
                            },
                            enabled = webhookUrl.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("QR")
                        }
                        Button(
                            onClick = {
                                webhookEnabled = webhookUrl.isNotBlank()
                                AppConfigManager.webhookEnabled = webhookEnabled
                                AppConfigManager.webhookUrl = webhookUrl.trim()
                                AppConfigManager.webhookSecret = webhookSecret.trim()
                                saveChannelsAndRun(context)
                                toast("Webhook channel saved.")
                            },
                            enabled = webhookUrl.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    canToggle: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.18f else 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    enabled = canToggle,
                    onCheckedChange = onToggle,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            content()
        }
    }
}

@Composable
private fun ChannelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ChannelSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WhatsAppPairingPanel(
    code: String?,
    output: String,
    status: String,
) {
    if (code.isNullOrBlank() && output.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Phone-code pairing",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        if (!code.isNullOrBlank()) {
            Text(
                text = code,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "Open WhatsApp > Linked devices > Link a device > Link with phone number instead, then enter this code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (status.isNotBlank()) {
            Text(
                text = "Pairing status: $status",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (output.isNotBlank() && code.isNullOrBlank()) {
            Text(
                text = output.takeLast(900),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun WhatsAppListenerPanel(status: WhatsAppListenerStatus?) {
    if (status == null) return
    val running = status.isRunning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.52f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (running) "Listener active" else "Listener inactive",
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "sync=${if (status.syncRunning) "running" else "off"} daemon=${if (status.daemonRunning) "running" else "off"} pid=${status.daemonPid.ifBlank { "-" }} heartbeat=${status.lastHeartbeatSecondsAgo?.let { "${it}s ago" } ?: "-"}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        if (status.logTail.isNotBlank()) {
            Text(
                text = status.logTail.takeLast(900),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.20f))
                    .padding(10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun WhatsAppQrPanel(
    bitmap: Bitmap?,
    output: String,
    status: String,
) {
    if (bitmap == null && output.isBlank()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Scan this QR from your other phone: WhatsApp > Linked devices > Link a device.",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "WhatsApp login QR",
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentScale = ContentScale.Fit,
            )
        }
        if (status.isNotBlank()) {
            Text(
                text = "Bridge status: $status",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (output.isNotBlank()) {
            Text(
                text = output.takeLast(1_800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.26f))
                    .padding(10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
private fun QrCodeDialog(
    title: String,
    qrBitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentScale = ContentScale.Fit,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private fun saveChannelsAndRun(context: Context) {
    AppConfigManager.syncToSandbox(context)
    val anyEnabled = AppConfigManager.whatsappEnabled ||
        AppConfigManager.smsEnabled ||
        AppConfigManager.telegramEnabled ||
        AppConfigManager.slackEnabled ||
        AppConfigManager.discordEnabled ||
        AppConfigManager.emailChannelEnabled ||
        AppConfigManager.webhookEnabled

    if (anyEnabled) {
        ServiceManager.restart(context)
    } else {
        ServiceManager.stop(context)
    }
}

private fun hasSmsPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
}

private fun extractWhatsAppQrPayload(output: String): String? {
    val labeledPatterns = listOf(
        Regex("""(?i)(?:qr\s*code|qrcode|qr|data-ref|ref)\s*[:=]\s*["']?([^"'\n]{24,})["']?"""),
        Regex("""(?i)(?:pairing\s*code|pairing)\s*[:=]\s*["']?([^"'\n]{8,})["']?"""),
    )
    labeledPatterns.forEach { pattern ->
        pattern.find(output)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 8 }?.let {
            return it
        }
    }

    return output.lineSequence()
        .map { it.trim().trim('"', '\'') }
        .firstOrNull { line ->
            line.length >= 40 &&
                !line.contains(' ') &&
                (line.contains(',') || line.startsWith("2@") || line.startsWith("1@"))
        }
}
