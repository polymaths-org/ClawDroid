package com.clawdroid.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.config.OpenCodeZen
import com.clawdroid.app.core.config.SavedProviderProfile
import com.clawdroid.app.core.localllm.LocalLlmConfig
import com.clawdroid.app.core.localllm.LocalModelManager
import com.clawdroid.app.core.localllm.LocalModelStatus
import com.clawdroid.app.ui.components.ClawPanel
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.ModelDropdown
import com.clawdroid.app.ui.components.partitionZenModels
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(AppConfigManager.provider) }
    var baseUrl by remember { mutableStateOf(AppConfigManager.baseUrl) }
    var apiKey by remember { mutableStateOf(AppConfigManager.apiKey) }
    var model by remember { mutableStateOf(AppConfigManager.model) }
    var profileName by remember { mutableStateOf(AppConfigManager.provider.ifBlank { "Main" }) }
    var showKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var zenModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var zenModelsLoading by remember { mutableStateOf(false) }
    var zenModelsError by remember { mutableStateOf<String?>(null) }
    val isZen = OpenCodeZen.isZen(provider, baseUrl)
    val localOptions = remember { LocalModelManager.options }
    var localModelId by remember {
        mutableStateOf(
            localOptions.firstOrNull { it.id == AppConfigManager.model }?.id
                ?: LocalLlmConfig.GGUF_MODEL_4B
        )
    }
    val localStatusMap by LocalModelManager.status.collectAsState()
    LaunchedEffect(localModelId) {
        LocalModelManager.refreshStatus(context, localModelId)
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerLow

    ClawSkinBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Provider", color = onSurface, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClawPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "AI Provider",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Configure your LLM endpoint. Compatible with OpenAI, OpenRouter, Groq, Together, Ollama, and any OpenAI-compatible API.",
                            color = onVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                ClawPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Endpoint", color = accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Saved name", color = onVariant, style = MaterialTheme.typography.bodySmall)
                            GlassTextField(
                                value = profileName,
                                onValueChange = { profileName = it },
                                placeholder = "Main, Work, Local, Research...",
                                singleLine = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Provider", color = onVariant, style = MaterialTheme.typography.bodySmall)
                            GlassTextField(
                                value = provider,
                                onValueChange = { provider = it },
                                placeholder = "e.g. openrouter, siliconflow, groq",
                                singleLine = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Base URL", color = onVariant, style = MaterialTheme.typography.bodySmall)
                            GlassTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                placeholder = "https://api.openai.com/v1",
                                singleLine = true,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("API Key", color = onVariant, style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    placeholder = "sk-...",
                                    singleLine = true,
                                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle key visibility",
                                        tint = onVariant,
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Model", color = onVariant, style = MaterialTheme.typography.bodySmall)
                            GlassTextField(
                                value = model,
                                onValueChange = { model = it },
                                placeholder = "gpt-4o, claude-3-opus, ...",
                                singleLine = true,
                            )
                            if (isZen) {
                                OutlinedButton(
                                    onClick = {
                                        zenModelsLoading = true
                                        zenModelsError = null
                                        scope.launch {
                                            try {
                                                zenModels = OpenCodeZen.fetchModels(
                                                    baseUrl.ifBlank { OpenCodeZen.BASE_URL },
                                                    apiKey,
                                                )
                                                if (zenModels.isNotEmpty() && model.isBlank()) {
                                                    model = zenModels.first()
                                                }
                                            } catch (t: Throwable) {
                                                zenModelsError = t.message ?: "Failed to load models"
                                            } finally {
                                                zenModelsLoading = false
                                            }
                                        }
                                    },
                                    enabled = !zenModelsLoading,
                                ) {
                                    Text(if (zenModelsLoading) "Loading models…" else "Load Zen models")
                                }
                                if (zenModelsError != null) {
                                    Text(
                                        zenModelsError.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                val partitioned = remember(zenModels) {
                                    partitionZenModels(zenModels, OpenCodeZen::isFree)
                                }
                                if (partitioned.free.isNotEmpty()) {
                                    Text("Free models", color = onVariant, style = MaterialTheme.typography.bodySmall)
                                    ModelDropdown(
                                        selected = if (model in partitioned.free) model else "",
                                        options = partitioned.free,
                                        onSelect = { model = it },
                                        label = "Free models",
                                        placeholder = "Select a free model",
                                    )
                                }
                                if (partitioned.paid.isNotEmpty()) {
                                    Text("All models", color = onVariant, style = MaterialTheme.typography.bodySmall)
                                    ModelDropdown(
                                        selected = if (model in partitioned.paid) model else "",
                                        options = partitioned.paid,
                                        onSelect = { model = it },
                                        label = "All models",
                                        placeholder = "Select a model",
                                    )
                                }
                            }
                        }
                    }
                }

                ClawPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Quick Presets", color = accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)

                        ProviderPreset("OpenCode Zen", "big-pickle", "https://opencode.ai/zen/v1", provider, model, baseUrl) {
                            provider = "opencode_zen"; baseUrl = "https://opencode.ai/zen/v1"; model = "big-pickle"
                        }
                        ProviderPreset("OpenAI", "gpt-4o", "https://api.openai.com/v1", provider, model, baseUrl) {
                            provider = "openai"; baseUrl = "https://api.openai.com/v1"; model = "gpt-4o"
                        }
                        ProviderPreset("OpenRouter", "openai/gpt-4o", "https://openrouter.ai/api/v1", provider, model, baseUrl) {
                            provider = "openrouter"; baseUrl = "https://openrouter.ai/api/v1"; model = "openai/gpt-4o"
                        }
                        ProviderPreset("Groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1", provider, model, baseUrl) {
                            provider = "groq"; baseUrl = "https://api.groq.com/openai/v1"; model = "llama-3.3-70b-versatile"
                        }
                        ProviderPreset("Together AI", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "https://api.together.xyz/v1", provider, model, baseUrl) {
                            provider = "together"; baseUrl = "https://api.together.xyz/v1"; model = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
                        }
                        ProviderPreset("Ollama (Local)", "llama3.2", "http://localhost:11434/v1", provider, model, baseUrl) {
                            provider = "ollama"; baseUrl = "http://localhost:11434/v1"; model = "llama3.2"
                        }
                        ProviderPreset("On-Device NPU (GenieX)", LocalLlmConfig.GGUF_MODEL_4B, "ondevice://geniex", provider, model, baseUrl) {
                            provider = LocalLlmConfig.PROVIDER_ID; baseUrl = "ondevice://geniex"; model = localModelId
                        }
                    }
                }

                ClawPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("On-Device Model (Hexagon NPU)", color = accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Weights download from the cloud on first use — never shipped in the APK. " +
                                "Stay on Wi-Fi; downloads resume if interrupted.",
                            color = onVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        localOptions.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { localModelId = opt.id }
                                    .padding(4.dp),
                            ) {
                                RadioButton(
                                    selected = localModelId == opt.id,
                                    onClick = { localModelId = opt.id },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(opt.label, color = onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(opt.id, color = onVariant, fontSize = 11.sp)
                                    Text(opt.sizeNote, color = onVariant, fontSize = 11.sp)
                                }
                            }
                        }

                        val localStatus = localStatusMap[localModelId]
                            ?: LocalModelStatus.NotDownloaded
                        when (localStatus) {
                            is LocalModelStatus.Downloading -> {
                                val frac = if (localStatus.totalBytes > 0) {
                                    (localStatus.downloadedBytes.toFloat() / localStatus.totalBytes).coerceIn(0f, 1f)
                                } else 0f
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "Downloading ${(localStatus.downloadedBytes / 1048576)} / ${(localStatus.totalBytes / 1048576)} MB",
                                    color = onVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedButton(
                                    onClick = { /* pullFlow has no cancel; runs to completion or error */ },
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Downloading…")
                                }
                            }
                            is LocalModelStatus.Ready -> {
                                Text("✓ Downloaded and ready", color = accent, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { scope.launch { LocalModelManager.delete(context, localModelId) } },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Delete")
                                    }
                                    Button(
                                        onClick = {
                                            provider = LocalLlmConfig.PROVIDER_ID
                                            baseUrl = "ondevice://geniex"
                                            apiKey = ""
                                            model = localModelId
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Use this model")
                                    }
                                }
                            }
                            is LocalModelStatus.Error -> {
                                Text(localStatus.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { scope.launch { LocalModelManager.download(context, localModelId) } },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Retry download")
                                }
                            }
                            is LocalModelStatus.NotDownloaded -> {
                                Button(
                                    onClick = { scope.launch { LocalModelManager.download(context, localModelId) } },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Download")
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val keyOptional = provider.equals("ollama", ignoreCase = true) || baseUrl.contains("localhost") ||
                            provider == LocalLlmConfig.PROVIDER_ID || baseUrl == "ondevice://geniex"
                        if (baseUrl.isNotBlank() && model.isNotBlank() && (apiKey.isNotBlank() || keyOptional)) {
                            AppConfigManager.save(provider.trim(), baseUrl.trim(), apiKey.trim(), model.trim())
                            AppConfigManager.saveProviderProfile(
                                SavedProviderProfile(
                                    name = profileName.trim().ifBlank { provider.trim() },
                                    provider = provider.trim(),
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                ),
                            )
                            Toast.makeText(context, "Provider settings saved", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Provider Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProviderPreset(
    name: String,
    presetModel: String,
    presetUrl: String,
    currentProvider: String,
    currentModel: String,
    currentUrl: String,
    onClick: () -> Unit,
) {
    val isActive = currentProvider.equals(name.lowercase().replace(" ", ""), ignoreCase = true) ||
        currentUrl == presetUrl
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.08f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(presetUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (isActive) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
