package com.clawdroid.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.config.AppConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAudio: () -> Unit = {},
    onNavigateToAutomations: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToAgentConfig: () -> Unit = {},
    onNavigateToThemes: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToConfigEditor: (ConfigFileType) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val border = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = onSurface, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeader(onSurface = onSurface, onVariant = onVariant)

            SettingsGroup("Core", surface, border, accent) {
                SettingsRow(Icons.Outlined.Security, "Agent", "Identity, prompts, approvals, max turns", accent, onSurface, onVariant, onNavigateToAgentConfig)
                SettingsRow(Icons.Outlined.Security, "Permissions", "Android permissions and special access", accent, onSurface, onVariant, onNavigateToPermissions)
                SettingsRow(Icons.Outlined.SettingsInputComponent, "Provider", "${AppConfigManager.provider} · ${AppConfigManager.model}", accent, onSurface, onVariant) {
                    onNavigateToConfigEditor(ConfigFileType.AGENTS)
                }
                SettingsRow(Icons.Outlined.Headphones, "Audio", "Cloud TTS, realtime voice, speech behavior", accent, onSurface, onVariant, onNavigateToAudio)
                SettingsRow(Icons.Outlined.ColorLens, "Themes", "Light, dark, minimalist, liquid glass", accent, onSurface, onVariant, onNavigateToThemes)
            }

            SettingsGroup("Capabilities", surface, border, accent) {
                SettingsRow(Icons.Outlined.Link, "Connections", "Google, GitHub, Notion, Spotify and service auth", accent, onSurface, onVariant, onNavigateToMcp)
                SettingsRow(Icons.Outlined.Extension, "MCP", "Connector runtime and server tools", accent, onSurface, onVariant, onNavigateToMcp)
                SettingsRow(Icons.Outlined.Link, "Channels", "WhatsApp, SMS, Telegram, Slack, email, webhooks", accent, onSurface, onVariant, onNavigateToChannels)
                SettingsRow(Icons.Outlined.Extension, "Skills", "Installable behaviors and skill sources", accent, onSurface, onVariant, onNavigateToSkills)
                SettingsRow(Icons.Outlined.Timer, "Automations", "Heartbeat, background service, approval mode", accent, onSurface, onVariant, onNavigateToAutomations)
            }

            SettingsGroup("Prompt Files", surface, border, accent) {
                SettingsRow(Icons.Outlined.Code, "AGENTS.md", "High-level behavior and project rules", accent, onSurface, onVariant) {
                    onNavigateToConfigEditor(ConfigFileType.AGENTS)
                }
                SettingsRow(Icons.Outlined.Code, "SOUL.md", "Tone, ethics, and personality", accent, onSurface, onVariant) {
                    onNavigateToConfigEditor(ConfigFileType.SOUL)
                }
                SettingsRow(Icons.Outlined.Code, "TOOLS.md", "Tool-use policy and execution rules", accent, onSurface, onVariant) {
                    onNavigateToConfigEditor(ConfigFileType.TOOLS)
                }
                SettingsRow(Icons.Outlined.Code, "SKILL.md", "Core skills and domain instructions", accent, onSurface, onVariant) {
                    onNavigateToConfigEditor(ConfigFileType.SKILL)
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    onSurface: androidx.compose.ui.graphics.Color,
    onVariant: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "ClawDroid",
            color = onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Configure the app like Android settings: choose a category, tune it, then come back.",
            color = onVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    surface: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onSurface: androidx.compose.ui.graphics.Color,
    onVariant: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = onVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = onVariant, modifier = Modifier.size(20.dp))
    }
}
