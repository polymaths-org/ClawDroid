package com.clawdroid.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.control.AndroidControlTools
import com.clawdroid.app.core.control.ScreenCaptureManager
import com.clawdroid.app.core.control.ScreenReaderService
import com.clawdroid.app.core.service.ServiceManager
import com.clawdroid.app.core.voice.SherpaOnnxTtsEngine
import com.clawdroid.app.core.voice.OfflineTtsDownloadManager
import com.clawdroid.app.core.voice.OfflineTtsModelCatalog
import com.clawdroid.app.core.voice.VoiceManager
import com.clawdroid.app.data.api.AiProviders
import com.clawdroid.app.data.api.ModelDiscoveryClient
import com.clawdroid.app.data.api.ProviderModel
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.GlowText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    TtsEngineOption("sherpa", "Sherpa-ONNX", "Optional offline neural voice, falls back safely", Icons.Outlined.Headphones),
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
    var providerId by remember { mutableStateOf(AppConfigManager.provider) }
    var selectedProvider by remember(providerId) { mutableStateOf(AiProviders.byId(providerId)) }
    var showKey by remember { mutableStateOf(false) }
    var useCustomModel by remember { mutableStateOf(false) }

    var ttsEngine by remember { mutableStateOf(AppConfigManager.ttsEngine) }
    var ttsVoice by remember { mutableStateOf(AppConfigManager.ttsVoice) }
    var ttsSpeed by remember { mutableStateOf(AppConfigManager.ttsSpeed) }
    var realtimeVoiceEnabled by remember { mutableStateOf(AppConfigManager.realtimeVoiceEnabled) }
    var realtimeVoiceModel by remember { mutableStateOf(AppConfigManager.realtimeVoiceModel) }
    var realtimeVoiceVoice by remember { mutableStateOf(AppConfigManager.realtimeVoiceVoice) }
    var showTtsEngineSheet by remember { mutableStateOf(false) }
    var showAdvancedVoice by remember { mutableStateOf(false) }

    var openaiTtsApiKey by remember { mutableStateOf(AppConfigManager.openaiTtsApiKey) }
    var openaiRealtimeApiKey by remember { mutableStateOf(AppConfigManager.openaiRealtimeApiKey) }
    var elevenlabsApiKey by remember { mutableStateOf(AppConfigManager.elevenlabsApiKey) }
    var deepgramApiKey by remember { mutableStateOf(AppConfigManager.deepgramApiKey) }

    var saved by remember { mutableStateOf(false) }
    var storagePermitted by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var offlineTtsModelId by remember { mutableStateOf(OfflineTtsModelCatalog.byId(AppConfigManager.offlineTtsModelId).id) }
    var offlineTtsSpeakerId by remember { mutableStateOf(AppConfigManager.offlineTtsSpeakerId) }
    var voiceTestStatus by remember { mutableStateOf("") }
    val offlineTtsManager = remember { OfflineTtsDownloadManager(context.applicationContext) }
    val offlineTtsState by offlineTtsManager.state.collectAsState()
    val offlineTtsPreset = OfflineTtsModelCatalog.byId(offlineTtsModelId)
    val modelDiscoveryClient = remember { ModelDiscoveryClient(context.applicationContext) }
    var discoveredModels by remember {
        mutableStateOf(modelDiscoveryClient.cachedModels(providerId, baseUrl, apiKey))
    }
    var modelSearch by remember { mutableStateOf("") }
    var modelFetchStatus by remember { mutableStateOf("") }
    var modelFetchLoading by remember { mutableStateOf(false) }
    var showProviderSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    fun refreshCachedModels() {
        discoveredModels = modelDiscoveryClient.cachedModels(providerId, baseUrl, apiKey)
    }

    fun fetchModels() {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            modelFetchStatus = "Enter Base URL and API key to fetch models."
            return
        }
        modelFetchLoading = true
        modelFetchStatus = "Fetching models…"
        scope.launch {
            val result = modelDiscoveryClient.discover(selectedProvider, baseUrl, apiKey)
            result.fold(
                onSuccess = { models ->
                    discoveredModels = models
                    modelFetchStatus = if (models.isEmpty()) {
                        "No models returned. You can still enter a manual model."
                    } else {
                        "Fetched ${models.size} models."
                    }
                },
                onFailure = { error ->
                    refreshCachedModels()
                    modelFetchStatus = "Fetch failed: ${error.message ?: error::class.java.simpleName}. Cached/manual models are still available."
                },
            )
            modelFetchLoading = false
        }
    }

    // Test voice TTS
    val settingsVoiceManager = remember { VoiceManager(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { settingsVoiceManager.destroy() }
    }
    var isUltraAgentEnabled by remember { mutableStateOf(AppConfigManager.ultraAgentEnabled) }
    var showWarningDialog by remember { mutableStateOf(false) }

    var whatsappEnabled by remember { mutableStateOf(AppConfigManager.whatsappEnabled) }
    var whatsappAllowedContacts by remember { mutableStateOf(AppConfigManager.whatsappAllowedContacts) }
    var smsEnabled by remember { mutableStateOf(AppConfigManager.smsEnabled) }
    var heartbeatEnabled by remember { mutableStateOf(AppConfigManager.heartbeatEnabled) }
    var heartbeatIntervalMin by remember { mutableStateOf(AppConfigManager.heartbeatIntervalMin) }
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var microphoneGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var overlayGranted by remember {
        mutableStateOf(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
    }
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

    LaunchedEffect(providerId, baseUrl, apiKey) {
        refreshCachedModels()
        if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
            delay(800)
            if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                fetchModels()
            }
        }
    }

    val saveAndSync = {
        AppConfigManager.save(
            provider = providerId,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            dialect = selectedProvider.dialect,
        )
        AppConfigManager.ttsEngine = ttsEngine
        AppConfigManager.ttsVoice = ttsVoice.trim()
        AppConfigManager.ttsSpeed = ttsSpeed
        AppConfigManager.offlineTtsModelId = offlineTtsModelId
        AppConfigManager.offlineTtsSpeakerId = offlineTtsSpeakerId
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

    fun selectOfflineVoice(preset: com.clawdroid.app.core.voice.OfflineTtsModelPreset, speakerId: Int = preset.defaultSpeakerId) {
        offlineTtsModelId = preset.id
        offlineTtsSpeakerId = speakerId
        ttsEngine = "sherpa"
        AppConfigManager.ttsEngine = "sherpa"
        AppConfigManager.offlineTtsModelId = preset.id
        AppConfigManager.offlineTtsSpeakerId = speakerId
        offlineTtsManager.refresh(preset)
        voiceTestStatus = ""
        saved = false
    }

    fun testVoice(forceOffline: Boolean = false) {
        if (forceOffline) {
            selectOfflineVoice(offlineTtsPreset, offlineTtsSpeakerId)
        }
        saveAndSync()
        val selectedOfflinePreset = OfflineTtsModelCatalog.byId(AppConfigManager.offlineTtsModelId)
        if (AppConfigManager.ttsEngine == "sherpa") {
            val readiness = SherpaOnnxTtsEngine.readinessMessage(context.applicationContext, selectedOfflinePreset)
            if (readiness != "Ready") {
                voiceTestStatus = readiness
                Toast.makeText(context, readiness, Toast.LENGTH_LONG).show()
                return
            }
            voiceTestStatus = "Testing ${selectedOfflinePreset.label}..."
        } else {
            voiceTestStatus = "Testing ${ttsEngineOptions.firstOrNull { it.id == AppConfigManager.ttsEngine }?.label ?: "selected voice"}..."
        }
        settingsVoiceManager.stop()
        settingsVoiceManager.speak(
            "Hello, I am ${AppConfigManager.agentName}. This is the selected ClawDroid voice.",
        ) {
            scope.launch {
                voiceTestStatus = "Voice test finished"
            }
        }
    }

    // Check notification listener access on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cn = ComponentName(context, com.clawdroid.app.core.channels.ClawNotificationListenerService::class.java)
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                notificationAccessGranted = flat != null && flat.contains(cn.flattenToString())
                microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                notificationPermissionGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                overlayGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
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
        microphoneGranted = permissions[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        notificationPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS]
                ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        } else {
            true
        }
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
                    Text("Provider", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    SelectableCard(
                        label = selectedProvider.label,
                        description = selectedProvider.description,
                        isSelected = true,
                        onClick = { showProviderSheet = true },
                    )

                    Text("Base URL", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    GlassTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; saved = false },
                        placeholder = selectedProvider.defaultBaseUrl.ifBlank { "https://example.com/v1" },
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
                    SelectableCard(
                        label = model.ifBlank { "Select model" },
                        description = if (discoveredModels.isNotEmpty()) {
                            "${discoveredModels.size} discovered models available"
                        } else {
                            "Fetch models or enter a manual model ID"
                        },
                        isSelected = model.isNotBlank() && !useCustomModel,
                        onClick = { showModelSheet = true },
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GlassButton(
                            onClick = { fetchModels() },
                            enabled = !modelFetchLoading && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (modelFetchLoading) "Fetching…" else "Fetch Models",
                                color = SoftWhite,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        GlassButton(
                            onClick = { useCustomModel = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Manual", color = SoftWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (modelFetchStatus.isNotBlank()) {
                        Text(
                            text = modelFetchStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (modelFetchStatus.startsWith("Fetch failed")) MaterialTheme.colorScheme.error else MutedGray,
                        )
                    }

                    if (!selectedProvider.supportsRuntime) {
                        Text(
                            text = "${selectedProvider.label} model discovery is available, but native chat runtime is pending. Choose an OpenAI-compatible provider for active chats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFB74D),
                        )
                    }

                    AnimatedVisibility(visible = useCustomModel || model.isBlank()) {
                        GlassTextField(
                            value = model,
                            onValueChange = { model = it; useCustomModel = true; saved = false },
                            placeholder = when (selectedProvider.id) {
                                "gemini" -> "gemini-2.5-pro"
                                "anthropic" -> "claude-sonnet-4-5"
                                "deepseek" -> "deepseek-chat"
                                else -> "e.g. openai/gpt-4o"
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Runtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGray,
                        )
                        Text(
                            text = selectedProvider.dialect.name.replace("_", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftWhite,
                            fontWeight = FontWeight.Medium,
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

                    val selectedTtsOption = ttsEngineOptions.firstOrNull { it.id == ttsEngine } ?: ttsEngineOptions.first()
                    SelectableCard(
                        label = selectedTtsOption.label,
                        description = selectedTtsOption.description,
                        isSelected = true,
                        onClick = { showTtsEngineSheet = true },
                    )

                    SettingSwitchRow(
                        title = "Advanced voice options",
                        subtitle = "Cloud keys, realtime voice, and provider-specific voices",
                        checked = showAdvancedVoice,
                        onCheckedChange = { showAdvancedVoice = it },
                    )

                    // ── Engine-specific config ───────────────────────────
                    AnimatedVisibility(visible = showAdvancedVoice) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                    // Sherpa setup card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectOfflineVoice(offlineTtsPreset, offlineTtsSpeakerId) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (offlineTtsState.installed) GlassFillStrong.copy(alpha = 0.5f) else GlassFill)
                            .border(
                                1.dp,
                                if (ttsEngine == "sherpa") EmberOrange else if (offlineTtsState.installed) EmberOrange.copy(alpha = 0.4f) else GlassBorderDim,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ttsEngine == "sherpa") Icons.Rounded.CheckCircle else Icons.Outlined.Headphones,
                                    contentDescription = null,
                                    tint = if (ttsEngine == "sherpa") EmberOrange else MutedGray,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Offline Neural Voice",
                                        color = SoftWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = "${offlineTtsPreset.label} • ${if (ttsEngine == "sherpa") "selected" else "tap to use"}",
                                        color = MutedGray,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = if (offlineTtsState.installed) "Installed" else "Not installed",
                                    color = if (offlineTtsState.installed) EmberOrange else MutedGray,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }

                            Text(
                                text = offlineTtsState.message,
                                color = if (offlineTtsState.installed) EmberOrange else MutedGray,
                                fontSize = 12.sp,
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OfflineTtsModelCatalog.presets.forEach { preset ->
                                    SelectableCard(
                                        label = preset.label,
                                        description = when (preset.id) {
                                            "vits-piper-en_US-glados" -> "Runtime-compatible offline VITS voice"
                                            else -> "Offline Sherpa-ONNX model"
                                        },
                                        isSelected = offlineTtsModelId == preset.id,
                                        onClick = {
                                            selectOfflineVoice(preset)
                                        },
                                    )
                                }
                            }

                            if (offlineTtsState.downloading) {
                                LinearProgressIndicator(
                                    progress = { offlineTtsState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = EmberOrange,
                                    trackColor = GlassBorderDim,
                                )
                            }

                            Text("Voice", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                offlineTtsPreset.speakerLabels.forEach { (id, label) ->
                                    SelectableCard(
                                        label = label,
                                        description = "Speaker $id",
                                        isSelected = offlineTtsSpeakerId == id,
                                        onClick = {
                                            selectOfflineVoice(offlineTtsPreset, id)
                                        },
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                GlassButton(
                                    onClick = {
                                        selectOfflineVoice(offlineTtsPreset, offlineTtsSpeakerId)
                                        offlineTtsManager.install(offlineTtsPreset)
                                    },
                                    enabled = !offlineTtsState.downloading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (offlineTtsState.installed) "Reinstall" else "Install",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = SoftWhite,
                                    )
                                }
                                GlassButton(
                                    onClick = {
                                        offlineTtsManager.delete(offlineTtsPreset)
                                        voiceTestStatus = "${offlineTtsPreset.label} deleted"
                                    },
                                    enabled = !offlineTtsState.downloading && offlineTtsState.installed,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SoftWhite)
                                }
                            }

                            GlassButton(
                                onClick = { testVoice(forceOffline = true) },
                                enabled = !offlineTtsState.downloading,
                            ) {
                                Text("Test Offline Voice", fontWeight = FontWeight.SemiBold, color = SoftWhite)
                            }
                        }
                    }

                    if (voiceTestStatus.isNotBlank()) {
                        Text(
                            text = voiceTestStatus,
                            color = if (voiceTestStatus.contains("failed", ignoreCase = true) || voiceTestStatus.contains("not ", ignoreCase = true) || voiceTestStatus.contains("Unsupported", ignoreCase = true)) MutedGray else EmberOrange,
                            fontSize = 12.sp,
                        )
                    }

                    // Test Voice button
                    GlassButton(
                        onClick = {
                            testVoice(forceOffline = false)
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
                            Text("Test Selected Voice", fontWeight = FontWeight.SemiBold, color = SoftWhite)
                        }
                    }
                }
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

            // ── Permissions ──────────────────────────────────
            GlowText(
                text = "Permissions",
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

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorderDim))

                    PermissionActionRow(
                        title = "Microphone",
                        status = if (microphoneGranted) "Granted" else "Needed for voice input",
                        granted = microphoneGranted,
                        buttonText = if (microphoneGranted) "Manage" else "Grant",
                        onClick = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        },
                    )

                    PermissionActionRow(
                        title = "Notifications",
                        status = if (notificationPermissionGranted && notificationAccessGranted) {
                            "App and listener access ready"
                        } else {
                            "Needed for channels and background updates"
                        },
                        granted = notificationPermissionGranted && notificationAccessGranted,
                        buttonText = "Manage",
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            } else {
                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }
                        },
                    )

                    PermissionActionRow(
                        title = "Overlay",
                        status = if (overlayGranted) "Granted" else "Needed for floating agent controls",
                        granted = overlayGranted,
                        buttonText = "Manage",
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                    )

                    PermissionActionRow(
                        title = "Accessibility",
                        status = if (accessibilityActive) "Screen control active" else "Needed for UI tree and gestures",
                        granted = accessibilityActive,
                        buttonText = "Manage",
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    )

                    PermissionActionRow(
                        title = "Screen Capture",
                        status = if (screenCaptureActive) "Capture session active" else "Optional visual fallback",
                        granted = screenCaptureActive,
                        buttonText = if (screenCaptureActive) "Stop" else "Grant",
                        onClick = {
                            if (screenCaptureActive) {
                                ScreenCaptureManager.stopCapture()
                                screenCaptureActive = false
                            } else {
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            }
                        },
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
                    Text(
                        text = "Built by Team Polymaths",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Save button ──────────────────────────────────
            SaveButton(
                saved = saved,
                enabled = apiKey.isNotBlank() && model.isNotBlank() && selectedProvider.supportsRuntime,
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
            if (apiKey.isNotBlank() && !selectedProvider.supportsRuntime) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${selectedProvider.label} model discovery works, but native chat runtime is pending. Select OpenAI, OpenRouter, DeepSeek, OpenCode, or Custom to save active chat settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showProviderSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProviderSheet = false },
            containerColor = DeepBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Choose Provider", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                AiProviders.presets.forEach { preset ->
                    SelectableCard(
                        label = preset.label,
                        description = preset.description,
                        isSelected = providerId == preset.id,
                        onClick = {
                            providerId = preset.id
                            selectedProvider = preset
                            if (preset.defaultBaseUrl.isNotBlank()) {
                                baseUrl = preset.defaultBaseUrl
                            }
                            model = ""
                            useCustomModel = false
                            modelFetchStatus = ""
                            discoveredModels = modelDiscoveryClient.cachedModels(preset.id, preset.defaultBaseUrl.ifBlank { baseUrl }, apiKey)
                            saved = false
                            showProviderSheet = false
                        },
                    )
                }
            }
        }
    }

    if (showModelSheet) {
        val filteredModels = discoveredModels.filter { providerModel ->
            val query = modelSearch.trim()
            query.isBlank() ||
                providerModel.id.contains(query, ignoreCase = true) ||
                providerModel.label.contains(query, ignoreCase = true) ||
                providerModel.owner?.contains(query, ignoreCase = true) == true
        }
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            containerColor = DeepBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Choose Model", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                GlassTextField(
                    value = modelSearch,
                    onValueChange = { modelSearch = it },
                    placeholder = "Search models",
                )
                if (filteredModels.isEmpty()) {
                    Text(
                        text = if (discoveredModels.isEmpty()) "No fetched models yet. Fetch models or use manual entry." else "No models match your search.",
                        color = MutedGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredModels) { providerModel ->
                            ModelRow(
                                providerModel = providerModel,
                                isSelected = providerModel.id == model,
                                onClick = {
                                    model = providerModel.id
                                    useCustomModel = false
                                    saved = false
                                    showModelSheet = false
                                },
                            )
                        }
                    }
                }
                GlassButton(
                    onClick = {
                        useCustomModel = true
                        showModelSheet = false
                    },
                ) {
                    Text("Enter Model Manually", color = SoftWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showTtsEngineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTtsEngineSheet = false },
            containerColor = DeepBlack,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Choose TTS Engine", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                ttsEngineOptions.forEach { option ->
                    SelectableCard(
                        label = option.label,
                        description = option.description,
                        isSelected = ttsEngine == option.id,
                        onClick = {
                            ttsEngine = option.id
                            saved = false
                            showTtsEngineSheet = false
                        },
                    )
                }
            }
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

@Composable
private fun ModelRow(
    providerModel: ProviderModel,
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
                text = providerModel.id,
                color = SoftWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = listOfNotNull(providerModel.owner, providerModel.description).joinToString(" • ")
                    .ifBlank { providerModel.label },
                color = MutedGray,
                fontSize = 11.sp,
                maxLines = 2,
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

@Composable
private fun PermissionActionRow(
    title: String,
    status: String,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Outlined.Security,
            contentDescription = null,
            tint = if (granted) EmberOrange else MutedGray,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(status, color = MutedGray, style = MaterialTheme.typography.bodySmall)
        }
        GlassButton(
            onClick = onClick,
            modifier = Modifier.width(96.dp).height(36.dp),
        ) {
            Text(buttonText, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
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
