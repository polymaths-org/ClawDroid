package com.clawdroid.app.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.control.ScreenCaptureManager
import com.clawdroid.app.core.control.ScreenReaderService
import com.clawdroid.app.ui.components.AnimatedPresetCard
import com.clawdroid.app.ui.components.ChannelConnectionStatus
import com.clawdroid.app.ui.components.ChannelStatusCard
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.MCPStatusRow
import com.clawdroid.app.ui.components.MCPServerInfo
import com.clawdroid.app.ui.components.PresetBadge
import com.clawdroid.app.ui.components.PresetStatus
import com.clawdroid.app.ui.components.ServerStatus
import com.clawdroid.app.ui.components.SetupWizardScaffold
import com.clawdroid.app.ui.components.WizardActionRow
import com.clawdroid.app.ui.components.WizardStep
import com.clawdroid.app.ui.theme.ActivePurple
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.NeonBlue
import com.clawdroid.app.ui.theme.NeonCyan
import com.clawdroid.app.ui.theme.SoftWhite

@Composable
fun AudioConfigScreen(onBack: () -> Unit) {
    var ttsEngine by remember { mutableStateOf(AppConfigManager.ttsEngine) }
    var ttsVoice by remember { mutableStateOf(AppConfigManager.ttsVoice) }
    var ttsSpeed by remember { mutableStateOf(AppConfigManager.ttsSpeed) }
    var openaiKey by remember { mutableStateOf(AppConfigManager.openaiTtsApiKey) }
    var elevenlabsKey by remember { mutableStateOf(AppConfigManager.elevenlabsApiKey) }
    var deepgramKey by remember { mutableStateOf(AppConfigManager.deepgramApiKey) }
    var dynamicThinking by remember { mutableStateOf(AppConfigManager.dynamicThinkingEnabled) }
    var emojiTone by remember { mutableStateOf(AppConfigManager.emojiToneEnabled) }
    var piperEnabled by remember { mutableStateOf(AppConfigManager.mcpEnabled) }

    ConfigScaffold("Audio & Voice", onBack) {
        InfoCard(
            title = "Voice Runtime",
            body = "Pick Android system TTS or a cloud voice engine, tune speed, and control how ClawDroid talks while working."
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("TTS Engine")

                ConfigChoice("Android TTS", "Native Android system voice. Fast, offline, and reliable.", ttsEngine == "device") { ttsEngine = "device" }
                ConfigChoice("OpenAI TTS", "Cloud voices: alloy, echo, fable, onyx, nova, shimmer. 6 distinct personalities.", ttsEngine == "openai") { ttsEngine = "openai" }
                ConfigChoice("ElevenLabs", "Premium neural voices: Rachel, Domi, Josh, Bella. Ultra-realistic.", ttsEngine == "elevenlabs") { ttsEngine = "elevenlabs" }
                ConfigChoice("Deepgram", "Fast cloud TTS: Asteria, Luna, Orion, Zeus. Low latency.", ttsEngine == "deepgram") { ttsEngine = "deepgram" }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Voice Details")
                GlassTextField(
                    value = ttsVoice,
                    onValueChange = { ttsVoice = it },
                    placeholder = "Voice id, e.g. onyx, nova, rachel, asteria",
                )
                Text(
                    "Speech Speed: ${String.format("%.1fx", ttsSpeed)}",
                    color = EmberOrange,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = ttsSpeed,
                    onValueChange = { ttsSpeed = it },
                    valueRange = 0.5f..2.0f,
                    steps = 15,
                    colors = configSliderColors(),
                )
                ConfigSwitch("Dynamic Thinking", "Task-aware thinking phrases while processing in voice mode.", dynamicThinking) { dynamicThinking = it }
                ConfigSwitch("Emoji Tone", "Strip emojis from speech and convert to emotional tone hints instead.", emojiTone) { emojiTone = it }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Cloud TTS API Keys")
                SecretField("OpenAI TTS API key", openaiKey) { openaiKey = it }
                SecretField("ElevenLabs API key", elevenlabsKey) { elevenlabsKey = it }
                SecretField("Deepgram API key", deepgramKey) { deepgramKey = it }
                Text(
                    "Keys are stored locally in EncryptedSharedPreferences.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SaveConfigButton {
            AppConfigManager.ttsEngine = ttsEngine
            AppConfigManager.ttsVoice = ttsVoice.trim()
            AppConfigManager.ttsSpeed = ttsSpeed
            AppConfigManager.openaiTtsApiKey = openaiKey.trim()
            AppConfigManager.elevenlabsApiKey = elevenlabsKey.trim()
            AppConfigManager.deepgramApiKey = deepgramKey.trim()
            AppConfigManager.dynamicThinkingEnabled = dynamicThinking
            AppConfigManager.emojiToneEnabled = emojiTone
        }
    }
}

@Composable
fun ThemeConfigScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf(AppConfigManager.appTheme) }
    val themes = remember {
        listOf(
            ThemePreset("light", "Light", "Bright Material 3 surfaces for daytime use.", listOf(Color(0xFFF7F9FC), Color(0xFFD6E3FF), Color(0xFF245FA8))),
            ThemePreset("dark", "Dark", "Default focused dark surface.", listOf(Color(0xFF111416), Color(0xFF323537), Color(0xFFD3E2FF))),
            ThemePreset("minimalist", "Minimalist", "Quiet grayscale controls with reduced visual noise.", listOf(Color(0xFF101112), Color(0xFF2B2F32), Color(0xFFE8EAED))),
            ThemePreset("liquid_glass_light", "Liquid Glass Light", "Apple-style translucent white glass with blue system accents.", listOf(Color(0xFFFFFFFF), Color(0xFFEAF4FF), Color(0xFF007AFF), Color(0xFFAF52DE))),
            ThemePreset("liquid_glass_dark", "Liquid Glass Dark", "Deep translucent glass with luminous blue, violet, and soft highlights.", listOf(Color(0xFF05070A), Color(0xFF1C1C1E), Color(0xFF64D2FF), Color(0xFFBF5AF2))),
        )
    }

    ConfigScaffold("Themes", onBack) {
        InfoCard(
            title = "Application Theme",
            body = "Choose the visual treatment for ClawDroid. The selected theme is persisted and used by UI surfaces as theme support expands.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Theme Presets")
                themes.forEach { preset ->
                    ThemeChoiceCard(
                        preset = preset,
                        selected = selectedTheme == preset.id,
                        onClick = { selectedTheme = preset.id },
                    )
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Preview")
                DetailRow("Current", selectedTheme.replace('_', ' ').replaceFirstChar { it.uppercaseChar() })
                DetailRow("Applies To", "Chat, sidebar, settings, terminal surfaces")
            }
        }

        SaveConfigButton {
            AppConfigManager.appTheme = selectedTheme
        }
    }
}

