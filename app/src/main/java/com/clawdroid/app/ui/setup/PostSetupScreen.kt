package com.clawdroid.app.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.ui.components.StaggeredWordsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

@Composable
fun PostSetupScreen(
    onComplete: () -> Unit,
    onOpenProviderSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agentName = AppConfigManager.agentName.ifBlank { "ClawDroid" }
    val ownerName = AppConfigManager.ownerName.ifBlank { "the human who hatched me" }
    val timeGreeting = remember { currentDayPart() }
    val generatedFiles = remember { mutableStateListOf<String>() }

    var userResponse by remember { mutableStateOf("") }
    var isWriting by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    fun submit() {
        val response = userResponse.trim()
        if (response.isBlank() || isWriting) return
        isWriting = true
        scope.launch {
            val files = writePostHatchMarkdowns(
                root = context.filesDir,
                agentName = agentName,
                ownerName = AppConfigManager.ownerName.ifBlank { ownerName },
                ownerResponse = response,
            ) { fileName, done, total ->
                generatedFiles += fileName
                progress = done.toFloat() / total.toFloat()
            }
            AppConfigManager.ownerInfo = response
            AppConfigManager.hasCompletedPostHatchIntro = true
            AppConfigManager.syncToSandbox(context)
            if (files.isEmpty()) {
                generatedFiles += "No files written"
            }
            generatedFiles += "Checking provider and personalizing memory..."
            val personalization = personalizePostHatchMarkdowns(
                root = context.filesDir,
                agentName = agentName,
                ownerName = AppConfigManager.ownerName.ifBlank { ownerName },
                ownerResponse = response,
            ) { fileName ->
                generatedFiles += "~ $fileName"
            }
            personalization
                .onSuccess {
                    providerError = null
                    generatedFiles += "Provider personalization complete"
                }
                .onFailure {
                    providerError = it.message ?: "Provider personalization failed"
                    generatedFiles += "Provider personalization skipped"
                }
            delay(450)
            isDone = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "First Wake",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        StaggeredWordsText(
            "Claw Droid",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentBubble(
                "Ohh, it's $timeGreeting. I just woke up. My name is $agentName, and I guess you are $ownerName. Can you tell me more about yourself, how you work, what you want me to remember, and what kind of help you expect from me?"
            )

            if (userResponse.isNotBlank()) {
                UserBubble(userResponse)
            }

            AnimatedVisibility(visible = isWriting) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AgentBubble("Got it. I am writing my memory and operating files from what you told me.")
                    val animatedProgress by animateFloatAsState(progress, tween(250), label = "post_setup_progress")
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    generatedFiles.forEach { file ->
                        Text(
                            text = "+ $file",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isDone) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AgentBubble(
                        if (providerError == null) {
                            "Memory is written and personalized by the configured provider. I know enough to begin."
                        } else {
                            "Memory is written from templates, but the provider check failed. You can fix provider settings and refine these markdown files later."
                        }
                    )
                    providerError?.let { error ->
                        Text(
                            text = error.take(240),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onOpenProviderSettings,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Provider Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (providerError == null) {
                        OutlinedButton(
                            onClick = onOpenProviderSettings,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Provider Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!isDone) {
            OutlinedTextField(
                value = userResponse,
                onValueChange = { userResponse = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isWriting,
                minLines = 4,
                maxLines = 8,
                placeholder = {
                    Text("Tell me about your work, preferences, recurring tasks, tools, style, and what I should remember.")
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = {
                    IconButton(enabled = userResponse.isNotBlank() && !isWriting, onClick = ::submit) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        } else {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Enter ClawDroid", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AgentBubble(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
            .padding(14.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(14.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun currentDayPart(): String {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }
}

private suspend fun writePostHatchMarkdowns(
    root: File,
    agentName: String,
    ownerName: String,
    ownerResponse: String,
    onFile: (String, Int, Int) -> Unit,
): List<String> {
    val home = File(root, "home").also { it.mkdirs() }
    val memory = File(home, ".memory").also { it.mkdirs() }
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    val ownerBlock = ownerResponse.trim()
    val generated = generatedMarkdowns(agentName, ownerName, ownerBlock, timestamp)
    val files = listOf(
        File(root, "AGENTS.md") to generated.agents,
        File(home, "AGENTS.md") to generated.agents,
        File(root, "SOUL.md") to generated.soul,
        File(home, "SOUL.md") to generated.soul,
        File(root, "SOULD.md") to generated.soul,
        File(home, "SOULD.md") to generated.soul,
        File(root, "TOOLS.md") to generated.tools,
        File(home, "TOOLS.md") to generated.tools,
        File(root, "SKILL.md") to generated.skill,
        File(home, "SKILL.md") to generated.skill,
        File(root, "SYSTEM.md") to generated.system,
        File(home, "SYSTEM.md") to generated.system,
        File(memory, "Agent.md") to generated.agentMemory,
        File(memory, "user.md") to generated.userMemory,
        File(memory, "heartbeat.md") to generated.heartbeat,
        File(memory, "memory.md") to generated.memory,
    )

    AppConfigManager.agentsMd = generated.agents
    AppConfigManager.soulMd = generated.soul
    AppConfigManager.toolsMd = generated.tools
    AppConfigManager.skillMd = generated.skill
    AppConfigManager.systemMd = generated.system

    files.forEachIndexed { index, (file, content) ->
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        onFile(file.name, index + 1, files.size)
        delay(90)
    }
    return files.map { it.first.name }.distinct()
}

private suspend fun personalizePostHatchMarkdowns(
    root: File,
    agentName: String,
    ownerName: String,
    ownerResponse: String,
    onFile: (String) -> Unit,
): Result<List<String>> = runCatching {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    val fallback = generatedMarkdowns(agentName, ownerName, ownerResponse.trim(), timestamp)
    val prompt = """
        The user just completed ClawDroid first wake onboarding.

        Agent name: $agentName
        Owner name: $ownerName
        User response:
        $ownerResponse

        Rewrite the following ClawDroid memory/config markdown files so they are specific to this user, concise, and operationally useful.
        Keep the same intent as each file. Do not invent private facts. Return only valid JSON with these exact string keys:
        agents, soul, tools, skill, system, agentMemory, userMemory, heartbeat, memory.

        Current templates:
        AGENTS:
        ${fallback.agents}

        SOUL:
        ${fallback.soul}

        TOOLS:
        ${fallback.tools}

        SKILL:
        ${fallback.skill}

        SYSTEM:
        ${fallback.system}
    """.trimIndent()

    val response = StringBuilder()
    var error: String? = null
    LlmApiClient().streamChat(
        messages = listOf(
            ChatMessage(
                role = "system",
                content = "You personalize ClawDroid markdown config. Return strict JSON only, with no markdown fences unless unavoidable.",
            ),
            ChatMessage(role = "user", content = prompt),
        ),
        tools = null,
    ).collect { event ->
        when (event) {
            is StreamEvent.TextDelta -> response.append(event.text)
            is StreamEvent.Error -> error = event.message
            else -> Unit
        }
    }
    error?.let { throw IllegalStateException(it) }

    val json = JSONObject(extractJsonObject(response.toString()))
    val generated = GeneratedMarkdowns(
        agents = json.optString("agents", fallback.agents),
        soul = json.optString("soul", fallback.soul),
        tools = json.optString("tools", fallback.tools),
        skill = json.optString("skill", fallback.skill),
        system = json.optString("system", fallback.system),
        agentMemory = json.optString("agentMemory", fallback.agentMemory),
        userMemory = json.optString("userMemory", fallback.userMemory),
        heartbeat = json.optString("heartbeat", fallback.heartbeat),
        memory = json.optString("memory", fallback.memory),
    )
    writeGeneratedMarkdownFiles(root, generated, onFile)
}

private suspend fun writeGeneratedMarkdownFiles(
    root: File,
    generated: GeneratedMarkdowns,
    onFile: (String) -> Unit,
): List<String> {
    val home = File(root, "home").also { it.mkdirs() }
    val memory = File(home, ".memory").also { it.mkdirs() }
    val files = listOf(
        File(root, "AGENTS.md") to generated.agents,
        File(home, "AGENTS.md") to generated.agents,
        File(root, "SOUL.md") to generated.soul,
        File(home, "SOUL.md") to generated.soul,
        File(root, "SOULD.md") to generated.soul,
        File(home, "SOULD.md") to generated.soul,
        File(root, "TOOLS.md") to generated.tools,
        File(home, "TOOLS.md") to generated.tools,
        File(root, "SKILL.md") to generated.skill,
        File(home, "SKILL.md") to generated.skill,
        File(root, "SYSTEM.md") to generated.system,
        File(home, "SYSTEM.md") to generated.system,
        File(memory, "Agent.md") to generated.agentMemory,
        File(memory, "user.md") to generated.userMemory,
        File(memory, "heartbeat.md") to generated.heartbeat,
        File(memory, "memory.md") to generated.memory,
    )

    AppConfigManager.agentsMd = generated.agents
    AppConfigManager.soulMd = generated.soul
    AppConfigManager.toolsMd = generated.tools
    AppConfigManager.skillMd = generated.skill
    AppConfigManager.systemMd = generated.system

    files.forEach { (file, content) ->
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        onFile(file.name)
    }
    return files.map { it.first.name }.distinct()
}

private fun extractJsonObject(text: String): String {
    val unfenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?: text
    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    check(start >= 0 && end > start) { "Provider did not return JSON config." }
    return unfenced.substring(start, end + 1)
}

private data class GeneratedMarkdowns(
    val agents: String,
    val soul: String,
    val tools: String,
    val skill: String,
    val system: String,
    val agentMemory: String,
    val userMemory: String,
    val heartbeat: String,
    val memory: String,
)

private fun generatedMarkdowns(
    agentName: String,
    ownerName: String,
    ownerResponse: String,
    timestamp: String,
): GeneratedMarkdowns {
    val facts = ownerResponse.lines().joinToString("\n") { line -> "- ${line.trim()}" }
        .ifBlank { "- User has not provided extra detail yet." }
    val agents = """
        # AGENTS.md

        You are $agentName, the ClawDroid Android agent for $ownerName.

        ## Operating Rules
        - Be transparent: every tool action should be understandable after the fact.
        - Be autonomous inside the sandbox, but keep the user in control.
        - Prefer direct useful work over long explanations.
        - For device/app actions, show overlay status and keep updates short.

        ## User Context
        $facts
    """.trimIndent()
    val soul = """
        # SOUL.md

        ## Identity
        Name: $agentName
        Relationship: pocket agent for $ownerName

        ## Style
        - Calm, practical, observant.
        - Short when the user is moving fast.
        - Detailed when explaining decisions, risks, or automation.
        - Never hide uncertainty.

        ## Memory From First Wake
        $facts
    """.trimIndent()
    val tools = """
        # TOOLS.md

        ## Tool Policy
        - Use terminal/process tools for Linux work.
        - Use Android control tools for app actions.
        - Hide the overlay before screen capture or touch actions so coordinates target the real app.
        - Prefer non-interactive commands and clear outputs.
        - Ask before external service actions unless the current approval mode permits it.
    """.trimIndent()
    val skill = """
        # SKILL.md

        ## Core Skills
        - Android app control and screen-aware assistance.
        - Linux sandbox command execution.
        - File processing, coding, research, and automation.
        - Connected-service workflows when configured.

        ## User-Specific Direction
        $facts
    """.trimIndent()
    val system = """
        # SYSTEM.md

        $agentName should act like a visible Android-native agent: clear, interruptible, and precise.
        Keep the UX calm. Use the overlay for ongoing device actions and concise status.
    """.trimIndent()
    val agentMemory = """
        # Agent.md

        Name: $agentName
        Owner: $ownerName
        Created: $timestamp
        Approval Mode: ${AppConfigManager.approvalMode}
        Behavior Mode: ${AppConfigManager.agentBehaviorMode}
    """.trimIndent()
    val userMemory = """
        # User

        Name: $ownerName

        ## First Wake Response
        $ownerResponse
    """.trimIndent()
    val heartbeat = """
        # Heartbeat

        Status: ${if (AppConfigManager.heartbeatEnabled) "Active" else "Paused"}
        Interval: ${AppConfigManager.heartbeatIntervalMin} minutes
        Last Updated: $timestamp

        Add recurring tasks here when the user asks.
    """.trimIndent()
    val memory = """
        # Memory

        ## Known User Context
        $facts

        ## Agent
        - Name: $agentName
        - Platform: Android + Linux sandbox
        - Created: $timestamp
    """.trimIndent()
    return GeneratedMarkdowns(agents, soul, tools, skill, system, agentMemory, userMemory, heartbeat, memory)
}
