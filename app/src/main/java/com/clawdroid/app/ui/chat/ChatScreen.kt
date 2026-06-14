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
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.ui.markdown.MarkdownText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.json.JSONObject
import android.widget.Toast

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
    var runningActivityId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val db = ClawDroidDatabase.get(context.applicationContext)
        val recentConv = db.conversations().getMostRecent()
        if (recentConv != null) {
            val messages = db.messages().getAll(recentConv.id)
            for (msg in messages) {
                // Ignore special system prompts and compaction summaries in the raw list,
                // or load them nicely. We only want to load user and assistant text bubbles.
                if (msg.role == "user" && !msg.content.startsWith("Previous conversation summary:")) {
                    items += UserChatItem(text = msg.content)
                } else if (msg.role == "assistant" && !msg.content.startsWith("[Compacted Summary]")) {
                    items += AgentChatItem(text = msg.content, streaming = false)
                }
            }
        }
    }

    fun ensureAgentMessage(): String {
        val existingId = runningAgentMessageId
        if (existingId != null) return existingId
        val message = AgentChatItem(text = "", streaming = true)
        items += message
        runningAgentMessageId = message.id
        return message.id
    }

    fun finishCurrentAgentText() {
        runningAgentMessageId?.let { id ->
            items.replaceAgentMessage(id) { it.copy(streaming = false) }
        }
        runningAgentMessageId = null
    }

    fun finishCurrentActivity() {
        runningActivityId?.let { id ->
            items.replaceActivityItem(id) { it.copy(running = false, steps = it.steps.markAllComplete()) }
        }
        runningActivityId = null
    }

    fun stopCurrentRun(reason: String = "Stopped") {
        engine?.stop()
        runJob?.cancel()
        runningAgentMessageId?.let { id ->
            items.replaceAgentMessage(id) { current ->
                current.copy(text = current.text.ifBlank { reason }, streaming = false)
            }
        }
        runningActivityId?.let { id ->
            items.replaceActivityItem(id) { current ->
                current.copy(running = false, steps = current.steps.markLastComplete(reason))
            }
        }
        runtimeState = AgentRuntimeState.Idle
        runningAgentMessageId = null
        runningActivityId = null
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
        val initialAgentMessageId = ensureAgentMessage()

        val newEngine = AgentEngine(context.applicationContext)
        engine = newEngine
        runtimeState = AgentRuntimeState.Running
        runJob = scope.launch {
            runCatching {
                newEngine.run(text).collect { event ->
                    when (event) {
                        is AgentRunEvent.TextDelta -> {
                            finishCurrentActivity()
                            val messageId = ensureAgentMessage()
                            items.replaceAgentMessage(messageId) { current ->
                                current.copy(text = current.text + event.text, streaming = true)
                            }
                        }

                        is AgentRunEvent.ToolCallRequested -> {
                            finishCurrentAgentText()
                            val step = ActivityStepItem(
                                callId = event.call.id,
                                type = event.call.name.toActivityStepType(),
                                summary = event.call.name.readableToolName(),
                                detail = event.call.arguments,
                                arguments = event.call.arguments,
                                running = true,
                            )
                            val activityId = runningActivityId
                            if (activityId == null) {
                                val activity = ActivityChatItem(steps = listOf(step), running = true)
                                items += activity
                                runningActivityId = activity.id
                            } else {
                                items.replaceActivityItem(activityId) { current ->
                                    current.copy(running = true, steps = current.steps + step)
                                }
                            }
                        }

                        is AgentRunEvent.ToolOutputUpdated -> {
                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
                                    current.copy(
                                        steps = current.steps.map { step ->
                                            if (step.callId == event.callId) {
                                                val mockResult = JSONObject().put("output", event.output).toString()
                                                step.copy(result = mockResult)
                                            } else {
                                                step
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        is AgentRunEvent.ToolResultReceived -> {
                            val isError = event.result.isError || runCatching {
                                val obj = JSONObject(event.result.content)
                                obj.optInt("exit_code", 0) != 0 || obj.has("error")
                            }.getOrDefault(false)

                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
                                    current.copy(running = true, steps = current.steps.markLastComplete(event.result.content, isError = isError))
                                }
                            }
                        }

                        is AgentRunEvent.SteeringApplied -> {
                            val activity = ActivityChatItem(
                                steps = listOf(
                                    ActivityStepItem(
                                        type = ActivityStepType.Service,
                                        summary = "Applied steering",
                                        detail = event.message,
                                    )
                                ),
                                running = false,
                            )
                            items += activity
                        }

                        is AgentRunEvent.LoopWarning -> {
                            val activity = ActivityChatItem(
                                steps = listOf(
                                    ActivityStepItem(
                                        type = ActivityStepType.Service,
                                        summary = "Loop warning",
                                        detail = event.message,
                                    )
                                ),
                                running = false,
                            )
                            items += activity
                        }

                        is AgentRunEvent.Completed -> {
                            runningAgentMessageId?.let { id ->
                                items.replaceAgentMessage(id) { current ->
                                    current.copy(text = current.text.ifBlank { event.finalText }, streaming = false)
                                }
                            }
                            finishCurrentActivity()
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                            runningActivityId = null
                        }

                        is AgentRunEvent.Stopped -> {
                            runningAgentMessageId?.let { id ->
                                items.replaceAgentMessage(id) { current ->
                                    current.copy(text = current.text.ifBlank { "Stopped: ${event.reason}" }, streaming = false)
                                }
                            }
                            runningActivityId?.let { id ->
                                items.replaceActivityItem(id) { current ->
                                    current.copy(running = false, steps = current.steps.markLastComplete(event.reason))
                                }
                            }
                            runtimeState = AgentRuntimeState.Idle
                            runningAgentMessageId = null
                            runningActivityId = null
                        }
                    }
                }
            }.onFailure { error ->
                val messageId = runningAgentMessageId ?: initialAgentMessageId
                items.replaceAgentMessage(messageId) { current ->
                    current.copy(
                        text = current.text.ifBlank { "Error: ${error.message ?: error::class.java.simpleName}" },
                        streaming = false,
                    )
                }
                runningActivityId?.let { id ->
                    items.replaceActivityItem(id) { current ->
                        current.copy(running = false, steps = current.steps.markLastComplete(error.message ?: "Run failed", isError = true))
                    }
                }
                runtimeState = AgentRuntimeState.Idle
                runningAgentMessageId = null
                runningActivityId = null
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
                            is ActivityChatItem -> ActivityMessageCard(item)
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
    MarkdownText(
        markdown = item.text.ifBlank { if (item.streaming) "Thinking…" else "" },
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ActivityMessageCard(item: ActivityChatItem) {
    InlineActivityTrail(steps = item.steps, running = item.running)
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
        commandCount > 0 -> "$commandCount command${if (commandCount == 1) "" else "s"}"
        latest != null -> latest.summary
        else -> "Preparing activity"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (running) "Running ·" else "Done ·",
                    color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = if (expanded) "Hide" else "Details",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
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
            if (step.isError) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "❌",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val parsed = formatStepContent(step)

                // Input section (Command, Path, URL, etc.)
                if (parsed.copyText != null || parsed.displayText.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (parsed.title.isNotEmpty()) {
                                Text(
                                    text = parsed.title,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Text(
                                text = parsed.displayText,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        if (parsed.copyText != null) {
                            val clipboardManager = LocalClipboardManager.current
                            val context = LocalContext.current
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(parsed.copyText))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy text",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Output section
                if (parsed.outputText.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = parsed.outputText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
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

private fun MutableList<ChatItem>.replaceActivityItem(id: String, transform: (ActivityChatItem) -> ActivityChatItem) {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) this[index] = transform(this[index] as ActivityChatItem)
}

private fun List<ActivityStepItem>.markLastComplete(detail: String, isError: Boolean = false): List<ActivityStepItem> {
    if (isEmpty()) return this
    val last = last()
    return dropLast(1) + last.copy(
        detail = detail,
        result = detail,
        running = false,
        isError = isError
    )
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

private data class StepDetails(
    val title: String,
    val copyText: String?,
    val displayText: String,
    val outputText: String
)

private fun formatStepContent(step: ActivityStepItem): StepDetails {
    val argsObj = runCatching { JSONObject(step.arguments) }.getOrNull()
    val resultObj = if (!step.result.isNullOrBlank()) {
        runCatching { JSONObject(step.result) }.getOrNull()
    } else {
        null
    }

    var title = "Input:"
    var copyText: String? = null
    var displayText = ""
    var outputText = ""

    val isError = resultObj?.has("error") == true || step.isError
    val errorMessage = resultObj?.optString("error")?.takeIf { it.isNotBlank() }
        ?: if (step.isError && step.result != null && !step.result.startsWith("{")) step.result else null

    val toolName = step.summary.lowercase().replace(" ", "_")

    when {
        toolName == "execute_command" || toolName == "start_process" -> {
            title = "Command:"
            val cmd = argsObj?.optString("command") ?: ""
            copyText = cmd
            displayText = cmd
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    if (toolName == "execute_command") {
                        val exitCode = resultObj.optInt("exit_code", 0)
                        val out = resultObj.optString("output") ?: ""
                        if (exitCode != 0) {
                            "Exit Code: $exitCode\n$out".trim()
                        } else {
                            out
                        }
                    } else {
                        val procId = resultObj.optString("process_id") ?: ""
                        val initOut = resultObj.optString("initial_output") ?: ""
                        "Process Started (ID: $procId)\n$initOut".trim()
                    }
                }
                step.running -> "Executing..."
                else -> ""
            }
        }
        
        toolName == "check_process" || toolName == "kill_process" -> {
            title = "Process ID:"
            val procId = argsObj?.optString("process_id") ?: ""
            copyText = procId
            displayText = procId
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val cmd = resultObj.optString("command") ?: ""
                    val state = resultObj.optString("state") ?: ""
                    val exitCode = resultObj.optInt("exit_code", -1)
                    val recent = resultObj.optString("recent_output") ?: ""
                    buildString {
                        append("Command: $cmd\n")
                        append("State: $state")
                        if (exitCode != -1) append(" (Exit Code: $exitCode)")
                        if (recent.isNotEmpty()) append("\n\nOutput:\n$recent")
                    }
                }
                step.running -> "Checking process..."
                else -> ""
            }
        }

        toolName == "send_input" -> {
            title = "Send Input:"
            val procId = argsObj?.optString("process_id") ?: ""
            val inputVal = argsObj?.optString("input") ?: ""
            copyText = inputVal
            displayText = "Process ID: $procId\nInput: $inputVal"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val state = resultObj.optString("state") ?: ""
                    val recent = resultObj.optString("recent_output") ?: ""
                    buildString {
                        append("State: $state\n\nRecent Output:\n$recent")
                    }
                }
                step.running -> "Sending input..."
                else -> ""
            }
        }

        toolName == "list_processes" -> {
            title = ""
            copyText = null
            displayText = "Listing active processes"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val array = resultObj.optJSONArray("processes")
                    if (array != null && array.length() > 0) {
                        buildString {
                            for (i in 0 until array.length()) {
                                val proc = array.optJSONObject(i) ?: continue
                                val pid = proc.optString("process_id")
                                val cmd = proc.optString("command")
                                val state = proc.optString("state")
                                append("[$pid] $state: $cmd\n")
                            }
                        }.trim()
                    } else {
                        "No active processes found."
                    }
                }
                step.running -> "Retrieving process list..."
                else -> ""
            }
        }

        toolName == "read_file" -> {
            title = "Read File Path:"
            val path = argsObj?.optString("path") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> resultObj.optString("content") ?: ""
                step.running -> "Reading file..."
                else -> ""
            }
        }

        toolName == "write_file" -> {
            title = "Write File Path:"
            val path = argsObj?.optString("path") ?: ""
            val content = argsObj?.optString("content") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val bytes = resultObj.optLong("bytes", 0)
                    "Successfully wrote $bytes bytes."
                }
                step.running -> "Writing file..."
                else -> content
            }
        }

        toolName == "edit_file" -> {
            title = "Edit File Path:"
            val path = argsObj?.optString("path") ?: ""
            val search = argsObj?.optString("search") ?: ""
            val replace = argsObj?.optString("replace") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val replacements = resultObj.optInt("replacements", 0)
                    "Made $replacements replacement(s)."
                }
                step.running -> "Editing file...\nSearch:\n$search\n\nReplace:\n$replace"
                else -> ""
            }
        }

        toolName == "list_directory" -> {
            title = "Directory Path:"
            val path = argsObj?.optString("path") ?: ""
            copyText = path
            displayText = path
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val entries = resultObj.optJSONArray("entries")
                    if (entries != null && entries.length() > 0) {
                        buildString {
                            for (i in 0 until entries.length()) {
                                val entry = entries.optJSONObject(i) ?: continue
                                val name = entry.optString("name")
                                val type = entry.optString("type")
                                val bytes = entry.optLong("bytes", -1)
                                val sizeStr = if (type == "file" && bytes != -1L) {
                                    formatBytes(bytes)
                                } else ""
                                append("- [${type.capitalize()}] $name${if (sizeStr.isNotEmpty()) " ($sizeStr)" else ""}\n")
                            }
                        }.trim()
                    } else {
                        "Directory is empty."
                    }
                }
                step.running -> "Listing directory contents..."
                else -> ""
            }
        }

        toolName == "browse_web" -> {
            title = "Browse URL:"
            val url = argsObj?.optString("url") ?: ""
            copyText = url
            displayText = url
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> resultObj.optString("content") ?: ""
                step.running -> "Browsing webpage..."
                else -> ""
            }
        }

        toolName == "web_search" -> {
            title = "Search Query:"
            val query = argsObj?.optString("query") ?: ""
            copyText = query
            displayText = query
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> {
                    val results = resultObj.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        buildString {
                            for (i in 0 until results.length()) {
                                val item = results.optJSONObject(i) ?: continue
                                val titleText = item.optString("title")
                                val url = item.optString("url")
                                val snippet = item.optString("snippet")
                                append("${i + 1}. $titleText\n   $url\n   $snippet\n\n")
                            }
                        }.trim()
                    } else {
                        "No search results found."
                    }
                }
                step.running -> "Searching DuckDuckGo..."
                else -> ""
            }
        }

        toolName == "send_notification" -> {
            title = "Notification:"
            val noteTitle = argsObj?.optString("title") ?: ""
            val noteBody = argsObj?.optString("body") ?: ""
            copyText = null
            displayText = "Title: $noteTitle\nBody: $noteBody"
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> "Notification sent successfully."
                step.running -> "Sending notification..."
                else -> ""
            }
        }

        else -> {
            title = "Arguments:"
            copyText = step.arguments
            displayText = step.arguments
            
            outputText = when {
                errorMessage != null -> "Error: $errorMessage"
                resultObj != null -> step.result ?: ""
                step.detail.isNotEmpty() -> step.detail
                else -> ""
            }
        }
    }

    if (errorMessage != null) {
        outputText = "Error: $errorMessage"
    }

    return StepDetails(title, copyText, displayText, outputText)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    val formattedVal = (bytes * 10 / Math.pow(1024.0, exp.toDouble())).toLong() / 10.0
    return "$formattedVal ${pre}B"
}

private fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

