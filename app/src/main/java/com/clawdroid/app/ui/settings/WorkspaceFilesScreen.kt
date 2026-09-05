package com.clawdroid.app.ui.settings

import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.workspace.WorkspaceFile
import com.clawdroid.app.core.workspace.WorkspaceFileManager
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import com.clawdroid.app.ui.components.GlowText
import com.clawdroid.app.ui.theme.DeepBlack
import com.clawdroid.app.ui.theme.EmberOrange
import com.clawdroid.app.ui.theme.GlassBorderDim
import com.clawdroid.app.ui.theme.GlassFill
import com.clawdroid.app.ui.theme.MutedGray
import com.clawdroid.app.ui.theme.SoftWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceFilesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var files by remember { mutableStateOf(WorkspaceFileManager.all(context)) }
    var editing by remember { mutableStateOf<WorkspaceFile?>(null) }
    var editorText by remember { mutableStateOf("") }

    fun reload() {
        files = WorkspaceFileManager.all(context)
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = { Text("Workspace Files", color = SoftWhite, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBlack),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlowText("Agent Workspace", style = MaterialTheme.typography.titleLarge)
            files.forEach { file ->
                WorkspaceFileRow(file = file) {
                    editing = file
                    editorText = file.content
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    editing?.let { file ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(file.title, color = SoftWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(file.description, color = MutedGray, style = MaterialTheme.typography.bodySmall)
                    GlassTextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        singleLine = false,
                        maxLines = 18,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.height(320.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    WorkspaceFileManager.save(context, file.name, editorText)
                    reload()
                    editing = null
                    Toast.makeText(context, "${file.name} saved", Toast.LENGTH_SHORT).show()
                }) { Text("SAVE", color = EmberOrange) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        WorkspaceFileManager.reset(context, file.name)
                        reload()
                        editing = null
                    }) { Text("RESET", color = MutedGray) }
                    TextButton(onClick = { editing = null }) { Text("CANCEL", color = SoftWhite) }
                }
            },
            containerColor = DeepBlack,
        )
    }
}

@Composable
private fun WorkspaceFileRow(file: WorkspaceFile, onClick: () -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = EmberOrange, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.title, color = SoftWhite, fontWeight = FontWeight.Bold)
                Text(file.description, color = MutedGray, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Modified ${DateFormat.format("MMM d, h:mm a", file.lastModified)}",
                    color = MutedGray.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MutedGray, modifier = Modifier.size(18.dp))
        }
    }
}
