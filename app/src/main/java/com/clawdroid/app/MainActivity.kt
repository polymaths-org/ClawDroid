package com.clawdroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clawdroid.app.core.bootstrap.BootstrapDiagnostics
import com.clawdroid.app.core.bootstrap.BootstrapDiagnosticsRunner
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.core.bootstrap.BootstrapProgress
import com.clawdroid.app.core.bootstrap.BootstrapResult
import com.clawdroid.app.core.engine.AgentSmokeResult
import com.clawdroid.app.core.engine.AgentSmokeTester
import com.clawdroid.app.data.api.ModelTestResult
import com.clawdroid.app.data.api.OpenRouterClient
import com.clawdroid.app.ui.theme.ClawDroidTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClawDroidTheme {
                ClawDroidApp()
            }
        }
    }
}

private sealed interface BootstrapUiState {
    data object Loading : BootstrapUiState
    data class Ready(val diagnostics: BootstrapDiagnostics) : BootstrapUiState
    data class Running(val progress: BootstrapProgress) : BootstrapUiState
    data class Complete(val result: BootstrapResult) : BootstrapUiState
    data class Error(val message: String) : BootstrapUiState
}

private sealed interface ModelUiState {
    data object Idle : ModelUiState
    data object Running : ModelUiState
    data class Complete(val result: ModelTestResult) : ModelUiState
    data class Error(val message: String) : ModelUiState
}

private sealed interface AgentUiState {
    data object Idle : AgentUiState
    data object Running : AgentUiState
    data class Complete(val result: AgentSmokeResult) : AgentUiState
    data class Error(val message: String) : AgentUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClawDroidApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bootstrapState by remember { mutableStateOf<BootstrapUiState>(BootstrapUiState.Loading) }
    var modelState by remember { mutableStateOf<ModelUiState>(ModelUiState.Idle) }
    var agentState by remember { mutableStateOf<AgentUiState>(AgentUiState.Idle) }

    LaunchedEffect(Unit) {
        bootstrapState = runCatching {
            BootstrapDiagnosticsRunner.run(context.applicationContext)
        }.fold(
            onSuccess = { BootstrapUiState.Ready(it) },
            onFailure = { BootstrapUiState.Error(it.message ?: "Unknown bootstrap error") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "ClawDroid") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Agent workspace",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Phase 1 scaffold is alive on device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Next checkpoint",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Prove the Linux runtime and model endpoint before building the full agent loop.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    BootstrapStatusCard(state = bootstrapState)
                    Spacer(modifier = Modifier.height(14.dp))
                    ModelStatusCard(state = modelState)
                    Spacer(modifier = Modifier.height(14.dp))
                    AgentStatusCard(state = agentState)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                bootstrapState = BootstrapUiState.Running(
                                    BootstrapProgress("Starting", "Preparing bootstrap")
                                )
                                bootstrapState = runCatching {
                                    BootstrapManager.ensureBootstrapped(context.applicationContext) { progress ->
                                        bootstrapState = BootstrapUiState.Running(progress)
                                    }
                                }.fold(
                                    onSuccess = { BootstrapUiState.Complete(it) },
                                    onFailure = { BootstrapUiState.Error(it.message ?: "Bootstrap failed") },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Run bootstrap")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                modelState = ModelUiState.Running
                                modelState = runCatching {
                                    OpenRouterClient.runStreamingSmokeTest()
                                }.fold(
                                    onSuccess = { ModelUiState.Complete(it) },
                                    onFailure = { ModelUiState.Error(it.message ?: "Model test failed") },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Test model")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                agentState = AgentUiState.Running
                                agentState = runCatching {
                                    AgentSmokeTester.run(context.applicationContext)
                                }.fold(
                                    onSuccess = { AgentUiState.Complete(it) },
                                    onFailure = { AgentUiState.Error(it.message ?: "Agent smoke test failed") },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Test tool call")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BootstrapStatusCard(state: BootstrapUiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Runtime check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            when (state) {
                BootstrapUiState.Loading -> LoadingRow("Creating sandbox directories")

                is BootstrapUiState.Error -> ErrorText(state.message)

                is BootstrapUiState.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Column {
                        Text(
                            text = state.progress.stage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.progress.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is BootstrapUiState.Ready -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip("usr")
                        StatusChip("home")
                        StatusChip("projects")
                        StatusChip(".memory")
                        StatusChip("tmp")
                    }
                    Text(
                        text = state.diagnostics.commandOutput,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is BootstrapUiState.Complete -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip("bash")
                        StatusChip("script")
                        StatusChip("apt")
                    }
                    Text(
                        text = state.result.bashOutput,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(state: AgentUiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tool check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            when (state) {
                AgentUiState.Idle -> Text(
                    text = "Tool-call smoke test is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AgentUiState.Running -> LoadingRow("Asking model to call execute_command")

                is AgentUiState.Error -> ErrorText(state.message)

                is AgentUiState.Complete -> {
                    StatusChip(state.result.toolCall.name)
                    Text(
                        text = state.result.commandOutput,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.result.finalResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(state: ModelUiState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Model check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            when (state) {
                ModelUiState.Idle -> Text(
                    text = "OpenRouter smoke test is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ModelUiState.Running -> LoadingRow("Calling OpenRouter")

                is ModelUiState.Error -> ErrorText(state.message)

                is ModelUiState.Complete -> {
                    StatusChip(state.result.model)
                    Text(
                        text = state.result.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = text)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun StatusChip(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ClawDroidAppPreview() {
    ClawDroidTheme {
        ClawDroidApp()
    }
}