private data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val colors: List<Color>,
)

@Composable
private fun ThemeChoiceCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.68f))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(preset.colors),
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(preset.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun PermissionManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshTick++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshTick++ }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun notificationListenerEnabled(): Boolean {
        val cn = ComponentName(context, com.clawdroid.app.core.channels.ClawNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn.flattenToString()) == true
    }

    fun batteryUnrestricted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppSettings() {
        settingsLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
    }

    ConfigScaffold("Permissions", onBack) {
        InfoCard(
            title = "Permission Manager",
            body = "Review and manage every Android permission or special access ClawDroid uses for agent work.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Runtime Permissions")
                PermissionRow(
                    title = "Microphone",
                    description = "Voice input and realtime call mode.",
                    granted = hasPermission(Manifest.permission.RECORD_AUDIO),
                    actionLabel = "Request",
                ) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = "Notifications",
                        description = "Task completion and background progress alerts.",
                        granted = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                        actionLabel = "Request",
                    ) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
                PermissionRow(
                    title = "Shared Files",
                    description = "Documents/ClawDroid inbox, output, projects, and exports.",
                    granted = if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager()
                    else hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    actionLabel = "Manage",
                ) {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    } else {
                        permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Special Access")
                PermissionRow(
                    title = "Display Over Other Apps",
                    description = "Floating assistant and visual overlay controls.",
                    granted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
                    actionLabel = "Manage",
                ) {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
                PermissionRow(
                    title = "Accessibility Service",
                    description = "Screen understanding and device control tools.",
                    granted = ScreenReaderService.instance != null,
                    actionLabel = "Open",
                ) {
                    settingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionRow(
                    title = "Notification Listener",
                    description = "Reads selected app notifications for channel workflows.",
                    granted = notificationListenerEnabled(),
                    actionLabel = "Open",
                ) {
                    settingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                PermissionRow(
                    title = "Screen Capture",
                    description = "Screenshot context for the assistant.",
                    granted = ScreenCaptureManager.isActive(),
                    actionLabel = "Start",
                ) {
                    Toast.makeText(context, "Start screen capture from Agent settings.", Toast.LENGTH_LONG).show()
                }
                PermissionRow(
                    title = "Battery Optimization",
                    description = "Keeps long-running tasks and background service alive.",
                    granted = batteryUnrestricted(),
                    actionLabel = "Manage",
                ) {
                    settingsLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("System Page")
                Text(
                    "Use Android's app info screen to revoke any permission at the OS level.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
                GlassButton(onClick = ::openAppSettings, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text("Open App Permission Settings", color = SoftWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) GlassFillStrong else GlassFill)
            .border(1.dp, if (granted) NeonCyan.copy(alpha = 0.35f) else GlassBorderDim, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (granted) NeonCyan else MutedGray.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold)
            Text(description, color = MutedGray, style = MaterialTheme.typography.bodySmall)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) NeonCyan else MutedGray,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        GlassButton(onClick = onAction, modifier = Modifier.width(92.dp).height(38.dp)) {
            Text(actionLabel, color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun McpConfigScreen(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(AppConfigManager.mcpEnabled) }
    var sandboxOnly by remember { mutableStateOf(AppConfigManager.mcpSandboxOnly) }
    var serverList by remember { mutableStateOf(AppConfigManager.mcpServerList) }

    val parsedServers = remember(serverList) {
        try {
            val arr = org.json.JSONArray(serverList)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MCPServerInfo(
                    name = obj.optString("name", ""),
                    command = obj.optString("command", ""),
                    args = obj.optString("args", ""),
                    status = try { ServerStatus.valueOf(obj.optString("status", "Stopped")) } catch (_: Exception) { ServerStatus.Stopped },
                    errorMessage = obj.optString("error", ""),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveServerList(servers: List<MCPServerInfo>) {
        val arr = org.json.JSONArray(servers.map { s ->
            org.json.JSONObject().apply {
                put("name", s.name)
                put("command", s.command)
                put("args", s.args)
                put("status", s.status.name)
                put("error", s.errorMessage)
            }
        })
        serverList = arr.toString()
        AppConfigManager.mcpServerList = serverList
    }

    fun toggleConnector(key: String, title: String, command: String, args: String = "") {
        val current = parsedServers.toMutableList()
        val existing = current.find { it.name == key }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add(MCPServerInfo(name = key, command = command, args = args))
        }
        saveServerList(current)
    }

    ConfigScaffold("Connectors", onBack) {
        InfoCard(
            title = "MCP Connectors",
            body = "Connectors give the agent access to external tools, databases, and services. Toggle each connector on to enable it."
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MCP Runtime", color = SoftWhite, fontWeight = FontWeight.Bold)
                        Text("Master switch for all connectors", color = MutedGray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.4f),
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = DeepBlack,
                        ),
                    )
                }
                AnimatedVisibility(visible = enabled) {
                    ConfigSwitch("Sandbox Only", "Restrict connectors to run inside the sandbox.", sandboxOnly) { sandboxOnly = it }
                }
            }
        }

        val connectors = listOf(
            ConnectorDef("filesystem", "Filesystem", Icons.Outlined.Folder, "npx @modelcontextprotocol/server-filesystem", "/home", "Read, write, and manage files on the local filesystem."),
            ConnectorDef("github", "GitHub", Icons.Outlined.Code, "python -m mcp_github", "", "Access repositories, issues, PRs, and code review."),
            ConnectorDef("browser", "Browser", Icons.Outlined.Language, "npx @anthropic/mcp-browser", "", "Headless browser for web scraping and automation."),
            ConnectorDef("sqlite", "SQLite", Icons.Outlined.Storage, "npx @anthropic/mcp-database-server sqlite", "", "Query and manage SQLite databases."),
            ConnectorDef("calendar", "Calendar", Icons.Outlined.CalendarMonth, "npx @anthropic/mcp-google-calendar", "", "Read and create calendar events."),
            ConnectorDef("email", "Email", Icons.Outlined.MailOutline, "npx @anthropic/mcp-email", "", "Send and read emails via MCP."),
            ConnectorDef("web-search", "Web Search", Icons.Outlined.Search, "python -m mcp_web_search", "", "Search the web and return results."),
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Available Connectors")

                connectors.forEach { def ->
                    val isConnected = parsedServers.any { it.name == def.key }
                    val connector = parsedServers.find { it.name == def.key }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isConnected) GlassFillStrong else GlassFill)
                            .border(1.dp, if (isConnected) NeonCyan.copy(alpha = 0.3f) else GlassBorderDim, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(def.icon, contentDescription = null, tint = if (isConnected) NeonCyan else MutedGray, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(def.title, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(def.description, color = MutedGray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isConnected,
                                onCheckedChange = { toggleConnector(def.key, def.title, def.command, def.args) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.4f),
                                    uncheckedThumbColor = MutedGray,
                                    uncheckedTrackColor = DeepBlack,
                                ),
                            )
                        }

                        if (isConnected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("\"${def.command} ${def.args}\"", color = MutedGray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        SaveConfigButton {
            AppConfigManager.mcpEnabled = enabled
            AppConfigManager.mcpSandboxOnly = sandboxOnly
            AppConfigManager.mcpServerList = serverList
        }
    }
}

private data class ConnectorDef(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val command: String,
    val args: String,
    val description: String,
)

@Composable
fun SkillsConfigScreen(onBack: () -> Unit) {
    var storeEnabled by remember { mutableStateOf(AppConfigManager.skillStoreEnabled) }
    var skillUrls by remember { mutableStateOf(AppConfigManager.mcpServers) }

    ConfigScaffold("Skills", onBack) {
        InfoCard(
            title = "Skill System",
            body = "Skills add specialist behavior through prompt files, scripts, and installable packages. Community skills are available from skills.sh. Local skills live in ~/skills/*.md or ~/skills/*.sh."
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Skill Sources")
                ConfigSwitch("Enable Skills Store", "Show skills.sh as the community skill source for one-command installs.", storeEnabled) { storeEnabled = it }

                Spacer(modifier = Modifier.height(8.dp))
                DetailRow("Community Registry", "https://skills.sh")
                DetailRow("Local Prompts", "~/skills/*.md")
                DetailRow("Script Skills", "~/skills/*.sh")
                DetailRow("Install Command", "skills.sh install <skill-name>")
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Available Skills")
                SkillCard("Web Researcher", "Deep research with citations and source comparison.", Icons.Outlined.Cloud)
                SkillCard("Code Reviewer", "Review patches, run tests, check project conventions.", Icons.Outlined.Cloud)
                SkillCard("ClawDroid WhatsApp", "WhatsApp automation skill for channel workflows.", Icons.Outlined.Cloud)
                SkillCard("Workflow Builder", "Create recurring automations from natural language.", Icons.Outlined.Cloud)
                SkillCard("Finance Tracker", "Categorize expenses, forecast budget.", Icons.Outlined.Cloud)
                SkillCard("Study Buddy", "Generate flashcards, quiz, explain concepts.", Icons.Outlined.Cloud)
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Custom Skill URLs")
                GlassTextField(
                    value = skillUrls,
                    onValueChange = { skillUrls = it },
                    placeholder = "https://skills.sh/my-custom-skill\nhttps://github.com/user/skill-repo",
                    singleLine = false,
                    maxLines = 4,
                )
            }
        }

        SaveConfigButton {
            AppConfigManager.skillStoreEnabled = storeEnabled
            AppConfigManager.mcpServers = skillUrls.trim()
        }
    }
}

@Composable
fun AutomationsConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var heartbeatEnabled by remember { mutableStateOf(AppConfigManager.heartbeatEnabled) }
    var interval by remember { mutableStateOf(AppConfigManager.heartbeatIntervalMin) }
    var ultraAgent by remember { mutableStateOf(AppConfigManager.ultraAgentEnabled) }
    var approvalMode by remember { mutableStateOf(AppConfigManager.approvalMode) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {}

    ConfigScaffold("Automations", onBack) {
        InfoCard(
            title = "Background Automation",
            body = "Control recurring heartbeat scans, foreground service behavior, approval modes, and overall agent autonomy in the background.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Heartbeat Scanner")
                ConfigSwitch("Autonomous Heartbeat", "Scan heartbeat.md task lists on a recurring schedule. Agent processes pending tasks autonomously.", heartbeatEnabled) { heartbeatEnabled = it }

                AnimatedVisibility(visible = heartbeatEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Cron Presets")
                        ConfigChoice("Every 15 min", "Fast checks for active projects and urgent reminders.", interval == 15) { interval = 15 }
                        ConfigChoice("Every 30 min", "Balanced recurring checks without too much background work.", interval == 30) { interval = 30 }
                        ConfigChoice("Hourly", "Lightweight monitoring for normal daily use.", interval == 60) { interval = 60 }
                        ConfigChoice("Every 2 hours", "Low-touch background automation.", interval == 120) { interval = 120 }

                        Text(
                            "Scan Interval: ${interval}m",
                            color = EmberOrange,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = interval.toFloat(),
                            onValueChange = { interval = it.toInt() },
                            valueRange = 15f..120f,
                            steps = 7,
                            colors = configSliderColors(),
                        )
                        DetailRow("Task File", "heartbeat.md in project sandbox root")
                        DetailRow("Scheduler", "WorkManager (survives app restart)")
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Approval Mode")
                Text(
                    "Controls when the agent needs your explicit approval before acting.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
                ConfigChoice("Default", "Sandbox full auto. Ask before connected services.", approvalMode == "default") { approvalMode = "default" }
                ConfigChoice("Trusted", "Sandbox and connected services run with minimal friction.", approvalMode == "trusted") { approvalMode = "trusted" }
                ConfigChoice("Cautious", "Ask for installs, destructive work, and connected services.", approvalMode == "cautious") { approvalMode = "cautious" }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfigSwitch("Ultra Agent Mode", "Foreground service + extra device permissions. Agent runs 24/7, listening on channels and processing heartbeats.", ultraAgent) {
                    ultraAgent = it
                    if (it) {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                            && !Settings.canDrawOverlays(context)
                        ) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                }
                Text(
                    "Use Cautious approval mode if you want installs and risky actions to pause for confirmation.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SaveConfigButton {
            AppConfigManager.heartbeatEnabled = heartbeatEnabled
            AppConfigManager.heartbeatIntervalMin = interval
            AppConfigManager.ultraAgentEnabled = ultraAgent
            AppConfigManager.approvalMode = approvalMode
            if (heartbeatEnabled) {
                com.clawdroid.app.core.automation.AutomationScheduler.schedule(context)
            }
            if (ultraAgent) {
                com.clawdroid.app.core.service.ServiceManager.start(context)
            } else {
                com.clawdroid.app.core.service.ServiceManager.stop(context)
            }
        }
    }
}

@Composable
fun AgentConfigScreen(onBack: () -> Unit) {
    var agentName by remember { mutableStateOf(AppConfigManager.agentName) }
    var agentPersonality by remember { mutableStateOf(AppConfigManager.agentPersonality) }
    var agentPurpose by remember { mutableStateOf(AppConfigManager.agentPurpose) }
    var ownerName by remember { mutableStateOf(AppConfigManager.ownerName) }
    var ownerInfo by remember { mutableStateOf(AppConfigManager.ownerInfo) }
    var maxTurns by remember { mutableStateOf(AppConfigManager.maxAgentTurns) }
    var approvalMode by remember { mutableStateOf(AppConfigManager.approvalMode) }
    var dynamicThinking by remember { mutableStateOf(AppConfigManager.dynamicThinkingEnabled) }
    var promptEnhancement by remember { mutableStateOf(AppConfigManager.promptEnhancementEnabled) }
    var emojiTone by remember { mutableStateOf(AppConfigManager.emojiToneEnabled) }
    
    // Prompt Files
    var agentsMd by remember { mutableStateOf(AppConfigManager.agentsMd) }
    var soulMd by remember { mutableStateOf(AppConfigManager.soulMd) }
    var toolsMd by remember { mutableStateOf(AppConfigManager.toolsMd) }
    var skillMd by remember { mutableStateOf(AppConfigManager.skillMd) }
    var claudeMd by remember { mutableStateOf(AppConfigManager.claudeMd) }

    ConfigScaffold("Agent Configuration", onBack) {
        InfoCard(
            title = "Agent Calibration",
            body = "Customize how your agent behaves, what it knows about you, and how much autonomy it has. These settings are injected into the system prompt.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Identity")
                GlassTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    placeholder = "Agent name (e.g. Nova, Aria, Cortex)",
                )
                GlassTextField(
                    value = agentPersonality,
                    onValueChange = { agentPersonality = it },
                    placeholder = "Personality (e.g. calm senior engineer, cyberpunk hacker)",
                )
                GlassTextField(
                    value = agentPurpose,
                    onValueChange = { agentPurpose = it },
                    placeholder = "Primary purpose (e.g. System controls & diagnostics)",
                )
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Prompt Instructions (Markdown)")
                
                GlassTextField(
                    value = agentsMd,
                    onValueChange = { agentsMd = it },
                    placeholder = "AGENTS.md - High level routing and agent selection logic",
                    singleLine = false,
                    maxLines = 4,
                )
                
                GlassTextField(
                    value = soulMd,
                    onValueChange = { soulMd = it },
                    placeholder = "SOUL.md - Deep personality traits, ethics, and style guidelines",
                    singleLine = false,
                    maxLines = 4,
                )
                
                GlassTextField(
                    value = toolsMd,
                    onValueChange = { toolsMd = it },
                    placeholder = "TOOLS.md - Tool execution rules and constraints",
                    singleLine = false,
                    maxLines = 4,
                )
                
                GlassTextField(
                    value = skillMd,
                    onValueChange = { skillMd = it },
                    placeholder = "SKILL.md - Core competencies and domain knowledge",
                    singleLine = false,
                    maxLines = 4,
                )
                
                GlassTextField(
                    value = claudeMd,
                    onValueChange = { claudeMd = it },
                    placeholder = "CLAUDE.md - System-level base prompt overrides",
                    singleLine = false,
                    maxLines = 4,
                )
                
                Text(
                    "These contents are read by the Agent Engine during prompt assembly.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Owner Context")
                GlassTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    placeholder = "Your name",
                )
                GlassTextField(
                    value = ownerInfo,
                    onValueChange = { ownerInfo = it },
                    placeholder = "Your preferences, work style, recurring context",
                    singleLine = false,
                    maxLines = 4,
                )
                Text(
                    "Owner context is injected at the start of every conversation so the agent remembers who you are.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Behavior")

                ConfigSwitch("Dynamic Thinking Phrases", "Task-aware processing messages. Shows contextual thinking phrases based on what the agent is doing (coding, researching, editing).", dynamicThinking) { dynamicThinking = it }
                ConfigSwitch("Prompt Improvement", "Refines app-control tasks internally before sending them to the agent. The refined prompt stays hidden from chat.", promptEnhancement) { promptEnhancement = it }
                ConfigSwitch("Emoji Tone Conversion", "Emojis are stripped from speech and converted into subtle tone/emotion hints in the voice output.", emojiTone) { emojiTone = it }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Max Agent Turns: $maxTurns",
                    color = EmberOrange,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = maxTurns.toFloat(),
                    onValueChange = { maxTurns = it.toInt() },
                    valueRange = 20f..300f,
                    steps = 13,
                    colors = configSliderColors(),
                )
                Text(
                    "Higher values allow the agent to take more autonomous steps before returning to you. Default: 200.",
                    color = MutedGray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Approval Mode")
                ConfigChoice("Default", "Sandbox: full auto. Connected services: ask first.", approvalMode == "default") { approvalMode = "default" }
                ConfigChoice("Trusted", "Sandbox & services: full auto. Minimal friction.", approvalMode == "trusted") { approvalMode = "trusted" }
                ConfigChoice("Cautious", "Ask for installs, destructive actions, and external services.", approvalMode == "cautious") { approvalMode = "cautious" }
            }
        }

        SaveConfigButton {
            AppConfigManager.agentName = agentName.trim().ifBlank { "Nova" }
            AppConfigManager.agentPersonality = agentPersonality.trim().ifBlank { "Professional" }
            AppConfigManager.agentPurpose = agentPurpose.trim().ifBlank { "General assistant" }
            AppConfigManager.ownerName = ownerName.trim()
            AppConfigManager.ownerInfo = ownerInfo.trim()
            AppConfigManager.maxAgentTurns = maxTurns
            AppConfigManager.approvalMode = approvalMode
            AppConfigManager.dynamicThinkingEnabled = dynamicThinking
            AppConfigManager.promptEnhancementEnabled = promptEnhancement
            AppConfigManager.emojiToneEnabled = emojiTone
            
            AppConfigManager.agentsMd = agentsMd.trim()
            AppConfigManager.soulMd = soulMd.trim()
            AppConfigManager.toolsMd = toolsMd.trim()
            AppConfigManager.skillMd = skillMd.trim()
            AppConfigManager.claudeMd = claudeMd.trim()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConfigScreenHeader(title)
            content()
        }
    }
}

@Composable
private fun ConfigScreenHeader(title: String) {
    val icon = when {
        title.contains("Audio", ignoreCase = true) -> Icons.Outlined.Headphones
        title.contains("Connector", ignoreCase = true) -> Icons.Outlined.Link
        title.contains("Automation", ignoreCase = true) -> Icons.Outlined.CalendarMonth
        title.contains("Agent", ignoreCase = true) -> Icons.Outlined.Security
        title.contains("Skill", ignoreCase = true) -> Icons.Outlined.Extension
        else -> Icons.Outlined.Tag
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Configure ClawDroid behavior", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ConfigChoice(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.64f))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ConfigSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        GlassTextField(
            value = value,
            onValueChange = onChange,
            placeholder = label,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SkillCard(
    title: String,
    description: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.68f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Text(
            "install",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SaveConfigButton(onSave: () -> Unit) {
    val context = LocalContext.current
    GlassButton(
        onClick = {
            onSave()
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Changes", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun configSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
)
