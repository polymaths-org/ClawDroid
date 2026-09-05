package com.clawdroid.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
        val activityGroup = ActivityGroupItem(running = true)
        val agentMessage = AgentChatItem(text = "", streaming = true)
        items += activityGroup
        items += agentMessage

        val newEngine = AgentEngine(context.applicationContext)
        engine = newEngine
        runtimeState = AgentRuntimeState.Running
        runJob = scope.launch {
            newEngine.run(text).collect { event ->
                when (event) {
                    is AgentRunEvent.TextDelta -> {
                        items.replaceAgentMessage(agentMessage.id) { current ->
                            current.copy(text = current.text + event.text, streaming = true)
                        }
                    }

                    is AgentRunEvent.ToolCallRequested -> {
                        items.replaceActivityGroup(activityGroup.id) { group ->
                            group.copy(
                                running = true,
                                steps = group.steps + ActivityStepItem(
                                    type = ActivityStepType.Command,
                                    summary = "Ran ${event.call.name}",
                                    detail = event.call.arguments,
                                    running = true,
                                ),
                            )
                        }
                    }

                    is AgentRunEvent.ToolResultReceived -> {
                        items.replaceActivityGroup(activityGroup.id) { group ->
                            group.copy(
                                steps = group.steps.markLastComplete(event.result.content),
                                running = true,
                            )
                        }
                    }

                    is AgentRunEvent.SteeringApplied -> {
                        items.replaceActivityGroup(activityGroup.id) { group ->
                            group.copy(
                                steps = group.steps + ActivityStepItem(
                                    type = ActivityStepType.Service,
                                    summary = "Applied steering",
                                    detail = event.message,
                                ),
                            )
                        }
                    }

                    is AgentRunEvent.LoopWarning -> {
                        items.replaceActivityGroup(activityGroup.id) { group ->
                            group.copy(
                                steps = group.steps + ActivityStepItem(
                                    type = ActivityStepType.Service,
                                    summary = "Loop warning",
                                    detail = event.message,
                                ),
                            )
                        }
                    }

                    is AgentRunEvent.Completed -> {
                        items.replaceAgentMessage(agentMessage.id) { current ->
                            current.copy(text = current.text.ifBlank { event.finalText }, streaming = false)
                        }
                        items.replaceActivityGroup(activityGroup.id) { it.copy(running = false) }
                        runtimeState = AgentRuntimeState.Idle
                    }

                    is AgentRunEvent.Stopped -> {
                        items.replaceAgentMessage(agentMessage.id) { current ->
                            current.copy(
                                text = current.text.ifBlank { "Stopped: ${event.reason}" },
                                streaming = false,
                            )
                        }
                        items.replaceActivityGroup(activityGroup.id) { it.copy(running = false) }
                        runtimeState = AgentRuntimeState.Idle
                    }
                }
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
                    is ActivityGroupItem -> ActivityStepGroup(item)
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            state = runtimeState,
            onSubmit = ::submit,
            onStop = {
                engine?.stop()
                runJob?.cancel()
                runtimeState = AgentRuntimeState.Idle
            },
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.text.ifBlank { if (item.streaming) "Thinking…" else "" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ActivityStepGroup(group: ActivityGroupItem) {
    var expanded by remember(group.running) { mutableStateOf(group.running) }
    ElevatedCard(
        onClick = { expanded = !expanded },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = group.summary(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.steps.forEach { ActivityStep(it) }
                }
            }
        }
    }
}

@Composable
private fun ActivityStep(step: ActivityStepItem) {
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    Card(onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${step.type.icon} ${step.summary}${if (step.running) "…" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = step.detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
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
    SurfaceInputContainer {
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

@Composable
private fun SurfaceInputContainer(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}

private fun ActivityGroupItem.summary(): String {
    if (steps.isEmpty()) return if (running) "🔄 Preparing activity…" else "No activity"
    val commandCount = steps.count { it.type == ActivityStepType.Command }
    val otherCount = steps.size - commandCount
    return buildString {
        append(if (running) "▼ " else "▶ ")
        if (commandCount > 0) append("⚙️ Ran $commandCount command${if (commandCount == 1) "" else "s"}")
        if (otherCount > 0) {
            if (commandCount > 0) append(" · ")
            append("🧩 $otherCount step${if (otherCount == 1) "" else "s"}")
        }
    }
}

private fun MutableList<ChatItem>.replaceAgentMessage(id: String, transform: (AgentChatItem) -> AgentChatItem) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index] as AgentChatItem)
}

private fun MutableList<ChatItem>.replaceActivityGroup(id: String, transform: (ActivityGroupItem) -> ActivityGroupItem) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index] as ActivityGroupItem)
}

private fun List<ActivityStepItem>.markLastComplete(detail: String): List<ActivityStepItem> {
    if (isEmpty()) return this
    return dropLast(1) + last().copy(detail = detail, running = false)
}
