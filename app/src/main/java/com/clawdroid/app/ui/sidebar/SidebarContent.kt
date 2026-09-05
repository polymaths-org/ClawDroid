package com.clawdroid.app.ui.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.ProjectEntity
import com.clawdroid.app.ui.components.ClawSkin
import com.clawdroid.app.ui.components.currentClawSkin
import com.clawdroid.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import java.util.UUID

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SidebarContent(
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewConversation: (projectId: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onNavigateToAutomations: () -> Unit,
    onNavigateToChannels: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToAgentConfig: () -> Unit,
    onNavigateToTerminal: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ClawDroidDatabase.get(context) }

    val projects by db.projects().observeProjects().collectAsState(initial = emptyList())
    val conversations by db.conversations().observeConversations().collectAsState(initial = emptyList())
    var showCreateProject by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var showAllChats by remember { mutableStateOf(false) }
    var showAllProjects by remember { mutableStateOf(false) }
    val skin = currentClawSkin()
    val isMagic = skin == ClawSkin.ClawMagic

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(
                if (isMagic) MaterialTheme.colorScheme.background
                else MaterialTheme.colorScheme.surface
            )
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (isMagic) 20.dp else Dimens.md,
                end = if (isMagic) 20.dp else Dimens.md,
                top = if (isMagic) 52.dp else 22.dp,
                bottom = 22.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(
                    text = "Claw",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, letterSpacing = 0.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                )
                Text(
                    text = "Droid",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, letterSpacing = 0.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(if (isMagic) 34.dp else 44.dp)
                    .clip(CircleShape)
                    .background(if (isMagic) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                    .border(1.dp, Color.White.copy(alpha = if (isMagic) 0.08f else 0f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isMagic) 0.60f else 1f),
                    modifier = Modifier.size(if (isMagic) 18.dp else 24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        val activeConversation = conversations.firstOrNull { it.id == activeConversationId }
        val totalCost = conversations.sumOf { it.costUsd }
        val totalTokens = conversations.sumOf {
            it.totalPromptTokens + it.totalCompletionTokens + it.totalCachedTokens
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SidebarInfoCard(
                icon = Icons.Rounded.Settings,
                title = "Provider",
                value = AppConfigManager.provider.replaceFirstChar { it.uppercaseChar() },
                detail = AppConfigManager.baseUrl.removePrefix("https://").removePrefix("http://"),
            )
            SidebarInfoCard(
                icon = Icons.Rounded.AutoAwesome,
                title = "Model",
                value = AppConfigManager.model.substringAfterLast('/'),
                detail = AppConfigManager.model,
            )
            SidebarInfoCard(
                icon = Icons.Rounded.Extension,
                title = "Context",
                value = formatTokenCount(activeConversation?.lastPromptTokens?.toLong() ?: 0L),
                detail = "Last request · ${formatTokenCount(totalTokens)} total",
            )
            SidebarInfoCard(
                icon = Icons.Rounded.Schedule,
                title = "Billing",
                value = "$" + String.format("%.4f", totalCost),
                detail = "${conversations.size} chats tracked",
            )
            SidebarTerminalButton(onClick = onNavigateToTerminal)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Recents")
            IconButton(
                onClick = { onNewConversation(null) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New chat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        val standaloneChats = conversations.filter { it.projectId == null }.sortedByDescending { it.updatedAt }
        if (standaloneChats.isEmpty()) {
            EmptyLabel("No chats yet")
        } else {
            val visibleChats = if (showAllChats) standaloneChats else standaloneChats.take(4)
            visibleChats.forEach { chat ->
                ChatRow(
                    title = chat.title,
                    selected = chat.id == activeConversationId,
                    onClick = { onSelectConversation(chat.id) },
                )
            }
            if (standaloneChats.size > 4) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (showAllChats) "Show less" else "Show more (${standaloneChats.size - 4} more)",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clickable { showAllChats = !showAllChats }
                        .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Projects")
            IconButton(
                onClick = { showCreateProject = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New project",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (projects.isEmpty()) {
            EmptyLabel("No projects yet.")
        } else {
            val visibleProjects = if (showAllProjects) projects else projects.take(4)
            visibleProjects.forEach { project ->
                ProjectSection(
                    project = project,
                    conversations = conversations.filter { it.projectId == project.id },
                    activeConversationId = activeConversationId,
                    onSelectConversation = onSelectConversation,
                    onNewThread = { onNewConversation(project.id) },
                )
            }
            if (projects.size > 4) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (showAllProjects) "Show less" else "Show more (${projects.size - 4} more)",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clickable { showAllProjects = !showAllProjects }
                        .padding(horizontal = Dimens.md, vertical = Dimens.sm)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = Color.White.copy(alpha = if (isMagic) 0.05f else 0.28f))
        Spacer(modifier = Modifier.height(if (isMagic) 14.dp else 12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onNavigateToSettings)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "N",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ClawDroid",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, letterSpacing = 0.sp),
                )
                Text(
                    "Workspace · ${AppConfigManager.agentName.ifBlank { "WhiteRose" }}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(Dimens.xxl))
    }

    if (showCreateProject) {
        CreateProjectDialog(
            value = newProjectName,
            onValueChange = { newProjectName = it },
            onConfirm = {
                val name = newProjectName.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        val projectId = UUID.randomUUID().toString()
                        db.projects().upsert(
                            ProjectEntity(
                                id = projectId,
                                name = name,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                        onNewConversation(projectId)
                    }
                }
                showCreateProject = false
                newProjectName = ""
            },
            onDismiss = {
                showCreateProject = false
                newProjectName = ""
            },
        )
    }
}

@Composable
private fun SidebarInfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
) {
    val isMagic = currentClawSkin() == ClawSkin.ClawMagic
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isMagic) Color.White.copy(alpha = 0.045f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f)
            )
            .border(
                1.dp,
                if (isMagic) Color.White.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape,
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    letterSpacing = 0.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SidebarTerminalButton(
    onClick: () -> Unit,
) {
    val isMagic = currentClawSkin() == ClawSkin.ClawMagic
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(
                if (isMagic) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
            )
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Open terminal",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
    else -> tokens.toString()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun EmptyLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

@Composable
private fun NavRow(item: NavItem, prominent: Boolean = false, selected: Boolean = false) {
    val isMagic = currentClawSkin() == ClawSkin.ClawMagic
    val shape = RoundedCornerShape(if (prominent) 14.dp else 12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (prominent && isMagic) 48.dp else if (isMagic) 44.dp else 48.dp)
            .clip(shape)
            .background(
                when {
                    prominent && isMagic -> Color.White.copy(alpha = 0.05f)
                    selected && isMagic -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    prominent -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    prominent && isMagic -> Color.White.copy(alpha = 0.10f)
                    selected && isMagic -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(onClick = item.onClick)
            .padding(horizontal = if (isMagic) 12.dp else if (prominent) 18.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (prominent && isMagic) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = when {
                selected -> MaterialTheme.colorScheme.primary
                isMagic -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(if (isMagic) 20.dp else if (prominent) 24.dp else 26.dp),
        )
        Spacer(modifier = Modifier.width(if (isMagic) 13.dp else 18.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            ),
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                isMagic -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (prominent || selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ChatRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        label = "chat_bg",
    )
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(bgColor)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
            color = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProjectSection(
    project: ProjectEntity,
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewThread: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded }
                .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(Dimens.sm))
            Text(
                text = project.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                conversations.forEach { chat ->
                    ChatRow(
                        title = chat.title,
                        selected = chat.id == activeConversationId,
                        onClick = { onSelectConversation(chat.id) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onNewThread)
                        .padding(start = 40.dp, end = Dimens.md, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(Dimens.sm))
                    Text(
                        text = "New Thread",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text("New Project", fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Project Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Create", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}
