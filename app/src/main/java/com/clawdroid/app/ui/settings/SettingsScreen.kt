package com.clawdroid.app.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.components.ClawPanel
import com.clawdroid.app.ui.components.ClawSkinBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToProvider: () -> Unit = {},
    onNavigateToAudio: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToAutomations: () -> Unit = {},
    onNavigateToConnections: () -> Unit = {},
    onNavigateToChannels: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToInterpole: () -> Unit = {},
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

    ClawSkinBackground(modifier = modifier) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                        .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            "Settings",
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, letterSpacing = 0.sp),
                        )
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = onVariant.copy(alpha = 0.60f))
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsHeader(onSurface = onSurface, onVariant = onVariant)

                SettingsGroup("Core", surface, border, accent) {
                    SettingsRow(Icons.Outlined.Security, "Agent", "Configuration", accent, onSurface, onVariant, onNavigateToAgentConfig)
                    SettingsRow(Icons.Outlined.Security, "Permissions", "Access control", accent, onSurface, onVariant, onNavigateToPermissions)
                    SettingsRow(Icons.Outlined.SettingsInputComponent, "Provider", "Model settings", accent, onSurface, onVariant, onNavigateToProvider)
                    SettingsRow(Icons.Outlined.Headphones, "Audio", "Voice & sound", accent, onSurface, onVariant, onNavigateToAudio)
                    SettingsRow(Icons.Outlined.Notifications, "Notifications", "Task alerts & ASK mode", accent, onSurface, onVariant, onNavigateToNotifications)
                    SettingsRow(Icons.Outlined.ColorLens, "Themes", "Appearance", accent, onSurface, onVariant, onNavigateToThemes)
                }

                SettingsGroup("Capabilities", surface, border, accent) {
                    SettingsRow(Icons.Outlined.Link, "Connections", "External services", accent, onSurface, onVariant, onNavigateToConnections)
                    SettingsRow(Icons.Outlined.Extension, "MCP", "Plugins & addons", accent, onSurface, onVariant, onNavigateToMcp)
                    SettingsRow(Icons.Outlined.Link, "Channels", "Communication", accent, onSurface, onVariant, onNavigateToChannels)
                    SettingsRow(Icons.Outlined.Extension, "Skills", "Skill library", accent, onSurface, onVariant, onNavigateToSkills)
                    SettingsRow(Icons.Outlined.Storage, "INTERPOLE", "Desktop bridge", accent, onSurface, onVariant, onNavigateToInterpole)
                    SettingsRow(Icons.Outlined.Timer, "Automations", "Background tasks", accent, onSurface, onVariant, onNavigateToAutomations)
                }

                SettingsGroup("Android Control", surface, border, accent) {
                    SettingsRow(Icons.Outlined.Visibility, "Screen Access", "Active", accent, onSurface, onVariant, onNavigateToPermissions, badge = "ACTIVE")
                    SettingsRow(Icons.Outlined.PhotoCamera, "Screen Capture", "Recording settings", accent, onSurface, onVariant, onNavigateToPermissions)
                }

                SettingsGroup("Prompt Files", surface, border, accent) {
                    SettingsRow(Icons.Outlined.Code, "AGENTS.md", "High-level behavior and project rules", accent, onSurface, onVariant, onClick = {
                        onNavigateToConfigEditor(ConfigFileType.AGENTS)
                    })
                    SettingsRow(Icons.Outlined.Code, "SOUL.md", "Tone, ethics, and personality", accent, onSurface, onVariant, onClick = {
                        onNavigateToConfigEditor(ConfigFileType.SOUL)
                    })
                    SettingsRow(Icons.Outlined.Code, "TOOLS.md", "Tool-use policy and execution rules", accent, onSurface, onVariant, onClick = {
                        onNavigateToConfigEditor(ConfigFileType.TOOLS)
                    })
                    SettingsRow(Icons.Outlined.Code, "SKILL.md", "Core skills and domain instructions", accent, onSurface, onVariant, onClick = {
                        onNavigateToConfigEditor(ConfigFileType.SKILL)
                    })
                    SettingsRow(Icons.Outlined.Code, "SYSTEM.md", "System-level base prompt overrides", accent, onSurface, onVariant, onClick = {
                        onNavigateToConfigEditor(ConfigFileType.SYSTEM)
                    })
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
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Configure the app like Android settings: choose a category, tune it, then come back.",
            color = onSurface.copy(alpha = 0.40f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            ) {
                Column(content = content)
            }
        }
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
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    color = onSurface.copy(alpha = 0.95f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
                )
                if (badge != null) {
                    Text(
                        badge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                subtitle,
                color = onSurface.copy(alpha = 0.40f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = onSurface.copy(alpha = 0.20f), modifier = Modifier.size(18.dp))
    }
}
