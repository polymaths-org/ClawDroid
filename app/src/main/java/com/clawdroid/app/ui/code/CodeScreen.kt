package com.clawdroid.app.ui.code

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.AgentRunManager
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ToolCallEntity
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.theme.DeveloperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private const val MAX_CHILDREN = 200
private const val MAX_FILE_BYTES = 150 * 1024
private const val MAX_VIEW_LINES = 1500

private val COMMAND_TOOLS = listOf(
    "execute_command", "start_process", "check_process", "send_input", "kill_process",
)

private val REVIEW_TOOLS = listOf("edit_file", "write_file")

private enum class CodeViewMode { FILES, REVIEWS }

/** VS Code/Replit-style explorer: file tree, review tabs, live command strip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ClawDroidDatabase.get(context) }

    val sandboxHome = remember { File(context.filesDir, "home") }
    val sharedRoot = remember {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "ClawDroid",
        )
    }
    var useShared by remember { mutableStateOf(false) }
    val root = if (useShared) sharedRoot else sandboxHome

    var treeNonce by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(setOf(root.absolutePath)) }
    val openTabs = remember { mutableStateListOf<File>() }
    var selectedTab by remember { mutableStateOf<File?>(null) }

    var lastCommand by remember { mutableStateOf<ToolCallEntity?>(null) }
    var reviews by remember { mutableStateOf<List<ToolCallEntity>>(emptyList()) }
    var mode by remember { mutableStateOf(CodeViewMode.FILES) }
    var treeVisible by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(1f) }
    var promptText by remember { mutableStateOf("") }
    var promptHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (isActive) {
            lastCommand = runCatching { db.toolCalls().getRecentByTools(COMMAND_TOOLS, 1) }
                .getOrNull()?.firstOrNull()
            reviews = runCatching { db.toolCalls().getRecentByTools(REVIEW_TOOLS, 20) }
                .getOrNull() ?: emptyList()
            delay(2000)
        }
    }

    fun sendPrompt() {
        val text = promptText.trim()
        if (text.isBlank()) return
        val convId = AppConfigManager.activeConversationId
        if (convId.isNullOrBlank()) {
            promptHint = "Open a chat first, then send."
            return
        }
        val scoped = buildString {
            append("In Code explorer")
            selectedTab?.let { append(" viewing ${it.absolutePath}") }
            append(": $text")
        }
        runCatching {
            AgentRunManager.startRun(context.applicationContext, convId, scoped)
            promptText = ""
            promptHint = "Sent to agent ✓"
        }.onFailure {
            promptHint = "Could not send: ${it.message ?: "busy"}"
        }
    }

    fun openFile(file: File) {
        if (file.isFile && file.length() <= MAX_FILE_BYTES * 4) {
            if (openTabs.none { it.absolutePath == file.absolutePath }) {
                if (openTabs.size >= 8) openTabs.removeAt(0)
                openTabs.add(file)
            }
            selectedTab = file
        }
    }

    ClawSkinBackground {
        // Code viewer defaults to the Developer theme, whatever the app theme is.
        androidx.compose.material3.MaterialTheme(colorScheme = DeveloperColors) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!fullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "</> ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (useShared) "Shared files" else "Sandbox",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = root.absolutePath,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { useShared = !useShared }) {
                            Icon(
                                if (useShared) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                                contentDescription = "Switch root",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { treeNonce++ }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh tree")
                        }
                        IconButton(onClick = onOpenTerminal) {
                            Icon(Icons.Rounded.Terminal, contentDescription = "Open terminal")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                    ),
                )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
            ) {
                // Toolbar: mode toggle, tree collapse, fullscreen, zoom
                if (!fullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolbarChip(
                            label = "Files",
                            selected = mode == CodeViewMode.FILES,
                            onClick = { mode = CodeViewMode.FILES },
                        )
                        ToolbarChip(
                            label = "Reviews ${if (reviews.isNotEmpty()) "(${reviews.size})" else ""}",
                            selected = mode == CodeViewMode.REVIEWS,
                            onClick = { mode = CodeViewMode.REVIEWS },
                        )
                        ToolbarChip(
                            label = if (treeVisible) "Hide tree" else "Tree",
                            selected = false,
                            onClick = { treeVisible = !treeVisible },
                        )
                        ToolbarChip(label = "Full", selected = false, onClick = { fullscreen = true })
                        ToolbarChip(
                            label = "A-",
                            selected = false,
                            onClick = { fontScale = (fontScale - 0.15f).coerceAtLeast(0.7f) },
                        )
                        ToolbarChip(
                            label = "A+",
                            selected = false,
                            onClick = { fontScale = (fontScale + 0.15f).coerceAtMost(1.8f) },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolbarChip(label = "Exit full", selected = true, onClick = { fullscreen = false })
                        ToolbarChip(
                            label = "A-",
                            selected = false,
                            onClick = { fontScale = (fontScale - 0.15f).coerceAtLeast(0.7f) },
                        )
                        ToolbarChip(
                            label = "A+",
                            selected = false,
                            onClick = { fontScale = (fontScale + 0.15f).coerceAtMost(1.8f) },
                        )
                    }
                }
                // Tabs
                if (!fullscreen && mode == CodeViewMode.FILES && openTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        openTabs.forEach { file ->
                            val selected = file.absolutePath == selectedTab?.absolutePath
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { selectedTab = file }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = file.name,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            openTabs.remove(file)
                                            if (selectedTab?.absolutePath == file.absolutePath) {
                                                selectedTab = openTabs.lastOrNull()
                                            }
                                        },
                                )
                            }
                        }
                    }
                }

                // Tree + viewer, or GitHub-style reviews
                if (mode == CodeViewMode.REVIEWS && !fullscreen) {
                    ReviewsList(
                        reviews = reviews,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val nodes = remember(root, treeNonce, expanded) {
                        flattenTree(root, expanded)
                    }
                    if (treeVisible && !fullscreen) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxSize()
                            .padding(start = 8.dp, end = 4.dp),
                        state = rememberLazyListState(),
                    ) {
                        items(nodes, key = { it.file.absolutePath }) { node ->
                            TreeRow(
                                node = node,
                                onToggle = { file ->
                                    val path = file.absolutePath
                                    expanded = if (expanded.contains(path)) expanded - path
                                    else expanded + path
                                },
                                onOpen = { openFile(it) },
                            )
                        }
                    }
                    } // treeVisible
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxSize()
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    ) {
                        val tab = selectedTab
                        if (tab == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Pick a file to review it side-by-side while the agent works.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        } else {
                            val content = remember(tab, treeNonce) { readCapped(tab) }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = tab.name,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp, 8.dp, 10.dp, 4.dp),
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                ) {
                                    val viewLines = content.lines().take(MAX_VIEW_LINES)
                                    itemsIndexed(viewLines) { index, line ->
                                        Row {
                                            Text(
                                                text = "${index + 1}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (10 * fontScale).sp,
                                                lineHeight = (16 * fontScale).sp,
                                                modifier = Modifier.width((32 * fontScale).dp),
                                            )
                                            Text(
                                                text = line.ifBlank { " " },
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (11 * fontScale).sp,
                                                lineHeight = (16 * fontScale).sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } // FILES tree+viewer row
                } // FILES/REVIEWS else

                // Prompt bar: last command status + input that sends to the agent
                if (!fullscreen) {
                    PromptBar(
                        last = lastCommand,
                        value = promptText,
                        onValueChange = { promptText = it; promptHint = null },
                        hint = promptHint,
                        onSend = { sendPrompt() },
                        onRefresh = {
                            scope.launch {
                                lastCommand = runCatching { db.toolCalls().getRecentByTools(COMMAND_TOOLS, 1) }
                                    .getOrNull()?.firstOrNull()
                            }
                        },
                    )
                } // prompt bar
            }
        }
        }
    }
}

private data class FlatNode(val file: File, val depth: Int, val expanded: Boolean, val isDir: Boolean)

private fun flattenTree(root: File, expanded: Set<String>): List<FlatNode> {
    val out = mutableListOf<FlatNode>()
    fun visit(dir: File, depth: Int) {
        val kids = (dir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .take(MAX_CHILDREN)
        kids.forEach { kid ->
            val isDir = kid.isDirectory
            val isOpen = expanded.contains(kid.absolutePath)
            out.add(FlatNode(kid, depth, isOpen, isDir))
            if (isDir && isOpen) visit(kid, depth + 1)
        }
    }
    if (root.exists()) visit(root, 0)
    return out
}

@Composable
private fun TreeRow(
    node: FlatNode,
    onToggle: (File) -> Unit,
    onOpen: (File) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { if (node.isDir) onToggle(node.file) else onOpen(node.file) }
            .padding(start = (4 + node.depth * 14).dp, top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDir) {
            Icon(
                if (node.expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Icon(
            if (node.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            tint = if (node.isDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = node.file.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontFamily = if (node.isDir) null else FontFamily.Monospace,
            fontWeight = if (node.isDir) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private fun readCapped(file: File): String = runCatching {
    if (!file.isFile || file.length() > MAX_FILE_BYTES * 4) return "(file too large to preview)"
    val text = file.readText().take(MAX_FILE_BYTES)
    if (text.any { it == '\u0000' }) return "(binary file)"
    text
}.getOrDefault("(cannot read file)")

@Composable
private fun ToolbarChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PromptBar(
    last: ToolCallEntity?,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String?,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (last?.status) {
                            "completed" -> Color(0xFF81C784)
                            "error" -> MaterialTheme.colorScheme.error
                            "running" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        },
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = last?.let { describeToolCall(it) } ?: "No agent command yet — type below to prompt the agent.",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Prompt agent about this code…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            IconButton(onClick = onSend, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send prompt",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ReviewsList(
    reviews: List<ToolCallEntity>,
    modifier: Modifier = Modifier,
) {
    if (reviews.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No file edits yet. When the agent creates, edits, or rewrites files, each change shows here GitHub-style.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reviews, key = { it.id }) { call ->
            ReviewCard(call = call)
        }
    }
}

@Composable
private fun ReviewCard(call: ToolCallEntity) {
    var expanded by remember { mutableStateOf(true) }
    val args = remember(call.arguments) { runCatching { JSONObject(call.arguments) }.getOrNull() }
    val path = args?.optString("path")?.takeIf { it.isNotBlank() } ?: call.toolName
    val diff = remember(call.id) {
        when (call.toolName) {
            "edit_file" -> diffFromEdit(
                path,
                args?.optString("search") ?: "",
                args?.optString("replace") ?: "",
            )
            else -> diffFromWrite(path, args?.optString("content") ?: "")
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .clickable { expanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (call.toolName == "edit_file") "📝" else "📄",
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = path.substringAfterLast('/'),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+${diff.added}",
                color = Color(0xFF81C784),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "−${diff.deleted}",
                color = Color(0xFFE57373),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = path,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            val shown = diff.lines.take(300)
            shown.forEach { line ->
                val bg = when (line.kind) {
                    DiffKind.ADD -> Color(0xFF81C784).copy(alpha = 0.13f)
                    DiffKind.DEL -> Color(0xFFE57373).copy(alpha = 0.13f)
                    DiffKind.SAME -> Color.Transparent
                }
                val fg = when (line.kind) {
                    DiffKind.ADD -> Color(0xFFA5D6A7)
                    DiffKind.DEL -> Color(0xFFEF9A9A)
                    DiffKind.SAME -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                }
                val sign = when (line.kind) {
                    DiffKind.ADD -> "+"
                    DiffKind.DEL -> "−"
                    DiffKind.SAME -> " "
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .padding(vertical = 1.dp),
                ) {
                    Text(
                        text = line.oldNo?.toString() ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = line.newNo?.toString() ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = "$sign ${line.text.ifBlank { " " }}",
                        color = fg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
            if (diff.lines.size > shown.size || diff.truncated) {
                Text(
                    text = "… ${diff.lines.size - shown.size} more lines",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun describeToolCall(t: ToolCallEntity): String {
    val cmd = runCatching { JSONObject(t.arguments).optString("command") }.getOrNull()
    val base = if (!cmd.isNullOrBlank()) "$ ${cmd.take(120)}" else "${t.toolName} ${t.arguments.take(100)}"
    val status = when (t.status) {
        "completed" -> "done"
        "error" -> "failed"
        else -> t.status
    }
    return "$base  ·  $status ${t.durationMs}ms"
}
