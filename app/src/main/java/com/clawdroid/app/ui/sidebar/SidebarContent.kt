package com.clawdroid.app.ui.sidebar

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.data.db.ProjectEntity
import com.clawdroid.app.ui.theme.CardDark
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.FireRed
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MoltenYellow
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SidebarContent(
    activeConversationId: String?,
    onNavigateToSettings: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: (projectId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ClawDroidDatabase.get(context) }

    val projects by db.projects().observeProjects().collectAsState(initial = emptyList())
    val conversations by db.conversations().observeConversations().collectAsState(initial = emptyList())

    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var showAllChats by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(DeepBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Logo
        Text(
            text = "🐙 ClawDroid",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("Quick Actions")
        DrawerAction("Automations", Icons.Rounded.Timer, onClick = onNavigateToSettings)
        DrawerAction("Connected Services", Icons.Rounded.History, onClick = onNavigateToSettings)
        DrawerAction("Settings", Icons.Rounded.Settings, onClick = onNavigateToSettings)

        SectionDivider()

        // ── CHATS HEADER ──
        SectionHeader(
            title = "Chats",
            actionContentDescription = "New chat",
            onActionClick = { onNewConversation(null) }
        )

        val standaloneChats = conversations.filter { it.projectId == null }.sortedByDescending { it.updatedAt }
        if (standaloneChats.isEmpty()) {
            Text(
                text = "No active chats. Start one above!",
                color = MutedGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            )
        } else {
            val visibleChats = if (showAllChats) standaloneChats else standaloneChats.take(4)
            visibleChats.forEach { chat ->
                DrawerTextItem(
                    label = "💬 ${chat.title}",
                    selected = chat.id == activeConversationId,
                    onClick = { onSelectConversation(chat.id) }
                )
            }
            if (standaloneChats.size > 4) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (showAllChats) "Show less" else "Show more (${standaloneChats.size - 4} more)",
                    color = EmberOrange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clickable { showAllChats = !showAllChats }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        SectionDivider()

        // ── PROJECTS HEADER ──
        SectionHeader(
            title = "Projects",
            actionContentDescription = "New project",
            onActionClick = { showCreateProjectDialog = true }
        )

        if (projects.isEmpty()) {
            Text(
                text = "No projects. Create one above!",
                color = MutedGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            )
        } else {
            projects.forEach { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = "Project folder",
                        tint = EmberOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SoftWhite
                    )
                }

                // Conversations inside project
                val projectChats = conversations.filter { it.projectId == project.id }
                projectChats.forEach { chat ->
                    DrawerTextItem(
                        label = "   💬 ${chat.title}",
                        selected = chat.id == activeConversationId,
                        onClick = { onSelectConversation(chat.id) }
                    )
                }

                // Add thread item
                DrawerTextItem(
                    label = "   + New Thread",
                    selected = false,
                    onClick = { onNewConversation(project.id) }
                )
            }
        }
    }

    // Create Project Dialog
    if (showCreateProjectDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateProjectDialog = false
                newProjectName = ""
            },
            title = {
                Text(
                    text = "New Project",
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Project Name", color = MutedGray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        cursorColor = EmberOrange,
                        focusedIndicatorColor = EmberOrange,
                        unfocusedIndicatorColor = GlassBorderDim
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProjectName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                val projectId = UUID.randomUUID().toString()
                                db.projects().upsert(
                                    ProjectEntity(
                                        id = projectId,
                                        name = name,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                onNewConversation(projectId)
                            }
                        }
                        showCreateProjectDialog = false
                        newProjectName = ""
                    }
                ) {
                    Text("Create", color = EmberOrange)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateProjectDialog = false
                        newProjectName = ""
                    }
                ) {
                    Text("Cancel", color = MutedGray)
                }
            },
            containerColor = DeepBlack,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, GlassBorderDim, RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionContentDescription: String? = null,
    onActionClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = EmberOrange,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionContentDescription != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = actionContentDescription,
                    tint = EmberOrange.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun DrawerAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    NavigationDrawerItem(
        label = { Text(label, color = SoftWhite) },
        selected = false,
        onClick = onClick,
        icon = { Icon(imageVector = icon, contentDescription = null, tint = MutedGray) },
        modifier = Modifier.padding(vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = DeepBlack,
        ),
    )
}

@Composable
private fun DrawerTextItem(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    NavigationDrawerItem(
        label = { Text(label, color = if (selected) SoftWhite else MutedGray) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = GlassFill,
            unselectedContainerColor = DeepBlack,
        ),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .then(
                if (selected) Modifier
                    .clip(shape)
                    .border(1.dp, GlassBorderDim, shape)
                else Modifier
            ),
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        GlassBorderDim,
                        GlassBorderDim.copy(alpha = 0.5f),
                        GlassBorderDim,
                    ),
                ),
            ),
    )
}
