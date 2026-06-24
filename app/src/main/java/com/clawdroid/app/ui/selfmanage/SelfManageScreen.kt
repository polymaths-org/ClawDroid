package com.clawdroid.app.ui.selfmanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.selfmanage.Alarm
import com.clawdroid.app.core.selfmanage.Reminder
import com.clawdroid.app.core.selfmanage.SelfManageRepository
import com.clawdroid.app.core.selfmanage.Todo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SelfManageTab(val label: String) {
    Alarms("Alarms"),
    Reminders("Reminders"),
    Todos("Todos"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SelfManageRepository(context) }
    val alarms by repo.getAllAlarms().collectAsState(initial = emptyList())
    val reminders by repo.getAllReminders().collectAsState(initial = emptyList())
    val todos by repo.getAllTodos().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableStateOf(SelfManageTab.Reminders) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Self Manage", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Alarms, reminders, and todos shared between you and the agent.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                SelfManageTab.entries.forEach { tab ->
                    val count = when (tab) {
                        SelfManageTab.Alarms -> alarms.count { it.enabled }
                        SelfManageTab.Reminders -> reminders.count { !it.completed }
                        SelfManageTab.Todos -> todos.count { !it.completed }
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text("${tab.label} ($count)") },
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    SelfManageTab.Alarms -> items(alarms, key = { it.id }) { alarm ->
                        SelfManageRow(
                            icon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
                            title = "${alarm.hour.toString().padStart(2, '0')}:${alarm.minute.toString().padStart(2, '0')}",
                            subtitle = alarm.label,
                            done = !alarm.enabled,
                            onComplete = { scope.launch { repo.updateAlarm(alarm.copy(enabled = !alarm.enabled)) } },
                            onDelete = { scope.launch { repo.deleteAlarm(alarm.id) } },
                        )
                    }
                    SelfManageTab.Reminders -> items(reminders, key = { it.id }) { reminder ->
                        SelfManageRow(
                            icon = { Icon(Icons.Rounded.EventNote, contentDescription = null) },
                            title = reminder.title,
                            subtitle = "${formatTime(reminder.dueAt)} · priority ${reminder.priority}",
                            done = reminder.completed,
                            onComplete = { scope.launch { repo.completeReminder(reminder.id) } },
                            onDelete = { scope.launch { repo.deleteReminder(reminder.id) } },
                        )
                    }
                    SelfManageTab.Todos -> items(todos, key = { it.id }) { todo ->
                        SelfManageRow(
                            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                            title = todo.title,
                            subtitle = listOfNotNull(todo.dueAt?.let(::formatTime), "priority ${todo.priority}").joinToString(" · "),
                            done = todo.completed,
                            onComplete = { scope.launch { repo.completeTodo(todo.id) } },
                            onDelete = { scope.launch { repo.deleteTodo(todo.id) } },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSelfManageDialog(
            tab = selectedTab,
            onDismiss = { showAddDialog = false },
            onSave = { title, detail, first, second ->
                scope.launch {
                    when (selectedTab) {
                        SelfManageTab.Alarms -> repo.addAlarm(
                            Alarm(
                                label = title,
                                hour = first.coerceIn(0, 23),
                                minute = second.coerceIn(0, 59),
                            )
                        )
                        SelfManageTab.Reminders -> repo.addReminder(
                            Reminder(
                                title = title,
                                description = detail,
                                dueAt = System.currentTimeMillis() + first.coerceAtLeast(1) * 60_000L,
                                priority = second.coerceIn(1, 10),
                            )
                        )
                        SelfManageTab.Todos -> repo.addTodo(
                            Todo(
                                title = title,
                                description = detail,
                                dueAt = first.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 60_000L },
                                priority = second.coerceIn(1, 10),
                            )
                        )
                    }
                    showAddDialog = false
                }
            },
        )
    }
}

@Composable
private fun SelfManageRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    done: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onComplete, modifier = Modifier.size(36.dp)) {
            Icon(
                if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = "Toggle complete",
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddSelfManageDialog(
    tab: SelfManageTab,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var first by remember { mutableStateOf(if (tab == SelfManageTab.Alarms) "7" else "60") }
    var second by remember { mutableStateOf(if (tab == SelfManageTab.Alarms) "0" else "5") }
    val labels = when (tab) {
        SelfManageTab.Alarms -> listOf("Label", "Optional note", "Hour", "Minute")
        SelfManageTab.Reminders -> listOf("Title", "Description", "Due in minutes", "Priority")
        SelfManageTab.Todos -> listOf("Title", "Description", "Due in minutes (0 none)", "Priority")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${tab.label.dropLast(1)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(labels[0]) }, singleLine = true)
                OutlinedTextField(value = detail, onValueChange = { detail = it }, label = { Text(labels[1]) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = first,
                        onValueChange = { first = it.filter(Char::isDigit) },
                        label = { Text(labels[2]) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it.filter(Char::isDigit) },
                        label = { Text(labels[3]) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), detail.trim(), first.toIntOrNull() ?: 0, second.toIntOrNull() ?: 0) },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
