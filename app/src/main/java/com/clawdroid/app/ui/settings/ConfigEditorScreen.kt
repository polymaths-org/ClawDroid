package com.clawdroid.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clawdroid.app.ui.components.GlassButton
import com.clawdroid.app.ui.components.GlassCard
import com.clawdroid.app.ui.components.GlassTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConfigFileType(val fileName: String, val label: String) {
    AGENTS("AGENTS.md", "AGENTS.md — Agent Instructions"),
    SOUL("SOUL.md", "SOUL.md — Agent Identity"),
    TOOLS("TOOLS.md", "TOOLS.md — Tool Rules"),
    SKILL("SKILL.md", "SKILL.md — Core Skills"),
    SYSTEM("SYSTEM.md", "SYSTEM.md — Base Prompt"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    fileType: ConfigFileType,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(fileType) {
        loading = true
        val text = withContext(Dispatchers.IO) {
            val file = File(context.filesDir, fileType.fileName)
            val legacyFile = File(context.filesDir, "CLAUDE.md")
            when {
                file.exists() -> file.readText()
                fileType == ConfigFileType.SYSTEM && legacyFile.exists() -> legacyFile.readText()
                else -> "# ${fileType.fileName}\n\n"
            }
        }
        content = text
        originalContent = text
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(fileType.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(16.dp),
        ) {
            if (loading) {
                Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Edit ${fileType.fileName}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Changes are saved to the app's internal storage. The agent reads these files at startup.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    GlassTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = "# Enter markdown content...",
                        singleLine = false,
                        maxLines = 40,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GlassButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val file = File(context.filesDir, fileType.fileName)
                                file.parentFile?.mkdirs()
                                file.writeText(content)
                            }
                            originalContent = content
                            Toast.makeText(context, "${fileType.fileName} saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = content != originalContent,
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(end = 8.dp))
                    Text("Save Changes", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
