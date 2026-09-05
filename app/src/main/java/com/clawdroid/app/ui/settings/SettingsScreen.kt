package com.clawdroid.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.control.AndroidControlTools
import com.clawdroid.app.core.control.ScreenCaptureManager
import com.clawdroid.app.core.control.ScreenReaderService
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.voice.PiperEngine
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.GlowText
import com.clawdroid.app.ui.components.PiperDownloadDialog
import kotlinx.coroutines.launch
import java.util.Locale
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

// ── Model presets ────────────────────────────────────────────────────────

private data class ModelPreset(val id: String, val label: String, val description: String)

private val modelPresets = listOf(
    ModelPreset("gpt-4o", "GPT-4o", "Best for complex reasoning & coding"),
    ModelPreset("gpt-4o-mini", "GPT-4o Mini", "Fast & lightweight for daily tasks"),
)

// ── TTS engine options ──────────────────────────────────────────────────

private data class TtsEngineOption(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

private val ttsEngineOptions = listOf(
    TtsEngineOption("device", "On-Device (Android TTS)", "Built-in system TTS, works offline", Icons.Outlined.Android),
    TtsEngineOption("openai", "OpenAI TTS", "6 voices: alloy, echo, fable, onyx, nova, shimmer", Icons.Outlined.Cloud),
    TtsEngineOption("elevenlabs", "ElevenLabs TTS", "Premium neural voices (Rachel, Domi, Josh…)", Icons.Outlined.Cloud),
    TtsEngineOption("deepgram", "Deepgram TTS", "12 voices: Asteria, Luna, Orion, Zeus…", Icons.Outlined.Cloud),
)

private val openaiVoices = listOf(
    "alloy" to "Alloy (Versatile)",
    "echo" to "Echo (Male / Warm)",
    "fable" to "Fable (British / Narrative)",
    "onyx" to "Onyx (Deep / Male)",
    "nova" to "Nova (Female / Warm)",
    "shimmer" to "Shimmer (Female / Clear)",
)

private val realtimeVoices = listOf(
    "marin" to "Marin",
    "cedar" to "Cedar",
)

// ── Main Screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        onBack()
    }
    var baseUrl by remember { mutableStateOf(AppConfigManager.baseUrl) }
    var apiKey by remember { mutableStateOf(AppConfigManager.apiKey) }
    var model by remember { mutableStateOf(AppConfigManager.model) }
    var showKey by remember { mutableStateOf(false) }
    var useCustomModel by remember {
        mutableStateOf(modelPresets.none { it.id == AppConfigManager.model })
    }

    var ttsEngine by remember { mutableStateOf(AppConfigManager.ttsEngine) }
    var ttsVoice by remember { mutableStateOf(AppConfigManager.ttsVoice) }
    var ttsSpeed by remember { mutableStateOf(AppConfigManager.ttsSpeed) }
    var realtimeVoiceEnabled by remember { mutableStateOf(AppConfigManager.realtimeVoiceEnabled) }
    var realtimeVoiceModel by remember { mutableStateOf(AppConfigManager.realtimeVoiceModel) }
    var realtimeVoiceVoice by remember { mutableStateOf(AppConfigManager.realtimeVoiceVoice) }

    var openaiTtsApiKey by remember { mutableStateOf(AppConfigManager.openaiTtsApiKey) }
    var openaiRealtimeApiKey by remember { mutableStateOf(AppConfigManager.openaiRealtimeApiKey) }
    var elevenlabsApiKey by remember { mutableStateOf(AppConfigManager.elevenlabsApiKey) }
    var deepgramApiKey by remember { mutableStateOf(AppConfigManager.deepgramApiKey) }

    var saved by remember { mutableStateOf(false) }
    var storagePermitted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Piper engine for optional download
    val piperEngine = remember { PiperEngine(context.applicationContext) }
    val piperDownloadProgress by piperEngine.downloadProgress.collectAsState()
    val piperInstalled = piperEngine.isInstalled
    val piperDownloading = piperDownloadProgress > 0f && piperDownloadProgress < 1f

    // Test voice TTS
    var testTts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { }
        testTts = tts
        onDispose { tts.shutdown() }
    }
    var isUltraAgentEnabled by remember { mutableStateOf(AppConfigManager.ultraAgentEnabled) }
    var showWarningDialog by remember { mutableStateOf(false) }

    var whatsappEnabled by remember { mutableStateOf(AppConfigManager.whatsappEnabled) }
    var whatsappAllowedContacts by remember { mutableStateOf(AppConfigManager.whatsappAllowedContacts) }
    var smsEnabled by remember { mutableStateOf(AppConfigManager.smsEnabled) }
    var heartbeatEnabled by remember { mutableStateOf(AppConfigManager.heartbeatEnabled) }
    var heartbeatIntervalMin by remember { mutableStateOf(AppConfigManager.heartbeatIntervalMin) }
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var accessibilityActive by remember { mutableStateOf(ScreenReaderService.instance != null) }
    var screenCaptureActive by remember { mutableStateOf(ScreenCaptureManager.isActive()) }
    var showScreenTestDialog by remember { mutableStateOf(false) }
    var screenTestResult by remember { mutableStateOf("") }
    var screenTestLoading by remember { mutableStateOf(false) }

    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val ok = ScreenCaptureManager.startCapture(context, result.resultCode, result.data!!)
            screenCaptureActive = ok
            Toast.makeText(
                context,
                if (ok) "Screen capture active" else "Failed to start screen capture",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val saveAndSync = {
        AppConfigManager.save(baseUrl.trim(), apiKey.trim(), model.trim())
        AppConfigManager.ttsEngine = ttsEngine
        AppConfigManager.ttsVoice = ttsVoice.trim()
        AppConfigManager.ttsSpeed = ttsSpeed
        AppConfigManager.realtimeVoiceEnabled = realtimeVoiceEnabled
        AppConfigManager.realtimeVoiceModel = realtimeVoiceModel.trim().ifBlank { "gpt-realtime-2" }
        AppConfigManager.realtimeVoiceVoice = realtimeVoiceVoice.trim().ifBlank { "marin" }
        AppConfigManager.openaiTtsApiKey = openaiTtsApiKey.trim()
        AppConfigManager.openaiRealtimeApiKey = openaiRealtimeApiKey.trim()
        AppConfigManager.elevenlabsApiKey = elevenlabsApiKey.trim()
        AppConfigManager.deepgramApiKey = deepgramApiKey.trim()
        AppConfigManager.ultraAgentEnabled = isUltraAgentEnabled
        AppConfigManager.whatsappEnabled = whatsappEnabled
        AppConfigManager.whatsappAllowedContacts = whatsappAllowedContacts.trim()
        AppConfigManager.smsEnabled = smsEnabled
        AppConfigManager.heartbeatEnabled = heartbeatEnabled
        AppConfigManager.heartbeatIntervalMin = heartbeatIntervalMin
        AppConfigManager.syncToSandbox(context)
    }

    // Check notification listener access on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cn = ComponentName(context, com.clawdroid.app.core.channels.ClawNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                notificationAccessGranted = flat != null && flat.contains(cn.flattenToString())
                accessibilityActive = ScreenReaderService.instance != null
                screenCaptureActive = ScreenCaptureManager.isActive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Toast.makeText(context, "Permissions updated.", Toast.LENGTH_SHORT).show()
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermitted = if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    val requestUltraAgentPermissions = {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Enable System Alert Window", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }

        val accessibilityIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(accessibilityIntent)
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SoftWhite,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBlack,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── AI Provider ──────────────────────────────────
            GlowText(
                text = "AI Provider",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Base URL", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    GlassTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; saved = false },
                        placeholder = "https://openrouter.ai/api/v1",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )

                    Text("API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    GlassTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        placeholder = "sk-…",
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Text(
                                text = if (showKey) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmberOrange,
                                modifier = Modifier
                                    .clickable { showKey = !showKey }
                                    .padding(8.dp),
                            )
                        },
                    )

                    Text("Model", style = MaterialTheme.typography.labelLarge, color = EmberOrange)

                    // Model preset cards (onboarding-style)
                    modelPresets.forEach { preset ->
                        val isSelected = !useCustomModel && model == preset.id
                        SelectableCard(
                            label = preset.label,
                            description = preset.description,
                            isSelected = isSelected,
                            onClick = {
                                model = preset.id
                                useCustomModel = false
                                saved = false
                            },
                        )
                    }

                    // Custom model toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (useCustomModel) GlassFillStrong else GlassFill)
                            .border(1.dp, if (useCustomModel) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                            .clickable { useCustomModel = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✏️ Custom Model",
                            color = SoftWhite,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (useCustomModel) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmberOrange,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    AnimatedVisibility(visible = useCustomModel) {
                        GlassTextField(
                            value = if (useCustomModel) model else "",
                            onValueChange = { model = it; saved = false },
                            placeholder = "e.g. anthropic/claude-3.5-sonnet",
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Voice & Speech ───────────────────────────────
            GlowText(
                text = "Voice & Speech",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Realtime Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            Text(
                                text = "Use OpenAI Realtime for live call sessions when native WebRTC transport is available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }
                        Switch(
                            checked = realtimeVoiceEnabled,
                            onCheckedChange = {
                                realtimeVoiceEnabled = it
                                saved = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SoftWhite,
                                checkedTrackColor = EmberOrange,
                            ),
                        )
                    }

                    AnimatedVisibility(visible = realtimeVoiceEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Realtime API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            Text(
                                text = "Uses OPENAI_REALTIME_API_KEY from .env unless you enter a key here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                            GlassTextField(
                                value = openaiRealtimeApiKey,
                                onValueChange = { openaiRealtimeApiKey = it; saved = false },
                                placeholder = "sk-... (leave blank to use .env)",
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )

                            Text("Realtime Model", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            GlassTextField(
                                value = realtimeVoiceModel,
                                onValueChange = { realtimeVoiceModel = it; saved = false },
                                placeholder = "gpt-realtime-2",
                            )

                            Text("Realtime Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            realtimeVoices.forEach { (id, label) ->
                                val isSelected = realtimeVoiceVoice == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) GlassFillStrong else GlassFill)
                                        .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                        .clickable { realtimeVoiceVoice = id; saved = false }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        color = SoftWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = EmberOrange,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("TTS Engine", style = MaterialTheme.typography.labelLarge, color = EmberOrange)

                    ttsEngineOptions.forEach { option ->
                        val isSelected = ttsEngine == option.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) GlassFillStrong else GlassFill)
                                .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(14.dp))
                                .clickable { ttsEngine = option.id; saved = false }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected) EmberOrange else MutedGray,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    color = SoftWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = option.description,
                                    color = MutedGray,
                                    fontSize = 12.sp,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = EmberOrange,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // ── Engine-specific config ───────────────────────────
                    when (ttsEngine) {
                        "openai" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("OpenAI API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            Text(
                                text = "Uses a dedicated OpenAI TTS key, or falls back to the main API key above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                            GlassTextField(
                                value = openaiTtsApiKey,
                                onValueChange = { openaiTtsApiKey = it; saved = false },
                                placeholder = "sk-… (leave blank to reuse main API key)",
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("OpenAI Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            openaiVoices.forEach { (id, label) ->
                                val isSelected = ttsVoice == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) GlassFillStrong else GlassFill)
                                        .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                        .clickable { ttsVoice = id; saved = false }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        color = SoftWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = EmberOrange,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        "elevenlabs" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ElevenLabs API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            GlassTextField(
                                value = elevenlabsApiKey,
                                onValueChange = { elevenlabsApiKey = it; saved = false },
                                placeholder = "Enter your ElevenLabs API key",
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("ElevenLabs Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            com.clawdroid.app.core.voice.ElevenLabsTtsEngine.PRESET_VOICES.forEach { (id, label) ->
                                val isSelected = ttsVoice == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) GlassFillStrong else GlassFill)
                                        .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                        .clickable { ttsVoice = id; saved = false }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        color = SoftWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = EmberOrange,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        "deepgram" -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Deepgram API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            GlassTextField(
                                value = deepgramApiKey,
                                onValueChange = { deepgramApiKey = it; saved = false },
                                placeholder = "Enter your Deepgram API key",
                                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Deepgram Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            com.clawdroid.app.core.voice.DeepgramTtsEngine.PRESET_VOICES.forEach { (id, label) ->
                                val isSelected = ttsVoice == id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) GlassFillStrong else GlassFill)
                                        .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                        .clickable { ttsVoice = id; saved = false }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        color = SoftWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = EmberOrange,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Speech Speed: ${String.format("%.1fx", ttsSpeed)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmberOrange,
                    )
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { ttsSpeed = it; saved = false },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = EmberOrange,
                            activeTrackColor = EmberOrange,
                            inactiveTrackColor = GlassBorderDim,
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Piper download card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (piperInstalled) GlassFillStrong.copy(alpha = 0.5f) else GlassFill)
                            .border(1.dp, if (piperInstalled) EmberOrange.copy(alpha = 0.4f) else GlassBorderDim, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (piperInstalled) Icons.Rounded.CheckCircle else Icons.Outlined.Headphones,
                                contentDescription = null,
                                tint = if (piperInstalled) EmberOrange else MutedGray,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (piperInstalled) "Piper Neural Voice Installed" else "Piper Neural TTS",
                                    color = SoftWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = if (piperInstalled) "Ryan — Male US (realistic, offline)" else "Download 50MB male voice model for realistic speech",
                                    color = MutedGray,
                                    fontSize = 12.sp,
                                )
                            }
                            if (!piperInstalled) {
                                GlassButton(
                                    onClick = { piperEngine.startDownload() },
                                    modifier = Modifier.width(100.dp).height(36.dp),
                                ) {
                                    Text("Download", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SoftWhite)
                                }
                            }
                        }
                    }

                    // Test Voice button
                    GlassButton(
                        onClick = {
                            testTts?.let { tts ->
                                tts.language = Locale.US
                                tts.setPitch(0.75f)
                                tts.setSpeechRate(0.82f * ttsSpeed)
                                tts.speak(
                                    "Hello, I am ${AppConfigManager.agentName}. This is my voice.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "test"
                                )
                            }
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Headphones,
                                contentDescription = null,
                                tint = SoftWhite,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔊 Test Voice", fontWeight = FontWeight.SemiBold, color = SoftWhite)
                        }
                    }
                }
            }

            // Piper download progress dialog
            if (piperDownloading) {
                PiperDownloadDialog(progress = piperDownloadProgress)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Agent ────────────────────────────────────────
            GlowText(
                text = "Agent",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = MutedGray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Approval Mode", style = MaterialTheme.typography.bodyMedium, color = MutedGray)
                        }
                        Text("Default", style = MaterialTheme.typography.bodyMedium, color = SoftWhite, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                tint = MutedGray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sandbox", style = MaterialTheme.typography.bodyMedium, color = MutedGray)
                        }
                        Text("Full Auto", style = MaterialTheme.typography.bodyMedium, color = SoftWhite, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ULTRA AGENT Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUltraAgentEnabled) Color(0xFFEF5350) else SoftWhite,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Grant autonomous device execution",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }
                        Switch(
                            checked = isUltraAgentEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showWarningDialog = true
                                } else {
                                    isUltraAgentEnabled = false
                                    AppConfigManager.ultraAgentEnabled = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFEF5350),
                                checkedTrackColor = Color(0xFFEF5350).copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = DeepBlack,
                            ),
                        )
                    }

                    Text(
                        text = "Configure how much autonomy the agent has.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray.copy(alpha = 0.7f),
                    )
                }
            }

            if (showWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showWarningDialog = false },
                    title = {
                        Text(
                            text = "⚠️ WARNING: ULTRA AGENT",
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    text = {
                        Text(
                            text = "Ultra Agent mode grants the AI permission to automatically run commands, access external APIs, and execute administrative functions on your device without approval. Continue?",
                            color = SoftWhite,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        GlassButton(
                            onClick = {
                                showWarningDialog = false
                                isUltraAgentEnabled = true
                                AppConfigManager.ultraAgentEnabled = true
                                requestUltraAgentPermissions()
                            },
                        ) {
                            Text("YES, ENABLE", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWarningDialog = false }) {
                            Text("CANCEL", color = SoftWhite)
                        }
                    },
                    containerColor = DeepBlack,
                    tonalElevation = 6.dp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Android Control ─────────────────────────────
            GlowText(
                text = "Android Control",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accessibility Service",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (accessibilityActive) "Screen control active" else "Required for UI tree reading and gestures",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (accessibilityActive) Color(0xFF66BB6A) else MutedGray,
                            )
                        }
                        if (accessibilityActive) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    GlassButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (accessibilityActive) "Manage Accessibility" else "Enable Accessibility Access",
                            color = SoftWhite,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Screen Capture",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (screenCaptureActive) "Vision fallback active" else "Fallback when UI tree is empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (screenCaptureActive) Color(0xFF66BB6A) else MutedGray,
                            )
                        }
                        if (screenCaptureActive) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassButton(
                            onClick = {
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Grant Capture", color = SoftWhite, fontWeight = FontWeight.SemiBold)
                        }
                        if (screenCaptureActive) {
                            GlassButton(
                                onClick = {
                                    ScreenCaptureManager.stopCapture()
                                    screenCaptureActive = false
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Stop Capture", color = SoftWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    GlassButton(
                        onClick = {
                            screenTestLoading = true
                            scope.launch {
                                val result = AndroidControlTools.getScreen(context).toString(2)
                                screenTestResult = if (result.length > 8000) {
                                    result.take(8000) + "\n…(truncated)"
                                } else {
                                    result
                                }
                                screenTestLoading = false
                                showScreenTestDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !screenTestLoading,
                    ) {
                        Text(
                            if (screenTestLoading) "Reading screen…" else "Test Screen Read",
                            color = SoftWhite,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (showScreenTestDialog) {
                AlertDialog(
                    onDismissRequest = { showScreenTestDialog = false },
                    title = {
                        Text("Screen Read Result", color = SoftWhite, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = screenTestResult.ifBlank { "No result" },
                                color = SoftWhite,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showScreenTestDialog = false }) {
                            Text("CLOSE", color = SoftWhite)
                        }
                    },
                    containerColor = DeepBlack,
                    tonalElevation = 6.dp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Skills & Channels ───────────────────────────
            GlowText(
                text = "Skills & Channels",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // WhatsApp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "WhatsApp Automation (Channel)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Draft and send responses autonomously",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }
                        Switch(
                            checked = whatsappEnabled,
                            onCheckedChange = { whatsappEnabled = it; saved = false },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmberOrange,
                                checkedTrackColor = EmberOrange.copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = DeepBlack,
                            ),
                        )
                    }

                    AnimatedVisibility(visible = whatsappEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!notificationAccessGranted) {
                                GlassButton(
                                    onClick = {
                                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Grant Notification Access", color = EmberOrange, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = EmberOrange,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Notification Access Granted", color = EmberOrange, fontWeight = FontWeight.Medium)
                                }
                            }

                            Text("Allowed Contacts", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            GlassTextField(
                                value = whatsappAllowedContacts,
                                onValueChange = { whatsappAllowedContacts = it; saved = false },
                                placeholder = "e.g. John Doe, Alice Smith (leave empty for all)",
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorderDim))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Heartbeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Autonomous Heartbeat (Skill)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Run tasks list inside heartbeat.md files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }
                        Switch(
                            checked = heartbeatEnabled,
                            onCheckedChange = { heartbeatEnabled = it; saved = false },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmberOrange,
                                checkedTrackColor = EmberOrange.copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = DeepBlack,
                            ),
                        )
                    }

                    AnimatedVisibility(visible = heartbeatEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Checklist Scan Interval: ${heartbeatIntervalMin}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoftWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = heartbeatIntervalMin.toFloat(),
                                onValueChange = { heartbeatIntervalMin = it.toInt(); saved = false },
                                valueRange = 15f..120f,
                                steps = 7, // 15, 30, 45, 60, 75, 90, 105, 120
                                colors = SliderDefaults.colors(
                                    thumbColor = EmberOrange,
                                    activeTrackColor = EmberOrange,
                                    inactiveTrackColor = GlassBorderDim
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Background Agent ─────────────────────────────
            GlowText(
                text = "Background Agent",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("24/7 Background Mode", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            Text(
                                text = "Agent runs in foreground service, listens on channels, processes heartbeats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }
                        Switch(
                            checked = isUltraAgentEnabled,
                            onCheckedChange = { isUltraAgentEnabled = it; saved = false },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmberOrange,
                                checkedTrackColor = EmberOrange.copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = DeepBlack,
                            ),
                        )
                    }

                    if (isUltraAgentEnabled) {
                        // Service status
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("●", color = EmberOrange, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Service active — channels connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                            )
                        }

                        // WhatsApp channel toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WhatsApp Channel", color = SoftWhite, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text("Connect via WhatsApp Web", style = MaterialTheme.typography.bodySmall, color = MutedGray)
                            }
                            Switch(
                                checked = whatsappEnabled,
                                onCheckedChange = { whatsappEnabled = it; saved = false },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmberOrange,
                                    checkedTrackColor = EmberOrange.copy(alpha = 0.5f),
                                    uncheckedThumbColor = MutedGray,
                                    uncheckedTrackColor = DeepBlack,
                                ),
                            )
                        }

                        if (whatsappEnabled) {
                            Text("Allowed Contacts", style = MaterialTheme.typography.bodySmall, color = MutedGray)
                            GlassTextField(
                                value = whatsappAllowedContacts,
                                onValueChange = { whatsappAllowedContacts = it; saved = false },
                                placeholder = "e.g. John Doe, Alice Smith",
                            )
                        }

                        // SMS channel toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SMS Channel", color = SoftWhite, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text("Read and reply to SMS", style = MaterialTheme.typography.bodySmall, color = MutedGray)
                            }
                            Switch(
                                checked = smsEnabled,
                                onCheckedChange = { smsEnabled = it; saved = false },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmberOrange,
                                    checkedTrackColor = EmberOrange.copy(alpha = 0.5f),
                                    uncheckedThumbColor = MutedGray,
                                    uncheckedTrackColor = DeepBlack,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorderDim))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Agent config management
                        Text("Agent Configuration", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                        Text(
                            text = "Config is stored in the sandbox as agent_config.json. Skills are loaded from ~/skills/*.md files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGray,
                        )
                        GlassButton(onClick = {
                            saveAndSync()
                            Toast.makeText(context, "Config saved to ~/agent_config.json", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Export Config", fontWeight = FontWeight.SemiBold, color = SoftWhite)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── File Storage ─────────────────────────────────
            GlowText(
                text = "File Storage",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Shared Folder", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    Text(
                        text = "Documents/ClawDroid/Inbox, Output, Projects, Exports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray,
                    )

                    if (!storagePermitted) {
                        GlassButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= 30) {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    storagePermissionLauncher.launch(intent)
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    )
                                }
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    tint = SoftWhite,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enable File Access", fontWeight = FontWeight.SemiBold, color = SoftWhite)
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmberOrange,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("File access granted", color = EmberOrange, fontWeight = FontWeight.Medium)
                        }
                    }

                    Text(
                        text = "Agent saves downloaded voices, session files, and exported data to the shared folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── About ────────────────────────────────────────
            GlowText(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MutedGray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Version", style = MaterialTheme.typography.bodyMedium, color = MutedGray)
                        }
                        Text("0.1.0", style = MaterialTheme.typography.bodyMedium, color = SoftWhite, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = MutedGray,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Architecture", style = MaterialTheme.typography.bodyMedium, color = MutedGray)
                        }
                        Text("Kotlin + Compose", style = MaterialTheme.typography.bodyMedium, color = SoftWhite, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "Built with Kotlin, Jetpack Compose, and Material 3.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Save button ──────────────────────────────────
            SaveButton(
                saved = saved,
                enabled = apiKey.isNotBlank(),
                onClick = {
                    saveAndSync()
                    if (heartbeatEnabled) {
                        com.clawdroid.app.core.automation.AutomationScheduler.schedule(context)
                    }
                    // Start/stop background service based on toggle
                    if (isUltraAgentEnabled) {
                        com.clawdroid.app.core.service.ServiceManager.start(context)
                    } else {
                        com.clawdroid.app.core.service.ServiceManager.stop(context)
                    }
                    saved = true
                },
            )

            if (!apiKey.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "API Key is required for the agent to work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ── Selectable Card (reusable for models, etc.) ─────────────────────────

@Composable
private fun SelectableCard(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) GlassFillStrong else GlassFill)
            .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = SoftWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = description,
                color = MutedGray,
                fontSize = 12.sp,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = EmberOrange,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Animated Save Button ────────────────────────────────────────────────

@Composable
private fun SaveButton(
    saved: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val saveAlpha by animateFloatAsState(
        targetValue = if (saved) 0.6f else 1f,
        animationSpec = tween(300),
        label = "save_alpha",
    )

    GlassButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = saved,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = SoftWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (!saved) {
                Icon(
                    imageVector = Icons.Rounded.Save,
                    contentDescription = null,
                    tint = SoftWhite,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (saved) "Saved!" else "Save Changes",
                fontWeight = FontWeight.SemiBold,
                color = SoftWhite,
                modifier = Modifier.alpha(saveAlpha),
            )
        }
    }
}
