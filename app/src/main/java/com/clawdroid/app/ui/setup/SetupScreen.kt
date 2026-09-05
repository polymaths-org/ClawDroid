package com.clawdroid.app.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.voice.PiperEngine
import com.clawdroid.app.core.voice.TtsEngineState
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.GlowText
import com.clawdroid.app.ui.theme.CardDark
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorder
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassFillStrong
import com.clawdroid.app.ui.theme.MoltenYellow
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

// ── Provider data ──────────────────────────────────────────────────────

data class ProviderInfo(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val defaultBaseUrl: String,
    val needsApiKey: Boolean = true,
)

private val providers = listOf(
    ProviderInfo("openrouter", "OpenRouter", "🌐", "Access 200+ models", "https://openrouter.ai/api/v1"),
    ProviderInfo("openai", "OpenAI", "🤖", "GPT-4o, o1, o3", "https://api.openai.com/v1"),
    ProviderInfo("groq", "Groq", "⚡", "Ultra-fast inference", "https://api.groq.com/openai/v1"),
    ProviderInfo("together", "Together AI", "🔗", "Open-source models", "https://api.together.xyz/v1"),
    ProviderInfo("ollama", "Ollama", "🦙", "Local models, no key", "http://localhost:11434/v1", needsApiKey = false),
    ProviderInfo("custom", "Custom", "🔧", "Any OpenAI-compatible", ""),
)

// ── SetupScreen ────────────────────────────────────────────────────────

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedProvider by remember { mutableStateOf<ProviderInfo?>(null) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    // Agent Customization states
    var agentName by remember { mutableStateOf("Nova") }
    var selectedPersonality by remember { mutableStateOf("Cyberpunk") }
    var customPersonality by remember { mutableStateOf("") }
    var selectedPurpose by remember { mutableStateOf("System Controls & Diagnostics") }
    var customPurpose by remember { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf("female") }

    // Owner info states
    var ownerName by remember { mutableStateOf(AppConfigManager.ownerName) }
    var ownerInfo by remember { mutableStateOf(AppConfigManager.ownerInfo) }

    Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // Lightweight static ambient fire glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FireRed.copy(alpha = 0.12f),
                        EmberOrange.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.7f),
                    radius = size.maxDimension * 0.6f,
                ),
                radius = size.maxDimension,
                center = Offset(size.width * 0.5f, size.height * 0.7f),
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            },
            label = "setup_step",
        ) { currentStep ->
            when (currentStep) {
                0 -> OwnerIntroductionStep(
                    ownerName = ownerName,
                    onOwnerNameChange = { ownerName = it },
                    ownerInfo = ownerInfo,
                    onOwnerInfoChange = { ownerInfo = it },
                    onNext = { step = 1 },
                )
                1 -> ProviderSelectionStep(
                    onProviderSelected = { provider ->
                        selectedProvider = provider
                        baseUrl = provider.defaultBaseUrl
                        model = when (provider.id) {
                            "openai" -> "gpt-4o"
                            "groq" -> "llama-3.3-70b-versatile"
                            "together" -> "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                            "ollama" -> "llama3.2"
                            else -> "openai/gpt-4o"
                        }
                        step = 2
                    },
                )
                2 -> ConfigurationStep(
                    provider = selectedProvider!!,
                    baseUrl = baseUrl,
                    onBaseUrlChange = { baseUrl = it },
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    model = model,
                    onModelChange = { model = it },
                    onBack = { step = 1 },
                    onNext = { step = 3 },
                )
                3 -> AgentCustomizationStep(
                    agentName = agentName,
                    onAgentNameChange = { agentName = it },
                    selectedPersonality = selectedPersonality,
                    onPersonalitySelected = { selectedPersonality = it },
                    customPersonality = customPersonality,
                    onCustomPersonalityChange = { customPersonality = it },
                    selectedPurpose = selectedPurpose,
                    onPurposeSelected = { selectedPurpose = it },
                    customPurpose = customPurpose,
                    onCustomPurposeChange = { customPurpose = it },
                    onBack = { step = 2 },
                    onNext = { step = 4 },
                )
                4 -> AgentVoiceSetupStep(
                    agentName = agentName,
                    selectedVoice = selectedVoice,
                    onVoiceSelected = { selectedVoice = it },
                    onBack = { step = 3 },
                    onNext = { step = 5 },
                )
                5 -> ConfirmationStep(
                    provider = selectedProvider!!,
                    baseUrl = baseUrl,
                    model = model,
                    agentName = agentName,
                    selectedPersonality = if (selectedPersonality == "Other") customPersonality else selectedPersonality,
                    selectedPurpose = if (selectedPurpose == "Other") customPurpose else selectedPurpose,
                    selectedVoice = selectedVoice,
                    onBack = { step = 4 },
                    onComplete = {
                        AppConfigManager.save(
                            provider = selectedProvider!!.id,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                            model = model.trim(),
                        )
                        AppConfigManager.ownerName = ownerName.trim()
                        AppConfigManager.ownerInfo = ownerInfo.trim()
                        AppConfigManager.agentName = agentName.trim()
                        AppConfigManager.agentPersonality = if (selectedPersonality == "Other") customPersonality.trim() else selectedPersonality
                        AppConfigManager.agentPurpose = if (selectedPurpose == "Other") customPurpose.trim() else selectedPurpose
                        AppConfigManager.agentVoiceProfile = selectedVoice
                        onSetupComplete()
                    },
                )
            }
        }
    }
}

