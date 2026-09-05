package com.clawdroid.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val items = remember { mutableStateListOf<ChatItem>() }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        PremiumTopBar()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (items.isEmpty()) {
                EmptyGreeting(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 28.dp,
                        end = 28.dp,
                        top = 36.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    items(items, key = { it.id }) { item ->
                        when (item) {
                            is UserChatItem -> UserMessageBubble(item)
                            is AgentChatItem -> AgentMessageCard(item)
                        }
                    }
                }
            }
        }

        PremiumInputBar(
            value = input,
            onValueChange = { input = it },
            state = runtimeState,
            onSubmit = ::submit,
            onStop = { stopCurrentRun() },
        )
    }
}

@Composable
private fun PremiumTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "ClawDroid",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
            ),
        )
        Surface(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Profile",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyGreeting(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Hi Rushikesh,",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.7).sp,
            ),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "what's the plan?",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.7).sp,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserMessageBubble(item: UserChatItem) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        ) {
            Text(
                text = item.text,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            )
        }
    }
}

@Composable
private fun AgentMessageCard(item: AgentChatItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (item.activitySteps.isNotEmpty() || item.activityRunning) {
            InlineActivityTrail(
                steps = item.activitySteps,
                running = item.activityRunning,
            )
        }
        MarkdownText(
            markdown = item.text.ifBlank { if (item.streaming) "Thinking…" else "" },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InlineActivityTrail(
    steps: List<ActivityStepItem>,
    running: Boolean,
) {
    var expanded by remember(running, steps.size) { mutableStateOf(running) }
    val commandCount = steps.count { it.type == ActivityStepType.Command }
    val latest = steps.lastOrNull()
    val title = when {
        commandCount > 0 && running -> "Running $commandCount command${if (commandCount == 1) "" else "s"}"
        commandCount > 0 -> "Ran $commandCount command${if (commandCount == 1) "" else "s"}"
        latest != null -> latest.summary
        else -> "Preparing activity"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer),
            ) {}
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = if (running) "live" else "done",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        AnimatedVisibility(visible = expanded && steps.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.takeLast(3).forEach { step -> InlineActivityStep(step) }
                }
            }
        }
    }
}

@Composable
private fun InlineActivityStep(step: ActivityStepItem) {
    var expanded by remember(step.running) { mutableStateOf(step.running) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = step.type.icon, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${step.summary}${if (step.running) "…" else ""}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = step.detail,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 112.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
            )
        }
    }
}

@Composable
private fun PremiumInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    state: AgentRuntimeState,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
) {
    var commandMenuVisible by remember { mutableStateOf(false) }
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { /* Attachment handling will be wired into message state later. */ },
    )
    val showCommandButton = value.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = commandMenuVisible && showCommandButton) {
            CommandMenu(
                onCommandSelected = { command ->
                    onValueChange(command)
                    commandMenuVisible = false
                },
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        ),
                    ),
                    shape = RoundedCornerShape(999.dp),
                ),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnimatedVisibility(visible = showCommandButton) {
                    CompactIconButton(onClick = { commandMenuVisible = !commandMenuVisible }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Command menu",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CompactIconButton(onClick = { attachmentPicker.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircleOutline,
                        contentDescription = "Attach file",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        commandMenuVisible = false
                        onValueChange(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp, max = 112.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primaryContainer),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = if (state == AgentRuntimeState.Running) "Steer ClawDroid..." else "Message ClawDroid...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (state == AgentRuntimeState.Running) {
                    Button(
                        onClick = onStop,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontSize = 13.sp)
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable(onClick = onSubmit),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandMenu(onCommandSelected: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CommandMenuItem("/help", "Show available commands", onCommandSelected)
            CommandMenuItem("/clear", "Start a fresh chat", onCommandSelected)
            CommandMenuItem("/runtime", "Check Linux runtime", onCommandSelected)
        }
    }
}

@Composable
private fun CommandMenuItem(
    command: String,
    description: String,
    onCommandSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .clickable { onCommandSelected(command) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = command,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
