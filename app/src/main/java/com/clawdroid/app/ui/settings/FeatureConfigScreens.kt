package com.clawdroid.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.voice.WakeVoiceService
import com.clawdroid.app.core.voice.WhisperModelManager
import com.clawdroid.app.ui.components.AnimatedPresetCard
import com.clawdroid.app.ui.components.ChannelConnectionStatus
import com.clawdroid.app.ui.components.ChannelStatusCard
import com.clawdroid.app.ui.components.ClawSkinBackground
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
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

@Composable
fun AudioConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ttsEngine by remember { mutableStateOf(AppConfigManager.ttsEngine) }
    var ttsVoice by remember { mutableStateOf(AppConfigManager.ttsVoice) }
    var ttsSpeed by remember { mutableStateOf(AppConfigManager.ttsSpeed) }
    var openaiKey by remember { mutableStateOf(AppConfigManager.openaiTtsApiKey) }
    var elevenlabsKey by remember { mutableStateOf(AppConfigManager.elevenlabsApiKey) }
    var deepgramKey by remember { mutableStateOf(AppConfigManager.deepgramApiKey) }
    var realtimeEnabled by remember { mutableStateOf(AppConfigManager.realtimeVoiceEnabled) }
    var realtimeModel by remember { mutableStateOf(AppConfigManager.realtimeVoiceModel) }
    var realtimeVoice by remember { mutableStateOf(AppConfigManager.realtimeVoiceVoice) }
    var realtimeKey by remember { mutableStateOf(AppConfigManager.openaiRealtimeApiKey) }
    var speechEngine by remember { mutableStateOf(AppConfigManager.speechRecognitionEngine) }
    var speechLanguage by remember { mutableStateOf(AppConfigManager.speechRecognitionLanguage) }
    var speechLanguageSwitch by remember { mutableStateOf(AppConfigManager.speechLanguageSwitchEnabled) }
    var speechPreferOnDevice by remember { mutableStateOf(AppConfigManager.speechPreferOnDevice) }
    var whisperModel by remember { mutableStateOf(AppConfigManager.whisperModelSize) }
    var whisperDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var whisperDownloadStatus by remember { mutableStateOf("") }
    var whisperRuntimeStatus by remember { mutableStateOf(WhisperModelManager.runtimeStatus(context)) }
    var whisperRuntimeInstalling by remember { mutableStateOf(false) }
    var wakeOnVoice by remember { mutableStateOf(AppConfigManager.wakeOnVoiceEnabled) }
    var wakeDetectionMode by remember { mutableStateOf(AppConfigManager.wakeDetectionMode) }
    var wakePhrase by remember { mutableStateOf(AppConfigManager.wakePhrase.ifBlank { "Hey ${AppConfigManager.agentName}" }) }
    var voiceNoiseGateEnabled by remember { mutableStateOf(AppConfigManager.voiceNoiseGateEnabled) }
    var voiceNoiseGate by remember { mutableStateOf(AppConfigManager.voiceNoiseGate) }
    var voiceInterruptThreshold by remember { mutableStateOf(AppConfigManager.voiceInterruptThreshold) }
    var dynamicThinking by remember { mutableStateOf(AppConfigManager.dynamicThinkingEnabled) }
    var emojiTone by remember { mutableStateOf(AppConfigManager.emojiToneEnabled) }
    var piperEnabled by remember { mutableStateOf(AppConfigManager.mcpEnabled) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

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
                    color = MaterialTheme.colorScheme.primary,
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

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Realtime Conversation")
                ConfigSwitch("Realtime Voice", "Use a streaming speech-to-speech provider when available.", realtimeEnabled) { realtimeEnabled = it }
                AnimatedVisibility(visible = realtimeEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassTextField(
                            value = realtimeModel,
                            onValueChange = { realtimeModel = it },
                            placeholder = "Realtime model, e.g. gpt-realtime-2",
                        )
                        GlassTextField(
                            value = realtimeVoice,
                            onValueChange = { realtimeVoice = it },
                            placeholder = "Voice, e.g. marin",
                        )
                        SecretField("OpenAI Realtime API key", realtimeKey) { realtimeKey = it }
                        DetailRow("Behavior", "Streams speech while responses are still being generated")
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Speech Recognition")
                ConfigChoice("Android Fast", "Newer low-latency recognizer used for quick voice chat turns.", speechEngine == "system") { speechEngine = "system" }
                ConfigChoice("Android Legacy", "Older Android recognizer behavior with fewer custom timing hints.", speechEngine == "system_legacy") { speechEngine = "system_legacy" }
                ConfigChoice("Whisper.cpp", "Local model path and downloader for native Whisper integration.", speechEngine == "whisper") { speechEngine = "whisper" }
                ConfigSwitch("Prefer On-device", "Use Android on-device recognition first when the OS provides it.", speechPreferOnDevice) { speechPreferOnDevice = it }
                ConfigSwitch("Multilingual Switch", "Enable Android language detection and switching on supported Android versions.", speechLanguageSwitch) { speechLanguageSwitch = it }
                SectionTitle("Language")
                ConfigChoice("Auto", "Use device language and multilingual switching.", speechLanguage == "auto") { speechLanguage = "auto" }
                ConfigChoice("English", "en-US", speechLanguage == "en-US") { speechLanguage = "en-US" }
                ConfigChoice("Hindi", "hi-IN", speechLanguage == "hi-IN") { speechLanguage = "hi-IN" }
                ConfigChoice("Spanish", "es-ES", speechLanguage == "es-ES") { speechLanguage = "es-ES" }
            }
        }

        AnimatedVisibility(visible = speechEngine == "whisper") {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Whisper.cpp Model")
                    DetailRow("Runtime", whisperRuntimeStatus)
                    WhisperModelManager.models.forEach { model ->
                        val downloaded = WhisperModelManager.isDownloaded(context, model.id)
                        ConfigChoice(
                            model.label,
                            "${model.approximateSize}${if (downloaded) " · downloaded" else ""}",
                            whisperModel == model.id,
                        ) { whisperModel = model.id }
                    }
                    GlassButton(
                        onClick = {
                            val modelId = whisperModel
                            whisperDownloadStatus = "Downloading ${WhisperModelManager.modelInfo(modelId).label}..."
                            whisperDownloadProgress = 0f
                            scope.launch {
                                WhisperModelManager.download(context, modelId) { progress ->
                                    whisperDownloadProgress = progress
                                }.onSuccess {
                                    whisperDownloadStatus = "Downloaded ${WhisperModelManager.modelInfo(modelId).label}"
                                    whisperDownloadProgress = null
                                }.onFailure { error ->
                                    whisperDownloadStatus = error.message ?: "Download failed"
                                    whisperDownloadProgress = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text("Download Selected Model", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    GlassButton(
                        onClick = {
                            whisperRuntimeInstalling = true
                            whisperRuntimeStatus = "Installing whisper.cpp runtime..."
                            scope.launch {
                                WhisperModelManager.installRuntime(context) { progress ->
                                    whisperRuntimeStatus = progress
                                }.onSuccess {
                                    whisperRuntimeStatus = WhisperModelManager.runtimeStatus(context)
                                    whisperRuntimeInstalling = false
                                }.onFailure { error ->
                                    whisperRuntimeStatus = error.message ?: "Runtime install failed"
                                    whisperRuntimeInstalling = false
                                }
                            }
                        },
                        enabled = !whisperRuntimeInstalling,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(
                            if (whisperRuntimeInstalling) "Installing Runtime..." else "Install Whisper.cpp Runtime",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (whisperDownloadProgress != null || whisperDownloadStatus.isNotBlank()) {
                        Text(
                            text = whisperDownloadProgress?.let { "${(it * 100).toInt()}% · $whisperDownloadStatus" } ?: whisperDownloadStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Wake on Voice")
                ConfigSwitch("Voice Wake", "Open voice chat from Android assistant handoff or a background wake listener.", wakeOnVoice) {
                    wakeOnVoice = it
                    if (it && wakeDetectionMode == "background") {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                AnimatedVisibility(visible = wakeOnVoice) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigChoice("System Assistant", "No ClawDroid background microphone. Set ClawDroid as the default assistant and use the system gesture or power shortcut.", wakeDetectionMode == "system") {
                            wakeDetectionMode = "system"
                        }
                        ConfigChoice("Background Listener", "Actually listens for the wake phrase in the background. Android will show microphone usage while active.", wakeDetectionMode == "background") {
                            wakeDetectionMode = "background"
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        GlassTextField(
                            value = wakePhrase,
                            onValueChange = { wakePhrase = it },
                            placeholder = "Hey ${AppConfigManager.agentName}",
                        )
                        if (wakeDetectionMode == "background") {
                            DetailRow("Background Mic", "On. Required for custom wake phrases on normal Android apps.")
                        } else {
                            DetailRow("Background Mic", "Off. Android must invoke ClawDroid first; arbitrary custom hotwords are OS/vendor controlled.")
                            GlassButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                                    }.onFailure {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                                        }.onFailure {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                            ) {
                                Text("Open Default Assistant Settings", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Noise Filtering")
                ConfigSwitch("Voice Noise Gate", "Ignore low-level noise before treating microphone input as speech.", voiceNoiseGateEnabled) {
                    voiceNoiseGateEnabled = it
                }
                AnimatedVisibility(visible = voiceNoiseGateEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Pickup threshold: ${(voiceNoiseGate * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = voiceNoiseGate,
                            onValueChange = { voiceNoiseGate = it },
                            valueRange = 0.03f..0.60f,
                            steps = 18,
                            colors = configSliderColors(),
                        )
                        Text(
                            "Interrupt threshold: ${(voiceInterruptThreshold * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = voiceInterruptThreshold,
                            onValueChange = { voiceInterruptThreshold = it },
                            valueRange = 0.08f..0.80f,
                            steps = 18,
                            colors = configSliderColors(),
                        )
                        DetailRow("Lower", "Picks up quiet speech but may react to room noise")
                        DetailRow("Higher", "Needs louder speech and ignores more background sound")
                    }
                }
            }
        }

        SaveConfigButton {
            AppConfigManager.ttsEngine = ttsEngine
            AppConfigManager.ttsVoice = ttsVoice.trim()
            AppConfigManager.ttsSpeed = ttsSpeed
            AppConfigManager.openaiTtsApiKey = openaiKey.trim()
            AppConfigManager.elevenlabsApiKey = elevenlabsKey.trim()
            AppConfigManager.deepgramApiKey = deepgramKey.trim()
            AppConfigManager.realtimeVoiceEnabled = realtimeEnabled
            AppConfigManager.realtimeVoiceModel = realtimeModel.trim()
            AppConfigManager.realtimeVoiceVoice = realtimeVoice.trim()
            AppConfigManager.openaiRealtimeApiKey = realtimeKey.trim()
            AppConfigManager.speechRecognitionEngine = speechEngine
            AppConfigManager.speechRecognitionLanguage = speechLanguage
            AppConfigManager.speechLanguageSwitchEnabled = speechLanguageSwitch
            AppConfigManager.speechPreferOnDevice = speechPreferOnDevice
            AppConfigManager.whisperModelSize = whisperModel
            AppConfigManager.wakeOnVoiceEnabled = wakeOnVoice
            AppConfigManager.wakeDetectionMode = wakeDetectionMode
            AppConfigManager.wakePhrase = wakePhrase.trim().ifBlank { "Hey ${AppConfigManager.agentName}" }
            AppConfigManager.voiceNoiseGateEnabled = voiceNoiseGateEnabled
            AppConfigManager.voiceNoiseGate = voiceNoiseGate
            AppConfigManager.voiceInterruptThreshold = voiceInterruptThreshold
            AppConfigManager.dynamicThinkingEnabled = dynamicThinking
            AppConfigManager.emojiToneEnabled = emojiTone
            if (wakeOnVoice && wakeDetectionMode == "background") {
                WakeVoiceService.start(context)
            } else {
                WakeVoiceService.stop(context)
            }
        }
    }
}

@Composable
fun NotificationConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(AppConfigManager.notificationsEnabled) }
    var taskStartedEnabled by remember { mutableStateOf(AppConfigManager.taskStartedNotificationsEnabled) }
    var taskFailedEnabled by remember { mutableStateOf(AppConfigManager.taskFailedNotificationsEnabled) }
    var completionMode by remember { mutableStateOf(AppConfigManager.taskCompletionNotificationMode) }
    var askListenSeconds by remember { mutableStateOf(AppConfigManager.notificationAskListenSeconds.toFloat()) }

    ConfigScaffold("Notifications", onBack) {
        InfoCard(
            title = "Notification Control",
            body = "Control task progress alerts and choose whether task completion is a simple notification or an ASK prompt.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Delivery")
                ConfigSwitch("Agent Notifications", "Master switch for ClawDroid task and agent alerts.", notificationsEnabled) {
                    notificationsEnabled = it
                }
                AnimatedVisibility(visible = notificationsEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConfigSwitch("Task Started", "Notify when a background task begins.", taskStartedEnabled) {
                            taskStartedEnabled = it
                        }
                        ConfigSwitch("Task Failed", "Notify when a task stops or needs attention.", taskFailedEnabled) {
                            taskFailedEnabled = it
                        }
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Task Complete")
                ConfigChoice("Notify", "Show a normal completion notification.", completionMode == "notify") {
                    completionMode = "notify"
                }
                ConfigChoice("ASK", "Ask whether the task should be marked done, with notification actions and optional voice.", completionMode == "ask") {
                    completionMode = "ask"
                }
                ConfigChoice("Silent", "Do not notify when a task completes.", completionMode == "silent") {
                    completionMode = "silent"
                }
                AnimatedVisibility(visible = completionMode == "ask") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Voice response window: ${askListenSeconds.toInt()}s",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = askListenSeconds,
                            onValueChange = { askListenSeconds = it },
                            valueRange = 3f..30f,
                            steps = 26,
                            colors = configSliderColors(),
                        )
                        DetailRow("ASK Behavior", "Notifications show Mark done, Needs work, and Voice actions. Voice opens only a timed overlay listener.")
                    }
                }
            }
        }

        SaveConfigButton {
            AppConfigManager.notificationsEnabled = notificationsEnabled
            AppConfigManager.taskStartedNotificationsEnabled = taskStartedEnabled
            AppConfigManager.taskFailedNotificationsEnabled = taskFailedEnabled
            AppConfigManager.taskCompletionNotificationMode = completionMode
            AppConfigManager.notificationAskListenSeconds = askListenSeconds.toInt()
            NotificationHelper.ensureChannels(context)
            Toast.makeText(context, "Notification settings saved.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ThemeConfigScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf(AppConfigManager.appTheme) }
    val themes = remember {
        listOf(
            ThemePreset("claw_magic", "Material You", "Adaptive phone-color accents, deep AI surfaces, and soft glass controls.", listOf(Color(0xFF090909), Color(0xFF161D1E), Color(0xFF44D8F1), Color(0xFFFFB87B))),
            ThemePreset("light", "Light", "Bright Material 3 surfaces for daytime use.", listOf(Color(0xFFF7F9FC), Color(0xFFD6E3FF), Color(0xFF245FA8))),
            ThemePreset("dark", "Dark", "Default focused dark surface.", listOf(Color(0xFF111416), Color(0xFF323537), Color(0xFFD3E2FF))),
            ThemePreset("minimalist", "Minimalist", "Quiet grayscale controls with reduced visual noise.", listOf(Color(0xFF101112), Color(0xFF2B2F32), Color(0xFFE8EAED))),
            ThemePreset("liquid_glass_light", "Liquid Glass Light", "Apple-style translucent white glass with blue system accents.", listOf(Color(0xFFFFFFFF), Color(0xFFEAF4FF), Color(0xFF007AFF), Color(0xFFAF52DE))),
            ThemePreset("liquid_glass_dark", "Liquid Glass Dark", "Deep translucent glass with luminous blue, violet, and soft highlights.", listOf(Color(0xFF05070A), Color(0xFF1C1C1E), Color(0xFF64D2FF), Color(0xFFBF5AF2))),
            ThemePreset("cyberpunk", "Cyberpunk", "Neon magenta/cyan HUD surfaces, scanlines, and high-contrast agent chrome.", listOf(Color(0xFF04010A), Color(0xFF18E6FF), Color(0xFFFF4FDB), Color(0xFFFFD166))),
            ThemePreset("jarvis", "JARVIS", "Precision cyan interface with tactical HUD framing and holographic panels.", listOf(Color(0xFF01080B), Color(0xFF23E7FF), Color(0xFF8BD7E6), Color(0xFFFFC857))),
        )
    }

    ConfigScaffold("Themes", onBack) {
        InfoCard(
            title = "Application Theme",
            body = "Choose the visual treatment for ClawDroid. Theme changes apply immediately and persist for future launches.",
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Theme Presets")
                themes.forEach { preset ->
                    ThemeChoiceCard(
                        preset = preset,
                        selected = selectedTheme == preset.id,
                        onClick = {
                            selectedTheme = preset.id
                            AppConfigManager.appTheme = preset.id
                        },
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
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.08f), shape)
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
    var screenContextMode by remember { mutableStateOf(AppConfigManager.screenContextMode) }
    var visualFallback by remember { mutableStateOf(AppConfigManager.visualContextFallbackEnabled) }
    var saveScreenshots by remember { mutableStateOf(AppConfigManager.saveScreenshotsToHistory) }
    val screenCaptureActive = remember(refreshTick) { ScreenCaptureManager.isActive() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshTick++ }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshTick++ }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val granted = result.resultCode == Activity.RESULT_OK && data != null &&
            ScreenCaptureManager.startCapture(context.applicationContext, result.resultCode, data)
        Toast.makeText(
            context,
            if (granted) "Screen capture is active for assistant vision." else "Screen capture was not started.",
            Toast.LENGTH_SHORT,
        ).show()
        refreshTick++
    }

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
                    description = "Android screen-share prompt for screenshot context.",
                    granted = screenCaptureActive,
                    actionLabel = if (screenCaptureActive) "Restart" else "Start",
                ) {
                    val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Screen Context Provider")
                ConfigChoice("Screen Tree First", "Use accessibility structure first; screenshot only for visual gaps.", screenContextMode == "tree_first") { screenContextMode = "tree_first" }
                ConfigChoice("Screen Tree Only", "No screenshot fallback.", screenContextMode == "tree_only") { screenContextMode = "tree_only" }
                ConfigChoice("Screenshot Only", "Use image context for visual tasks.", screenContextMode == "screenshot_only") { screenContextMode = "screenshot_only" }
                ConfigChoice("Both", "Return tree and screenshot when the model supports vision.", screenContextMode == "both") { screenContextMode = "both" }
                ConfigSwitch("Visual Fallback", "Allow screenshots only when the tree is missing visual context.", visualFallback) { visualFallback = it }
                ConfigSwitch("Save Screenshot History", "Keep visual context snapshots for later review.", saveScreenshots) { saveScreenshots = it }
                DetailRow("Model Support", "Text-only models use tree context; vision-capable models can use screenshots")
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
                    Text("Open App Permission Settings", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        SaveConfigButton {
            AppConfigManager.screenContextMode = screenContextMode
            AppConfigManager.visualContextFallbackEnabled = visualFallback
            AppConfigManager.saveScreenshotsToHistory = saveScreenshots
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
            .background(if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (granted) MaterialTheme.colorScheme.primary else MutedGray.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold)
            Text(description, color = MutedGray, style = MaterialTheme.typography.bodySmall)
            Text(
                if (granted) "Granted" else "Not granted",
                color = if (granted) MaterialTheme.colorScheme.primary else MutedGray,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        GlassButton(onClick = onAction, modifier = Modifier.width(92.dp).height(38.dp)) {
            Text(actionLabel, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
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
                            .border(1.dp, if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else GlassBorderDim, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(def.icon, contentDescription = null, tint = if (isConnected) MaterialTheme.colorScheme.primary else MutedGray, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(def.title, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(def.description, color = MutedGray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isConnected,
                                onCheckedChange = { toggleConnector(def.key, def.title, def.command, def.args) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
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
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
fun InterpoleConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppConfigManager.interpoleEnabled) }
    var connectionType by remember { mutableStateOf(AppConfigManager.interpoleConnectionType) }
    var host by remember { mutableStateOf(AppConfigManager.interpoleHost) }
    var port by remember { mutableStateOf(AppConfigManager.interpolePort.toString()) }
    var defaultEnvironment by remember { mutableStateOf(AppConfigManager.interpoleDefaultEnvironment) }
    var trustMode by remember { mutableStateOf(AppConfigManager.interpoleTrustMode) }
    var trustedFolders by remember { mutableStateOf(AppConfigManager.interpoleTrustedFolders) }
    var allowExecute by remember { mutableStateOf(AppConfigManager.interpoleAllowExecute) }
    var pairedDeviceName by remember { mutableStateOf(AppConfigManager.interpolePairedDeviceName) }
    var showConnect by remember { mutableStateOf(false) }

    val safePort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 8765
    val endpoint = host.trim().takeIf { it.isNotBlank() }?.let { "$it:$safePort" } ?: "Not configured"
    val isPaired = pairedDeviceName.isNotBlank() && host.trim().isNotBlank()

    ConfigScaffold("INTERPOLE", onBack) {
        InfoCard(
            title = "Desktop Bridge",
            body = "Connect one desktop, then keep the advanced trust controls out of the way until the bridge is paired."
        )

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Status")
                StatusLine(if (isPaired && enabled) "Connected desktop configured" else "No desktop connected")
                DetailRow("Desktop", pairedDeviceName.ifBlank { "Not paired" })
                DetailRow("Endpoint", endpoint)
                GlassButton(
                    onClick = { showConnect = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isPaired) "Reconnect Desktop" else "Connect Desktop",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isPaired) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Connection")
                    ConfigSwitch("Enable INTERPOLE", "Allow the agent to use the paired desktop when policy permits it.", enabled) { enabled = it }
                    ConfigChoice("Local", "Use a LAN host or IP on the same network.", connectionType == "local") { connectionType = "local" }
                    ConfigChoice("Tailscale", "Use a Tailscale IP or MagicDNS name for private remote access.", connectionType == "tailscale") { connectionType = "tailscale" }
                    InterpoleTextField(
                        label = "Host",
                        value = host,
                        placeholder = "192.168.1.20 or desktop.tailnet.ts.net",
                        onChange = { host = it },
                    )
                    InterpoleTextField(
                        label = "Port",
                        value = port,
                        placeholder = "8765",
                        keyboardType = KeyboardType.Number,
                        onChange = { port = it.filter(Char::isDigit).take(5) },
                    )
                }
            }
        }

        AnimatedVisibility(visible = isPaired) {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Execution Policy")
                    ConfigChoice("Android sandbox", "Default to the phone sandbox unless the agent explicitly needs the desktop.", defaultEnvironment == "android") { defaultEnvironment = "android" }
                    ConfigChoice("Connected desktop", "Prefer the paired INTERPOLE machine for eligible filesystem and shell work.", defaultEnvironment == "desktop") { defaultEnvironment = "desktop" }
                    ConfigChoice("Zero trust", "Only trusted folders are available, and sensitive actions require approval.", trustMode == "zero_trust") { trustMode = "zero_trust" }
                    ConfigChoice("Trusted", "Allow configured desktop access with minimal approval friction.", trustMode == "trusted") { trustMode = "trusted" }
                    ConfigChoice("Ask every time", "Require confirmation before each desktop action.", trustMode == "ask_every_time") { trustMode = "ask_every_time" }
                    ConfigSwitch("Allow desktop command execution", "Permit signed execute calls on the paired machine.", allowExecute) { allowExecute = it }
                    InterpoleTextField(
                        label = "Trusted folders",
                        value = trustedFolders,
                        placeholder = "/home/me/projects\n/home/me/Documents",
                        singleLine = false,
                        maxLines = 4,
                        onChange = { trustedFolders = it },
                    )
                }
            }
        }

        AnimatedVisibility(visible = isPaired) {
            SaveConfigButton {
                AppConfigManager.interpoleEnabled = enabled
                AppConfigManager.interpoleConnectionType = connectionType
                AppConfigManager.interpoleHost = host.trim()
                AppConfigManager.interpolePort = safePort
                AppConfigManager.interpoleDefaultEnvironment = defaultEnvironment
                AppConfigManager.interpoleTrustMode = trustMode
                AppConfigManager.interpoleTrustedFolders = trustedFolders.trim()
                AppConfigManager.interpoleAllowExecute = allowExecute
                AppConfigManager.interpolePairedDeviceName = pairedDeviceName.trim()
            }
        }
    }

    if (showConnect) {
        InterpoleConnectDialog(
            initialConnectionType = connectionType,
            initialPort = safePort,
            onDismiss = { showConnect = false },
            onConnected = { result ->
                enabled = true
                connectionType = result.connectionType
                host = result.host
                port = result.port.toString()
                pairedDeviceName = result.deviceName
                trustMode = result.trustMode
                AppConfigManager.interpoleEnabled = true
                AppConfigManager.interpoleConnectionType = result.connectionType
                AppConfigManager.interpoleHost = result.host
                AppConfigManager.interpolePort = result.port
                AppConfigManager.interpolePairedDeviceName = result.deviceName
                AppConfigManager.interpoleTrustMode = result.trustMode
                AppConfigManager.interpoleDeviceId = result.deviceId
                AppConfigManager.interpoleDeviceToken = result.deviceToken
                Toast.makeText(context, "INTERPOLE desktop connected", Toast.LENGTH_SHORT).show()
                showConnect = false
            },
        )
    }
}

@Composable
private fun InterpoleConnectDialog(
    initialConnectionType: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onConnected: (InterpolePairingResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("type") }
    var connectionType by remember { mutableStateOf(initialConnectionType) }
    var port by remember { mutableStateOf(initialPort.toString()) }
    var manualHost by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf<InterpoleDiscoveredDevice?>(null) }
    var devices by remember { mutableStateOf<List<InterpoleDiscoveredDevice>>(emptyList()) }
    var pairingCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val safePort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 8765
    val targetHost = selectedDevice?.host ?: manualHost.trim()
    val runCommand = if (connectionType == "tailscale") "interpole auth tailscale" else "interpole auth local"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect INTERPOLE") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    "type" -> {
                        SectionTitle("Connection Type")
                        ConfigChoice("Local", "Scan the current Wi-Fi/LAN for desktops running INTERPOLE.", connectionType == "local") {
                            connectionType = "local"
                            selectedDevice = null
                            status = ""
                        }
                        ConfigChoice("Tailscale", "Enter a Tailscale IP or MagicDNS name after setting up both devices.", connectionType == "tailscale") {
                            connectionType = "tailscale"
                            selectedDevice = null
                            status = ""
                        }
                        InterpoleTextField(
                            label = "Port",
                            value = port,
                            placeholder = "8765",
                            keyboardType = KeyboardType.Number,
                            onChange = { port = it.filter(Char::isDigit).take(5) },
                        )
                        if (connectionType == "tailscale") {
                            InterpoleTextField(
                                label = "Tailscale host",
                                value = manualHost,
                                placeholder = "100.x.y.z or desktop.tailnet.ts.net",
                                onChange = {
                                    manualHost = it
                                    selectedDevice = null
                                },
                            )
                        }
                    }

                    "scan" -> {
                        SectionTitle("Available Desktops")
                        if (devices.isEmpty()) {
                            Text(
                                "No INTERPOLE desktops found. Run $runCommand on the desktop, then scan again or enter the host manually.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
                            )
                        }
                        devices.forEach { device ->
                            ConfigChoice(
                                label = device.label,
                                description = "${device.host}:${device.port} - ${device.mode}",
                                selected = selectedDevice?.host == device.host,
                            ) {
                                selectedDevice = device
                                manualHost = device.host
                            }
                        }
                        InterpoleTextField(
                            label = "Host",
                            value = manualHost,
                            placeholder = "192.168.1.20",
                            onChange = {
                                manualHost = it
                                selectedDevice = null
                            },
                        )
                    }

                    "steps" -> {
                        SectionTitle("Desktop Steps")
                        DetailRow("Desktop", targetHost.ifBlank { "Not selected" })
                        DetailRow("Port", safePort.toString())
                        InterpoleSetupStep("1", "Run the installer from the INTERPOLE folder if this desktop is not installed yet: ./install.sh")
                        InterpoleSetupStep("2", "Run $runCommand and keep that terminal open.")
                        InterpoleSetupStep("3", "Send the request from this phone. The desktop will show a 6-digit code.")
                    }

                    "code" -> {
                        SectionTitle("Enter Desktop Code")
                        DetailRow("Desktop", targetHost)
                        InterpoleTextField(
                            label = "6-digit code",
                            value = pairingCode,
                            placeholder = "123456",
                            keyboardType = KeyboardType.Number,
                            onChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                        )
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Working...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), fontSize = 12.sp)
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        status,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when (step) {
                        "type" -> {
                            if (connectionType == "local") {
                                busy = true
                                status = "Scanning the local network..."
                                scope.launch {
                                    devices = scanInterpoleDevices(safePort)
                                    busy = false
                                    step = "scan"
                                    status = if (devices.isEmpty()) "No devices found. You can enter the desktop IP manually." else "Pick a desktop."
                                }
                            } else if (manualHost.isNotBlank()) {
                                step = "steps"
                                status = ""
                            } else {
                                status = "Enter the desktop Tailscale IP or MagicDNS name."
                            }
                        }
                        "scan" -> {
                            if (targetHost.isBlank()) {
                                status = "Pick a desktop or enter the host manually."
                            } else {
                                step = "steps"
                                status = ""
                            }
                        }
                        "steps" -> {
                            if (targetHost.isBlank()) {
                                status = "Pick or enter a desktop host first."
                            } else {
                                busy = true
                                status = "Sending pairing request..."
                                scope.launch {
                                    requestInterpolePairing(targetHost, safePort, connectionType)
                                        .onSuccess {
                                            step = "code"
                                            status = "Request sent. Enter the 6-digit code shown on the desktop."
                                        }
                                        .onFailure { error ->
                                            status = error.message ?: "Could not send pairing request."
                                        }
                                    busy = false
                                }
                            }
                        }
                        "code" -> {
                            if (pairingCode.length != 6) {
                                status = "Enter the 6-digit code shown on the desktop."
                            } else {
                                busy = true
                                status = "Completing pairing..."
                                scope.launch {
                                    completeInterpolePairing(targetHost, safePort, connectionType, pairingCode)
                                        .onSuccess(onConnected)
                                        .onFailure { error ->
                                            status = error.message ?: "Pairing failed."
                                        }
                                    busy = false
                                }
                            }
                        }
                    }
                },
            ) {
                Text(
                    when (step) {
                        "type" -> if (connectionType == "local") "Scan" else "Next"
                        "steps" -> "Send Request"
                        "code" -> "Pair"
                        else -> "Next"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when (step) {
                        "scan", "steps" -> {
                            step = if (connectionType == "local" && step == "steps") "scan" else "type"
                            status = ""
                        }
                        "code" -> {
                            step = "steps"
                            status = ""
                        }
                        else -> onDismiss()
                    }
                },
            ) {
                Text(if (step == "type") "Cancel" else "Back")
            }
        },
    )
}

@Composable
private fun InterpoleTextField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        GlassTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

@Composable
private fun InterpoleSetupStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        )
    }
}

private data class InterpoleDiscoveredDevice(
    val host: String,
    val port: Int,
    val label: String,
    val mode: String,
)

private data class InterpolePairingResult(
    val connectionType: String,
    val host: String,
    val port: Int,
    val deviceName: String,
    val deviceId: String,
    val deviceToken: String,
    val trustMode: String,
)

private suspend fun scanInterpoleDevices(port: Int): List<InterpoleDiscoveredDevice> = withContext(Dispatchers.IO) {
    val hosts = localIpv4Prefixes()
        .flatMap { prefix -> (1..254).map { "$prefix.$it" } }
        .distinct()
    coroutineScope {
        hosts.map { host ->
            async { probeInterpoleHost(host, port) }
        }.awaitAll()
            .filterNotNull()
            .distinctBy { it.host }
            .sortedBy { it.host }
    }
}

private fun localIpv4Prefixes(): List<String> {
    val prefixes = linkedSetOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val parts = address.hostAddress?.split(".").orEmpty()
                    if (parts.size == 4) {
                        prefixes.add(parts.take(3).joinToString("."))
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return prefixes.toList()
}

private fun probeInterpoleHost(host: String, port: Int): InterpoleDiscoveredDevice? {
    return try {
        val body = rawInterpoleRequest(
            host = host,
            port = port,
            request = buildString {
                append("GET /health HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            },
            timeoutMs = 300,
        )
        val json = org.json.JSONObject(body)
        if (!json.optBoolean("ok") || json.optString("name") != "INTERPOLE") return null
        InterpoleDiscoveredDevice(
            host = host,
            port = port,
            label = "INTERPOLE desktop",
            mode = json.optString("mode", "local"),
        )
    } catch (_: Exception) {
        null
    }
}

private suspend fun requestInterpolePairing(
    host: String,
    port: Int,
    connectionType: String,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val response = postInterpoleJson(
            host = host,
            port = port,
            path = "/v1/pair/request",
            org.json.JSONObject()
                .put("device_name", android.os.Build.MODEL ?: "ClawDroid")
                .put("connection_type", connectionType),
        )
        if (!response.optBoolean("ok")) {
            error(response.optString("error", "Pairing request failed."))
        }
    }
}

private suspend fun completeInterpolePairing(
    host: String,
    port: Int,
    connectionType: String,
    pin: String,
): Result<InterpolePairingResult> = withContext(Dispatchers.IO) {
    runCatching {
        val response = postInterpoleJson(
            host = host,
            port = port,
            path = "/v1/pair/complete",
            org.json.JSONObject()
                .put("pin", pin)
                .put("device_name", android.os.Build.MODEL ?: "ClawDroid")
                .put("requested_trust_mode", AppConfigManager.interpoleTrustMode),
        )
        if (!response.optBoolean("ok")) {
            error(response.optString("error", "Pairing failed."))
        }
        val deviceId = response.optString("device_id")
        val deviceToken = response.optString("device_token")
        if (deviceId.isBlank() || deviceToken.isBlank()) {
            error("Desktop did not return pairing credentials.")
        }
        val trustMode = response.optJSONObject("policy")?.optString("trust_mode") ?: AppConfigManager.interpoleTrustMode
        InterpolePairingResult(
            connectionType = connectionType,
            host = host,
            port = port,
            deviceName = "INTERPOLE desktop",
            deviceId = deviceId,
            deviceToken = deviceToken,
            trustMode = trustMode,
        )
    }
}

private fun postInterpoleJson(
    host: String,
    port: Int,
    path: String,
    payload: org.json.JSONObject,
): org.json.JSONObject {
    val bytes = payload.toString().toByteArray(Charsets.UTF_8)
    val request = buildString {
        append("POST $path HTTP/1.1\r\n")
        append("Host: $host:$port\r\n")
        append("Content-Type: application/json\r\n")
        append("Content-Length: ${bytes.size}\r\n")
        append("Connection: close\r\n")
        append("\r\n")
        append(payload.toString())
    }
    val body = rawInterpoleRequest(host, port, request, timeoutMs = 4000)
    return org.json.JSONObject(body.ifBlank { "{}" })
}

private fun rawInterpoleRequest(
    host: String,
    port: Int,
    request: String,
    timeoutMs: Int,
): String {
    Socket().use { socket ->
        socket.soTimeout = timeoutMs
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        val output = socket.getOutputStream()
        output.write(request.toByteArray(Charsets.UTF_8))
        output.flush()
        val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
        val statusLine = response.lineSequence().firstOrNull().orEmpty()
        if (!statusLine.contains(" 2")) {
            val errorBody = response.substringAfter("\r\n\r\n", "")
            error(errorBody.ifBlank { statusLine.ifBlank { "INTERPOLE request failed." } })
        }
        return response.substringAfter("\r\n\r\n", "")
    }
}

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
    var remindersEnabled by remember { mutableStateOf(AppConfigManager.remindersEnabled) }
    var reminderDelivery by remember { mutableStateOf(AppConfigManager.reminderDefaultDeliveryMode) }
    var scheduledAgentRuns by remember { mutableStateOf(AppConfigManager.scheduledAgentRunsEnabled) }
    var proactiveAssistant by remember { mutableStateOf(AppConfigManager.proactiveAssistantEnabled) }
    var proactiveMode by remember { mutableStateOf(AppConfigManager.proactiveAssistantMode) }
    var proactiveDelivery by remember { mutableStateOf(AppConfigManager.proactiveDeliveryMode) }
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
                            color = MaterialTheme.colorScheme.primary,
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
                SectionTitle("Smart Apps")
                ConfigSwitch("Reminders & Todos", "Allow the agent to save reminders, todos, alarms, and scheduled prompts.", remindersEnabled) { remindersEnabled = it }
                AnimatedVisibility(visible = remindersEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfigChoice("Notification", "Show a normal Android reminder notification.", reminderDelivery == "notification") { reminderDelivery = "notification" }
                        ConfigChoice("Voice", "Notification opens directly into voice chat.", reminderDelivery == "voice") { reminderDelivery = "voice" }
                        ConfigChoice("Both", "Notify and offer voice chat from the reminder.", reminderDelivery == "both") { reminderDelivery = "both" }
                        ConfigSwitch("Scheduled Agent Runs", "Let timed reminders run an agent task automatically when requested.", scheduledAgentRuns) { scheduledAgentRuns = it }
                    }
                }
            }
        }

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Proactive Check-ins")
                ConfigSwitch("Assistant Check-ins", "Let ClawDroid occasionally ask about email, calendar, and daily plans.", proactiveAssistant) { proactiveAssistant = it }
                AnimatedVisibility(visible = proactiveAssistant) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfigChoice("Daily Brief", "Ask what is planned and surface calendar/email context.", proactiveMode == "daily_brief") { proactiveMode = "daily_brief" }
                        ConfigChoice("Email Triage", "Ask before drafting or replying to new important mail.", proactiveMode == "email_triage") { proactiveMode = "email_triage" }
                        ConfigChoice("Work Prompt", "Ask what help is needed when automations wake.", proactiveMode == "work_prompt") { proactiveMode = "work_prompt" }
                        SectionTitle("Delivery")
                        ConfigChoice("Notification", "Use a notification prompt.", proactiveDelivery == "notification") { proactiveDelivery = "notification" }
                        ConfigChoice("Voice", "Open voice chat when the user taps the prompt.", proactiveDelivery == "voice") { proactiveDelivery = "voice" }
                        ConfigChoice("Silent", "Save the check-in for the agent without prompting.", proactiveDelivery == "silent") { proactiveDelivery = "silent" }
                    }
                }
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
            AppConfigManager.remindersEnabled = remindersEnabled
            AppConfigManager.reminderDefaultDeliveryMode = reminderDelivery
            AppConfigManager.scheduledAgentRunsEnabled = scheduledAgentRuns
            AppConfigManager.proactiveAssistantEnabled = proactiveAssistant
            AppConfigManager.proactiveAssistantMode = proactiveMode
            AppConfigManager.proactiveDeliveryMode = proactiveDelivery
            if (heartbeatEnabled) {
                com.clawdroid.app.core.automation.AutomationScheduler.schedule(context)
                com.clawdroid.app.core.automation.AutomationScheduler.runNow(context)
            } else {
                com.clawdroid.app.core.automation.AutomationScheduler.scheduleOrCancel(context)
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
    var responseMode by remember { mutableStateOf(AppConfigManager.agentResponseMode) }
    var dynamicThinking by remember { mutableStateOf(AppConfigManager.dynamicThinkingEnabled) }
    var promptEnhancement by remember { mutableStateOf(AppConfigManager.promptEnhancementEnabled) }
    var emojiTone by remember { mutableStateOf(AppConfigManager.emojiToneEnabled) }
    
    // Prompt Files
    var agentsMd by remember { mutableStateOf(AppConfigManager.agentsMd) }
    var soulMd by remember { mutableStateOf(AppConfigManager.soulMd) }
    var toolsMd by remember { mutableStateOf(AppConfigManager.toolsMd) }
    var skillMd by remember { mutableStateOf(AppConfigManager.skillMd) }
    var systemMd by remember { mutableStateOf(AppConfigManager.systemMd) }

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
                    value = systemMd,
                    onValueChange = { systemMd = it },
                    placeholder = "SYSTEM.md - System-level base prompt overrides",
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

                SectionTitle("Response Style")
                ConfigChoice("Fast", "Short, direct, and to the point.", responseMode == "fast") { responseMode = "fast" }
                ConfigChoice("Thinking", "Shows brief reasoning and tradeoffs before important actions.", responseMode == "thinking") { responseMode = "thinking" }
                ConfigChoice("Balanced", "Clear answers with enough context, without getting long.", responseMode == "balanced") { responseMode = "balanced" }

                Spacer(modifier = Modifier.height(8.dp))
                ConfigSwitch("Dynamic Thinking Phrases", "Task-aware processing messages. Shows contextual thinking phrases based on what the agent is doing (coding, researching, editing).", dynamicThinking) { dynamicThinking = it }
                ConfigSwitch("Prompt Improvement", "Refines app-control tasks internally before sending them to the agent. The refined prompt stays hidden from chat.", promptEnhancement) { promptEnhancement = it }
                ConfigSwitch("Emoji Tone Conversion", "Emojis are stripped from speech and converted into subtle tone/emotion hints in the voice output.", emojiTone) { emojiTone = it }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Max Agent Turns: $maxTurns",
                    color = MaterialTheme.colorScheme.primary,
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
            AppConfigManager.agentResponseMode = responseMode
            AppConfigManager.dynamicThinkingEnabled = dynamicThinking
            AppConfigManager.promptEnhancementEnabled = promptEnhancement
            AppConfigManager.emojiToneEnabled = emojiTone
            
            AppConfigManager.agentsMd = agentsMd.trim()
            AppConfigManager.soulMd = soulMd.trim()
            AppConfigManager.toolsMd = toolsMd.trim()
            AppConfigManager.skillMd = skillMd.trim()
            AppConfigManager.systemMd = systemMd.trim()
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
    ClawSkinBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
                        .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, letterSpacing = 0.sp),
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ConfigScreenHeader(title)
                content()
            }
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
        title.contains("INTERPOLE", ignoreCase = true) -> Icons.Outlined.Storage
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
            )
            Text(
                "Configure ClawDroid behavior",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
            )
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
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, letterSpacing = 0.sp),
            )
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.8f.sp),
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
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.07f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
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
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
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
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
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
    inactiveTrackColor = Color.White.copy(alpha = 0.10f),
)
