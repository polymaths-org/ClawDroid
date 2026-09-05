package com.clawdroid.app.ui.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.ProjectEntity
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

    val navItems = listOf(
        NavItem("Terminal", Icons.Rounded.Terminal, onNavigateToTerminal),
        NavItem("Agent Config", Icons.Rounded.Tune, onNavigateToAgentConfig),
        NavItem("Audio", Icons.Rounded.Album, onNavigateToAudio),
        NavItem("Skills", Icons.Rounded.Extension, onNavigateToSkills),
        NavItem("Channels", Icons.Rounded.Cable, onNavigateToChannels),
        NavItem("MCP", Icons.Rounded.Api, onNavigateToMcp),
        NavItem("Automations", Icons.Rounded.Autorenew, onNavigateToAutomations),
        NavItem("Settings", Icons.Rounded.Settings, onNavigateToSettings),
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.md, vertical = Dimens.md),
    ) {
        Spacer(modifier = Modifier.height(Dimens.sm))

        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(Dimens.xl))

        // Navigation items
        SectionLabel("Navigation")
        Spacer(modifier = Modifier.height(Dimens.sm))

        navItems.forEach { item ->
            NavRow(item = item)
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(Dimens.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(Dimens.md))

        // Chats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Chats")
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
        Spacer(modifier = Modifier.height(Dimens.sm))

        val standaloneChats = conversations.filter { it.projectId == null }.sortedByDescending { it.updatedAt }
        if (standaloneChats.isEmpty()) {
            EmptyLabel("No chats yet. Tap + to start.")
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

        Spacer(modifier = Modifier.height(Dimens.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(Dimens.md))

        // Projects
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
        Spacer(modifier = Modifier.height(Dimens.sm))

        if (projects.isEmpty()) {
            EmptyLabel("No projects yet.")
        } else {
            projects.forEach { project ->
                ProjectSection(
                    project = project,
                    conversations = conversations.filter { it.projectId == project.id },
                    activeConversationId = activeConversationId,
                    onSelectConversation = onSelectConversation,
                    onNewThread = { onNewConversation(project.id) },
                )
            }
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun EmptyLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = Dimens.md),
    )
}

@Composable
private fun NavRow(item: NavItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = item.onClick)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSize),
        )
        Spacer(modifier = Modifier.width(Dimens.md))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
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
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            .copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        label = "chat_bg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ChatBubble,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(Dimens.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

        if (expanded) {
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
