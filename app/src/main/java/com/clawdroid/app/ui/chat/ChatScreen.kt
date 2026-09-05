package com.clawdroid.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.ui.markdown.MarkdownText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val items = remember {
        mutableStateListOf<ChatItem>(
            AgentChatItem(text = "Hi, I’m ClawDroid 🐙. Tell me what to do and I’ll show my work as I go."),
        )
    }
    var input by remember { mutableStateOf("") }
    var runtimeState by remember { mutableStateOf(AgentRuntimeState.Idle) }
    var engine by remember { mutableStateOf<AgentEngine?>(null) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var runningAgentMessageId by remember { mutableStateOf<String?>(null) }

    fun stopCurrentRun(reason: String = "Stopped") {
        engine?.stop()
        runJob?.cancel()
        runningAgentMessageId?.let { id ->
            items.replaceAgentMessage(id) { current ->
                current.copy(
                    text = current.text.ifBlank { reason },
                    streaming = false,
                    activityRunning = false,
                    activitySteps = current.activitySteps.markLastComplete(reason),
                )
            }
        }
        runtimeState = AgentRuntimeState.Idle
        runningAgentMessageId = null
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""

        if (runtimeState == AgentRuntimeState.Running) {
            engine?.steer(text)
            items += UserChatItem(text = text)
            return
        }

        items += UserChatItem(text = text)
        val agentMessage = AgentChatItem(text = "", streaming = true, activityRunning = true)
        items += agentMessage
        runningAgentMessageId = agentMessage.id

        val newEngine = AgentEngine(context.applicationContext)
        engine = newEngine
        runtimeState = AgentRuntimeState.Running
        runJob = scope.launch {
            runCatching {
                newEngine.run(text).collect { event ->
                    when (event) {
                        is AgentRunEvent.TextDelta -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(text = current.text + event.text, streaming = true)
                            }
                        }

                        is AgentRunEvent.ToolCallRequested -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    activityRunning = true,
                                    activitySteps = current.activitySteps + ActivityStepItem(
                                        type = event.call.name.toActivityStepType(),
                                        summary = event.call.name.readableToolName(),
                                        detail = event.call.arguments,
                                        running = true,
                                    ),
                                )
                            }
                        }

                        is AgentRunEvent.ToolResultReceived -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    activityRunning = true,
                                    activitySteps = current.activitySteps.markLastComplete(event.result.content),
                                )
                            }
                        }

                        is AgentRunEvent.SteeringApplied -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    activitySteps = current.activitySteps + ActivityStepItem(
                                        type = ActivityStepType.Service,
                                        summary = "Applied steering",
                                        detail = event.message,
                                    ),
                                )
                            }
                        }

                        is AgentRunEvent.LoopWarning -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    activitySteps = current.activitySteps + ActivityStepItem(
                                        type = ActivityStepType.Service,
                                        summary = "Loop warning",
                                        detail = event.message,
                                    ),
                                )
                            }
                        }

                        is AgentRunEvent.Completed -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    text = current.text.ifBlank { event.finalText },
                                    streaming = false,
                                    activityRunning = false,
                                    activitySteps = current.activitySteps.markAllComplete(),
                                )
                            }
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                        }

                        is AgentRunEvent.Stopped -> {
                            items.replaceAgentMessage(agentMessage.id) { current ->
                                current.copy(
                                    text = current.text.ifBlank { "Stopped: ${event.reason}" },
                                    streaming = false,
                                    activityRunning = false,
                                    activitySteps = current.activitySteps.markLastComplete(event.reason),
                                )
                            }
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                        }
                    }
                }
            }.onFailure { error ->
                items.replaceAgentMessage(agentMessage.id) { current ->
                    current.copy(
                        text = current.text.ifBlank { "Error: ${error.message ?: error::class.java.simpleName}" },
                        streaming = false,
                        activityRunning = false,
                        activitySteps = current.activitySteps.markLastComplete(error.message ?: "Run failed"),
                    )
                }
                runtimeState = AgentRuntimeState.Idle
                runningAgentMessageId = null
            }
        }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(items, key = { it.id }) { item ->
                when (item) {
                    is UserChatItem -> UserMessageBubble(item)
                    is AgentChatItem -> AgentMessageCard(item)
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            state = runtimeState,
            onSubmit = ::submit,
            onStop = { stopCurrentRun() },
        )
    }
}

@Composable
private fun UserMessageBubble(item: UserChatItem) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Text(text = item.text, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AgentMessageCard(item: AgentChatItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (item.activitySteps.isNotEmpty() || item.activityRunning) {
            InlineActivityTrail(
                steps = item.activitySteps,
                running = item.activityRunning,
            )
        }
        MarkdownText(
            markdown = item.text.ifBlank { if (item.streaming) "Thinking…" else "" },
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun InlineActivityTrail(
    steps: List<ActivityStepItem>,
    running: Boolean,
) {
    var expanded by remember(running, steps.size) { mutableStateOf(running) }
    val latest = steps.lastOrNull()
    val summary = when {
        latest != null -> "${latest.type.icon} ${latest.summary}${if (latest.running) "…" else ""}"
        running -> "🔄 Preparing…"
        else -> "Activity"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (steps.isEmpty()) "" else "${steps.size} step${if (steps.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }

            AnimatedVisibility(visible = expanded && steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.takeLast(4).forEach { step ->
                        InlineActivityStep(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineActivityStep(step: ActivityStepItem) {
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${step.type.icon} ${step.summary}${if (step.running) "…" else ""}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = step.detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 96.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    state: AgentRuntimeState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (state == AgentRuntimeState.Running) "Steer the agent…" else "Type a message…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                maxLines = 4,
            )
            if (state == AgentRuntimeState.Running) {
                Button(onClick = onStop) {
                    Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                    Text("Stop")
                }
            } else {
                IconButton(onClick = onSubmit) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

private fun MutableList<ChatItem>.replaceAgentMessage(id: String, transform: (AgentChatItem) -> AgentChatItem) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index] as AgentChatItem)
}

private fun List<ActivityStepItem>.markLastComplete(detail: String): List<ActivityStepItem> {
    if (isEmpty()) return this
    return dropLast(1) + last().copy(detail = detail, running = false)
}

private fun List<ActivityStepItem>.markAllComplete(): List<ActivityStepItem> = map { it.copy(running = false) }

private fun String.toActivityStepType(): ActivityStepType = when (this) {
    "read_file", "list_directory" -> ActivityStepType.File
    "write_file", "edit_file" -> ActivityStepType.Edit
    "browse_web", "web_search" -> ActivityStepType.Web
    "send_notification" -> ActivityStepType.Service
    "start_process", "check_process", "send_input", "kill_process", "list_processes", "execute_command" -> ActivityStepType.Command
    else -> ActivityStepType.Service
}

private fun String.readableToolName(): String = split('_')
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
