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
import com.clawdroid.app.ui.components.ClawPanel
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.components.GlassTextField

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
    var showKey by remember { mutableStateOf(false) }

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
                    }
                }

                Button(
                    onClick = {
                        if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                            AppConfigManager.save(provider.trim(), baseUrl.trim(), apiKey.trim(), model.trim())
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