// ── Step 0: Owner Introduction ─────────────────────────────────────────

@Composable
private fun OwnerIntroductionStep(
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    ownerInfo: String,
    onOwnerInfoChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        GlowText(
            text = "Welcome to ClawDroid 🐙",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Before we begin, tell me about yourself so I can serve you better.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )

        Spacer(modifier = Modifier.height(36.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("What should I call you?", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                GlassTextField(
                    value = ownerName,
                    onValueChange = onOwnerNameChange,
                    placeholder = "Your name or handle",
                )

                Text("Tell me about yourself", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                GlassTextField(
                    value = ownerInfo,
                    onValueChange = onOwnerInfoChange,
                    placeholder = "e.g. I'm a developer who loves Python, I work on open source projects, I prefer concise answers...",
                    singleLine = false,
                )

                Text(
                    text = "This helps me understand your preferences, work style, and how to best assist you. I'll remember this across conversations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        GlassButton(
            onClick = onNext,
            enabled = ownerName.isNotBlank(),
        ) {
            Text("Next →", fontWeight = FontWeight.Bold, color = SoftWhite)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Step 1: Provider Selection ─────────────────────────────────────────

@Composable
private fun ProviderSelectionStep(
    onProviderSelected: (ProviderInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        GlowText(
            text = "Choose Your Provider",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select an AI provider to power your agent",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )

        Spacer(modifier = Modifier.height(36.dp))

        providers.forEachIndexed { index, provider ->
            val entryAlpha = remember { Animatable(0f) }
            val entryScale = remember { Animatable(0.9f) }

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 60L)
                entryAlpha.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 60L)
                entryScale.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            }

            ProviderCard(
                provider = provider,
                onClick = { onProviderSelected(provider) },
                modifier = Modifier
                    .alpha(entryAlpha.value)
                    .scale(entryScale.value),
            )

            if (index < providers.lastIndex) {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill, shape)
            .border(1.dp, GlassBorderDim, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = provider.icon,
                fontSize = 28.sp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray,
                )
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = EmberOrange.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Step 2: Configuration ──────────────────────────────────────────────

@Composable
private fun ConfigurationStep(
    provider: ProviderInfo,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        GlowText(
            text = "Configure ${provider.name}",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${provider.icon} ${provider.description}",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )

        Spacer(modifier = Modifier.height(36.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Base URL
                Text("Base URL", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                GlassTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    placeholder = "https://api.example.com/v1",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                // API Key (only if needed)
                if (provider.needsApiKey) {
                    Text("API Key", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                    GlassTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        placeholder = "sk-...",
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
                }

                // Model
                Text("Model", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                GlassTextField(
                    value = model,
                    onValueChange = onModelChange,
                    placeholder = "Model name or ID",
                )

                Text(
                    text = "Type the model name manually or use the default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        val canProceed = baseUrl.isNotBlank() && model.isNotBlank() &&
                (!provider.needsApiKey || apiKey.isNotBlank())

        GlassButton(
            onClick = onNext,
            enabled = canProceed,
        ) {
            Text(
                "Continue",
                fontWeight = FontWeight.SemiBold,
                color = SoftWhite,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Step 3: Agent Customization ────────────────────────────────────────

@Composable
private fun AgentCustomizationStep(
    agentName: String,
    onAgentNameChange: (String) -> Unit,
    selectedPersonality: String,
    onPersonalitySelected: (String) -> Unit,
    customPersonality: String,
    onCustomPersonalityChange: (String) -> Unit,
    selectedPurpose: String,
    onPurposeSelected: (String) -> Unit,
    customPurpose: String,
    onCustomPurposeChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        GlowText(
            text = "Customize Your Agent",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure your agent's digital identity",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )

        Spacer(modifier = Modifier.height(28.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Agent Name
                Text("Agent Name", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                GlassTextField(
                    value = agentName,
                    onValueChange = onAgentNameChange,
                    placeholder = "e.g. Nova",
                )

                // Presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Nova", "Aura", "Matrix", "Kore").forEach { name ->
                        val isSelected = agentName.trim().equals(name, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) GlassFillStrong else GlassFill)
                                .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(8.dp))
                                .clickable { onAgentNameChange(name) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(name, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Personality selection
                Text("Personality Style", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                val personalities = listOf("Cyberpunk", "Sysadmin", "Helpful", "Analytical", "Other")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    personalities.forEach { personality ->
                        val isSelected = selectedPersonality == personality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GlassFillStrong else GlassFill)
                                .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                .clickable { onPersonalitySelected(personality) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (personality) {
                                    "Cyberpunk" -> "🕶 Cyberpunk"
                                    "Sysadmin" -> "⚙️ Sysadmin"
                                    "Helpful" -> "🤝 Helpful"
                                    "Analytical" -> "🔬 Analytical"
                                    else -> "✏️ Other (Custom)"
                                },
                                color = SoftWhite,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Text("✓", color = EmberOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (selectedPersonality == "Other") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Describe Personality Style", style = MaterialTheme.typography.labelSmall, color = MutedGray)
                    GlassTextField(
                        value = customPersonality,
                        onValueChange = onCustomPersonalityChange,
                        placeholder = "e.g. Sarcastic assistant, quiet companion...",
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Purpose Selection
                Text("Primary Purpose", style = MaterialTheme.typography.labelLarge, color = EmberOrange)
                val purposes = listOf(
                    "System Controls & Diagnostics",
                    "Coding & Research",
                    "Conversational Assistant",
                    "Other"
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    purposes.forEach { purpose ->
                        val isSelected = selectedPurpose == purpose
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GlassFillStrong else GlassFill)
                                .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(12.dp))
                                .clickable { onPurposeSelected(purpose) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (purpose == "Other") "✏️ Other (Custom)" else purpose,
                                color = SoftWhite,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Text("✓", color = EmberOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (selectedPurpose == "Other") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Describe Primary Purpose", style = MaterialTheme.typography.labelSmall, color = MutedGray)
                    GlassTextField(
                        value = customPurpose,
                        onValueChange = onCustomPurposeChange,
                        placeholder = "e.g. Help with translation, summarize notifications...",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        val enableContinue = agentName.isNotBlank() &&
                (selectedPersonality != "Other" || customPersonality.isNotBlank()) &&
                (selectedPurpose != "Other" || customPurpose.isNotBlank())

        GlassButton(
            onClick = onNext,
            enabled = enableContinue
        ) {
            Text("Continue", fontWeight = FontWeight.SemiBold, color = SoftWhite)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Step 4: Agent Voice Setup ──────────────────────────────────────────

@Composable
private fun AgentVoiceSetupStep(
    agentName: String,
    selectedVoice: String,
    onVoiceSelected: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Piper engine state ──
    val piperEngine = remember { PiperEngine(context) }
    val piperState by piperEngine.state.collectAsState()
    val piperProgress by piperEngine.downloadProgress.collectAsState()
    val piperReady = piperState == TtsEngineState.Ready
    var piperDownloading by remember { mutableStateOf(false) }
    var piperSamplePlayed by remember { mutableStateOf(false) }

    // ── Android TTS for non-Piper voices ──
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    // ── Storage permission ──
    var storageGranted by remember { mutableStateOf(checkStoragePermission(context)) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { storageGranted = checkStoragePermission(context) }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> storageGranted = granted }

    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { }
        ttsInstance = t
        onDispose {
            t.shutdown()
            piperEngine.destroy()
        }
    }

    val voiceOptions = buildList {
        add(Triple("male", "Male Voice (Realistic)", "👨"))
        add(Triple("male_deep", "Male Voice (Deep)", "🧔"))
        add(Triple("female", "Female Voice (Natural)", "👩"))
        add(Triple("female_high", "Female Voice (High)", "👩‍🦰"))
        add(Triple("synth", "Android Synth", "🤖"))
        if (piperReady) {
            add(Triple("piper", "Piper Neural (Male)", "🎤"))
        }
    }

    fun playSample() {
        if (selectedVoice == "piper") {
            piperEngine.speak("Hello, this is $agentName. I'm your Piper neural voice.") {
                piperSamplePlayed = true
            }
        } else {
            ttsInstance?.let { tts ->
                tts.language = Locale.US
                when (selectedVoice) {
                    "female" -> { tts.setPitch(1.2f); tts.setSpeechRate(1.1f) }
                    "female_high" -> { tts.setPitch(1.45f); tts.setSpeechRate(1.05f) }
                    "male" -> { tts.setPitch(0.85f); tts.setSpeechRate(1.0f) }
                    "male_deep" -> { tts.setPitch(0.65f); tts.setSpeechRate(0.95f) }
                    "synth" -> { tts.setPitch(0.55f); tts.setSpeechRate(0.85f) }
                    else -> { tts.setPitch(1.0f); tts.setSpeechRate(1.0f) }
                }
                tts.speak("Hello, this is $agentName", TextToSpeech.QUEUE_FLUSH, null, "preview")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        GlowText(
            text = "Choose Voice Profile",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a voice tone and listen to the preview",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )

        Spacer(modifier = Modifier.height(36.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                voiceOptions.forEach { (id, label, icon) ->
                    val isSelected = selectedVoice == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) GlassFillStrong else GlassFill)
                            .border(1.dp, if (isSelected) EmberOrange else GlassBorderDim, RoundedCornerShape(14.dp))
                            .clickable { onVoiceSelected(id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(icon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                color = SoftWhite,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (id == "piper") {
                                Text(
                                    text = "Neural, offline, natural",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmberOrange,
                                )
                            }
                        }
                        if (isSelected) {
                            Text("✓", color = EmberOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(onClick = ::playSample) {
                    Text("🔊 Play Sample Speech", fontWeight = FontWeight.Medium, color = SoftWhite)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Download / Status card ──
        if (!piperReady) {
            PiperDownloadCard(
                downloading = piperDownloading,
                progress = piperProgress,
                onDownload = {
                    piperDownloading = true
                    scope.launch {
                        piperEngine.startDownload()
                        piperEngine.state.first { it != TtsEngineState.Initializing }
                        piperDownloading = false
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Storage permission card ──
        if (!storageGranted) {
            StoragePermissionCard(
                onGrant = {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        storagePermissionLauncher.launch(intent)
                    } else {
                        legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassButton(onClick = onNext) {
            Text("Configure Agent 🚀", fontWeight = FontWeight.Bold, color = SoftWhite)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PiperDownloadCard(
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "piper_dl_progress",
    )
    val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)

    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎤", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (downloading) "Downloading Piper… $pct%" else "📥 Download Piper Voice",
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (downloading) "Neural TTS engine (~80 MB)"
                        else "Natural male voice, works offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray,
                    )
                }
            }

            if (downloading && animatedProgress < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(GlassFill, RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(EmberOrange, RoundedCornerShape(3.dp)),
                    )
                }
            }

            if (!downloading) {
                GlassButton(onClick = onDownload) {
                    Text("⬇ Download (~80 MB)", fontWeight = FontWeight.Medium, color = SoftWhite)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassFill)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🗣", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kokoro TTS", color = MutedGray, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        Text("Coming soon — smaller, fast, multilingual", style = MaterialTheme.typography.labelSmall, color = MutedGray.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionCard(
    onGrant: () -> Unit,
) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📂", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow File Storage",
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Agent needs access to Documents/ClawDroid/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray,
                    )
                }
            }
            GlassButton(onClick = onGrant) {
                Text("Grant Permission", fontWeight = FontWeight.Medium, color = SoftWhite)
            }
        }
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 30) {
        android.os.Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

// ── Step 5: Confirmation ───────────────────────────────────────────────

@Composable
private fun ConfirmationStep(
    provider: ProviderInfo,
    baseUrl: String,
    model: String,
    agentName: String,
    selectedPersonality: String,
    selectedPurpose: String,
    selectedVoice: String,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = provider.icon,
            fontSize = 56.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlowText(
            text = "Setup Complete",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow("Provider", provider.name)
                SummaryRow("Model", model)
                SummaryRow("Agent Name", agentName)
                SummaryRow("Personality", selectedPersonality)
                SummaryRow("Purpose", selectedPurpose)
                SummaryRow("Voice Profile", selectedVoice.uppercase())
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        GlassButton(onClick = onComplete) {
            Text(
                "Let's Go 🚀",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = SoftWhite,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = SoftWhite,
            fontWeight = FontWeight.Medium,
        )
    }
}
