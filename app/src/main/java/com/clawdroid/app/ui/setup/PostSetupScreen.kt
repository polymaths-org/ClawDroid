package com.clawdroid.app.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.GlassBorderDim
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var ownerName by remember { mutableStateOf(AppConfigManager.ownerName) }
    var ownerRole by remember { mutableStateOf("") }
    var ownerUseCases by remember { mutableStateOf(AppConfigManager.ownerInfo) }
    var ownerPreferences by remember { mutableStateOf("") }
    var recurringTasks by remember { mutableStateOf("") }
    var writingProgress by remember { mutableStateOf(0f) }
    var writingFiles by remember { mutableStateOf(listOf<String>()) }
    var done by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(DeepBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("OpenClaw", fontSize = MaterialTheme.typography.headlineLarge.fontSize, color = EmberOrange)
        Spacer(Modifier.height(8.dp))
        Text(
            if (step < 2) "Personalize Your Agent" else "Writing Agent Memory",
            style = MaterialTheme.typography.headlineSmall,
            color = SoftWhite, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text("Give the agent enough context to understand who you are, what you do, and what it should optimize for.",
                    color = MutedGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyledField("Your Name", ownerName, { ownerName = it })
                    StyledField("Your Role / Work", ownerRole, { ownerRole = it }, singleLine = false)
                    StyledField("What will you use ClawDroid for?", ownerUseCases, { ownerUseCases = it }, singleLine = false)
                    StyledField("Preferences and working style", ownerPreferences, { ownerPreferences = it }, singleLine = false)
                    StyledField("Recurring checks or automations", recurringTasks, { recurringTasks = it }, singleLine = false)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { step = 1 },
                    enabled = ownerName.isNotBlank() && ownerUseCases.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberOrange),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Review Context", fontWeight = FontWeight.Bold) }
            }
            1 -> {
                Text("This context is saved into the sandbox so the agent can start future chats with useful memory.",
                    color = MutedGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", ownerName)
                    InfoRow("Role / Work", ownerRole)
                    InfoRow("Use Cases", ownerUseCases)
                    InfoRow("Preferences", ownerPreferences)
                    InfoRow("Recurring Tasks", recurringTasks)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = EmberOrange),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Write Agent Files", fontWeight = FontWeight.Bold) }
            }
            2 -> {
                Text("Creating memory, identity, tool, and heartbeat files...",
                    color = MutedGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                val progress by animateFloatAsState(
                    targetValue = writingProgress,
                    animationSpec = tween(500), label = "progress"
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmberOrange,
                    trackColor = GlassFill,
                )
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    writingFiles.forEach { file ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(300)),
                        ) {
                            Text("✓ $file", color = if (file.startsWith("✓")) SoftWhite else MutedGray,
                                fontSize = 14.sp)
                        }
                    }
                }

                LaunchedEffect(step) {
                    val sandboxDir = context.filesDir
                    val homeDir = File(sandboxDir, "home").also { it.mkdirs() }
                    val memoryDir = File(homeDir, ".memory").also { it.mkdirs() }
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val files = listOf(
                        homeDir.resolve("AGENTS.md") to buildString {
                            appendLine("# AGENTS.md")
                            appendLine()
                            appendLine(AppConfigManager.agentsMd.ifBlank { "Use project context, memory, and visible tool results before acting." })
                            appendLine()
                            appendLine("## Owner")
                            appendLine("- Name: $ownerName")
                            appendLine("- Role / Work: $ownerRole")
                            appendLine("- Main Use Cases: $ownerUseCases")
                        },
                        homeDir.resolve("SOUL.md") to buildString {
                            appendLine("# SOUL.md")
                            appendLine()
                            appendLine(AppConfigManager.soulMd.ifBlank { "Be transparent, practical, autonomous inside the sandbox, and easy to interrupt." })
                            appendLine()
                            appendLine("## Communication")
                            appendLine("- Keep activity visible.")
                            appendLine("- Keep spoken output clean: no emoji names, dividers, or filler lines.")
                        },
                        homeDir.resolve("TOOLS.md") to buildString {
                            appendLine("# TOOLS.md")
                            appendLine()
                            appendLine(AppConfigManager.toolsMd.ifBlank { "Use shell, file, browser, notification, and process tools deliberately." })
                            appendLine()
                            appendLine("## Constraints")
                            appendLine("- Prefer non-interactive commands.")
                            appendLine("- Ask before connected-service actions unless approval settings allow it.")
                        },
                        memoryDir.resolve("user.md") to buildString {
                            appendLine("# User")
                            appendLine()
                            appendLine("Name: $ownerName")
                            appendLine("Role / Work: $ownerRole")
                            appendLine("Use Cases: $ownerUseCases")
                            appendLine("Preferences: $ownerPreferences")
                            appendLine("Recurring Tasks: $recurringTasks")
                        },
                        memoryDir.resolve("Identity.md") to buildString {
                            appendLine("# Owner Identity")
                            appendLine()
                            appendLine("**Name:** $ownerName")
                            appendLine("**Role / Work:** $ownerRole")
                            appendLine("**Use Cases:** $ownerUseCases")
                            appendLine("**Preferences:** $ownerPreferences")
                            appendLine("**Recurring Tasks:** $recurringTasks")
                        },
                        memoryDir.resolve("Agent.md") to buildString {
                            appendLine("# Agent Configuration")
                            appendLine()
                            appendLine("**Name:** ${AppConfigManager.agentName}")
                            appendLine("**Personality:** ${AppConfigManager.agentPersonality}")
                            appendLine("**Purpose:** ${AppConfigManager.agentPurpose}")
                            appendLine("**Platform:** Android (Linux sandbox)")
                            appendLine("**Approval Mode:** ${AppConfigManager.approvalMode}")
                        },
                        memoryDir.resolve("soul.md") to buildString {
                            appendLine("# Agent Soul — Guiding Principles")
                            appendLine()
                            appendLine("## Values")
                            appendLine("- Transparency: never hide actions, always show what you did")
                            appendLine("- Autonomy: figure things out before asking for help")
                            appendLine("- Precision: be exact, avoid vague answers")
                            appendLine("- Growth: learn from every interaction")
                            appendLine()
                            appendLine("## Rules")
                            appendLine("- Keep the user informed of progress")
                            appendLine("- Show tool calls and their results")
                            appendLine("- Never execute destructive commands without confirmation")
                            appendLine("- Respect the sandbox boundary")
                        },
                        memoryDir.resolve("tools.md") to buildString {
                            appendLine("# Available Tools")
                            appendLine()
                            appendLine("The agent has access to:")
                            appendLine("- execute_command: Run shell commands")
                            appendLine("- start_process: Background processes")
                            appendLine("- read_file / write_file / edit_file: File operations")
                            appendLine("- list_directory: Explore filesystem")
                            appendLine("- browse_web: Visit URLs")
                            appendLine("- web_search: Search the web")
                            appendLine("- send_notification: Send alerts")
                            appendLine("- Process management: check/kill/list processes, send input")
                        },
                        memoryDir.resolve("heartbeat.md") to buildString {
                            appendLine("# Heartbeat & Automation Schedule")
                            appendLine()
                            appendLine("**Status:** ${if (AppConfigManager.heartbeatEnabled) "Active" else "Paused"}")
                            appendLine("**Check Interval:** ${AppConfigManager.heartbeatIntervalMin} minutes")
                            appendLine("**Last Updated:** $timestamp")
                            appendLine()
                            appendLine("## Requested Recurring Work")
                            appendLine(recurringTasks.ifBlank { "- None yet. Ask the user before adding recurring automations." })
                        },
                        memoryDir.resolve("memory.md") to buildString {
                            appendLine("# ClawDroid Agent Memory")
                            appendLine()
                            appendLine("## Known Facts")
                            appendLine("- **Agent Name:** ${AppConfigManager.agentName}")
                            appendLine("- **Personality:** ${AppConfigManager.agentPersonality}")
                            appendLine("- **Purpose:** ${AppConfigManager.agentPurpose}")
                            appendLine("- **Platform:** Android (Linux sandbox)")
                            appendLine("- **Owner:** $ownerName")
                            appendLine("- **Owner Role / Work:** $ownerRole")
                            appendLine("- **Owner Use Cases:** $ownerUseCases")
                            appendLine("- **Owner Preferences:** $ownerPreferences")
                            appendLine("- **Recurring Tasks:** $recurringTasks")
                        },
                    )

                    val totalFiles = files.size
                    files.forEachIndexed { i, (file, content) ->
                        file.parentFile?.mkdirs()
                        file.writeText(content)
                        writingFiles = writingFiles + file.name
                        writingProgress = (i + 1).toFloat() / totalFiles
                        kotlinx.coroutines.delay(300)
                    }

                    AppConfigManager.ownerName = ownerName
                    AppConfigManager.ownerInfo = buildString {
                        appendLine("Role / Work: $ownerRole")
                        appendLine("Use Cases: $ownerUseCases")
                        appendLine("Preferences: $ownerPreferences")
                        appendLine("Recurring Tasks: $recurringTasks")
                    }
                    kotlinx.coroutines.delay(500)
                    done = true
                }

                if (done) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = EmberOrange),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Start Chatting →", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun StyledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    Column {
        Text(label, color = MutedGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SoftWhite, unfocusedTextColor = SoftWhite,
                focusedBorderColor = EmberOrange, unfocusedBorderColor = GlassBorderDim,
                cursorColor = EmberOrange,
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = if (singleLine) 1 else 5,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(GlassFill).padding(12.dp)
    ) {
        Text(label, color = EmberOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(if (value.isBlank()) "—" else value, color = SoftWhite, fontSize = 14.sp)
    }
}
