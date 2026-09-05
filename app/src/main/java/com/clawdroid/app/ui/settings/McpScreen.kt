package com.clawdroid.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.service.GoogleAuthManager
import com.clawdroid.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Google Sign-In config & launcher
    var isGoogleConnected by remember { mutableStateOf(GoogleAuthManager.isGoogleConnected) }
    var googleEmail by remember { mutableStateOf(AppConfigManager.googleAccountEmail) }

    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(AppConfigManager.googleClientId)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/gmail.modify"),
                Scope("https://www.googleapis.com/auth/calendar")
            )
            .build()
    }
    
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val authCode = account?.serverAuthCode
            if (authCode != null) {
                googleEmail = account.email ?: ""
                AppConfigManager.googleAccountEmail = googleEmail
                scope.launch {
                    val success = GoogleAuthManager.exchangeAuthCode(authCode)
                    if (success) {
                        isGoogleConnected = true
                        Toast.makeText(context, "Google connected successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "OAuth exchange failed. Check client secret.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(context, "OAuth failed: No server authorization code received.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Load active servers configuration
    var mcpConfigStr by remember {
        mutableStateOf(
            AppConfigManager.mcpServersConfig.takeIf { it.isNotBlank() } ?: getDefaultConfig()
        )
    }

    val serversList = remember(mcpConfigStr) {
        val root = runCatching { JSONObject(mcpConfigStr) }.getOrDefault(JSONObject())
        val serversJson = root.optJSONObject("mcpServers") ?: JSONObject()
        val list = mutableListOf<McpServerItem>()
        val keys = serversJson.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val sObj = serversJson.getJSONObject(name)
            list.add(
                McpServerItem(
                    name = name,
                    enabled = sObj.optBoolean("enabled", true),
                    command = sObj.optString("command", ""),
                    args = sObj.optJSONArray("args")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    env = sObj.optJSONObject("env")?.let { envObj ->
                        val map = mutableMapOf<String, String>()
                        val ek = envObj.keys()
                        while (ek.hasNext()) {
                            val k = ek.next()
                            map[k] = envObj.getString(k)
                        }
                        map
                    } ?: emptyMap()
                )
            )
        }
        list
    }

    var activeLogsServer by remember { mutableStateOf<String?>(null) }
    var activeConfigDialogServer by remember { mutableStateOf<McpServerItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MCP Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = SoftWhite,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = SoftWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBlack,
                    titleContentColor = SoftWhite
                )
            )
        },
        containerColor = DeepBlack,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Google Account Connection Card ──
                item {
                    GoogleAccountCard(
                        isConnected = isGoogleConnected,
                        email = googleEmail,
                        onConnect = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                signInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        onDisconnect = {
                            GoogleAuthManager.disconnect()
                            googleSignInClient.signOut()
                            isGoogleConnected = false
                            googleEmail = ""
                            Toast.makeText(context, "Google Account Disconnected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // ── Virtual/Native Tools Title ──
                item {
                    Text(
                        text = "Virtual Google Tools",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MutedGray,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Virtual tools list (Always running/active if Google is connected)
                item {
                    VirtualToolItem(
                        name = "Gmail API Tools",
                        desc = "gmail_list_messages, gmail_get_message, gmail_send_message, gmail_create_draft",
                        icon = Icons.Rounded.Email,
                        active = isGoogleConnected
                    )
                }

                item {
                    VirtualToolItem(
                        name = "Google Calendar API",
                        desc = "calendar_list_events, calendar_create_event",
                        icon = Icons.Rounded.CalendarMonth,
                        active = isGoogleConnected
                    )
                }

                // ── Subprocess Servers Title ──
                item {
                    Text(
                        text = "Local Sandboxed Servers",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MutedGray,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                // MCP server lists
                items(serversList.size) { index ->
                    val server = serversList[index]
                    McpServerCard(
                        server = server,
                        onToggle = { isEnabled ->
                            val updatedConfig = updateServerConfig(mcpConfigStr, server.name, isEnabled)
                            AppConfigManager.mcpServersConfig = updatedConfig
                            mcpConfigStr = updatedConfig
                        },
                        onEdit = {
                            activeConfigDialogServer = server
                        },
                        onViewLogs = {
                            activeLogsServer = server.name
                        }
                    )
                }
            }

            // Config modification dialog
            activeConfigDialogServer?.let { server ->
                McpConfigDialog(
                    server = server,
                    onDismiss = { activeConfigDialogServer = null },
                    onSave = { updatedServer ->
                        val updatedConfig = saveServerConfigDetails(mcpConfigStr, updatedServer)
                        AppConfigManager.mcpServersConfig = updatedConfig
                        mcpConfigStr = updatedConfig
                        activeConfigDialogServer = null
                        Toast.makeText(context, "${server.name} saved.", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Logs bottom sheet view
            activeLogsServer?.let { serverName ->
                McpLogsBottomSheet(
                    serverName = serverName,
                    onDismiss = { activeLogsServer = null }
                )
            }
        }
    }
}

@Composable
private fun GoogleAccountCard(
    isConnected: Boolean,
    email: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.95f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderDim, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.DarkGray,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = "Google Client",
                        tint = if (isConnected) Color(0xFF4CAF50) else MutedGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Google Account Connection",
                        color = SoftWhite,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) "Connected: $email" else "Tap below to connect your email & calendar",
                        color = if (isConnected) Color(0xFF4CAF50) else MutedGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassFillMedium)
                .clickable { if (isConnected) onDisconnect() else onConnect() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                    contentDescription = null,
                    tint = EmberOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "Disconnect Google Account" else "Connect Google Account",
                    color = EmberOrange,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun VirtualToolItem(
    name: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardDark.copy(alpha = 0.85f))
            .border(1.dp, GlassBorderDim, shape)
            .padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color(0xFF4CAF50) else MutedGray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = SoftWhite,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = desc,
                color = MutedGray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        Text(
            text = if (active) "Active" else "Disabled",
            color = if (active) Color(0xFF4CAF50) else MutedGray,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServerItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onViewLogs: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.95f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderDim, shape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (server.enabled) EmberOrange.copy(alpha = 0.15f) else Color.DarkGray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (server.name) {
                                "filesystem" -> Icons.Rounded.Folder
                                "github" -> Icons.Rounded.Code
                                else -> Icons.Rounded.Terminal
                            },
                            contentDescription = null,
                            tint = if (server.enabled) EmberOrange else MutedGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = server.name.replaceFirstChar { it.uppercaseChar() },
                            color = SoftWhite,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = server.command,
                            color = MutedGray,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
                
                Switch(
                    checked = server.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmberOrange,
                        checkedTrackColor = EmberOrange.copy(alpha = 0.4f),
                        uncheckedThumbColor = MutedGray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // EDIT action button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassFill)
                        .clickable { onEdit() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = EmberOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit Config",
                        color = EmberOrange,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // VIEW LOGS action button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassFill)
                        .clickable { onViewLogs() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notes,
                        contentDescription = "Logs",
                        tint = SoftWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View Logs",
                        color = SoftWhite,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun McpConfigDialog(
    server: McpServerItem,
    onDismiss: () -> Unit,
    onSave: (McpServerItem) -> Unit
) {
    var cmd by remember { mutableStateOf(server.command) }
    var argsStr by remember { mutableStateOf(server.args.joinToString(" ")) }
    
    // Flatten environment variables
    var token by remember {
        mutableStateOf(
            if (server.name == "github") server.env.getOrDefault("GITHUB_TOKEN", "") else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configure ${server.name.replaceFirstChar { it.uppercaseChar() }}",
                color = SoftWhite,
                fontWeight = FontWeight.Bold
            )
        },
        containerColor = CardDark,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    label = { Text("Command") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmberOrange,
                        unfocusedBorderColor = GlassBorderDim,
                        focusedLabelColor = EmberOrange,
                        unfocusedLabelColor = MutedGray,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = argsStr,
                    onValueChange = { argsStr = it },
                    label = { Text("Arguments") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmberOrange,
                        unfocusedBorderColor = GlassBorderDim,
                        focusedLabelColor = EmberOrange,
                        unfocusedLabelColor = MutedGray,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (server.name == "github") {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("GitHub Token (PAT)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmberOrange,
                            unfocusedBorderColor = GlassBorderDim,
                            focusedLabelColor = EmberOrange,
                            unfocusedLabelColor = MutedGray,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val arguments = argsStr.split(" ").filter { it.isNotBlank() }
                    val environment = if (server.name == "github") mapOf("GITHUB_TOKEN" to token) else server.env
                    onSave(server.copy(command = cmd, args = arguments, env = environment))
                }
            ) {
                Text("Save", color = EmberOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedGray)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpLogsBottomSheet(
    serverName: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GlassBorderDim) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Logs: $serverName",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, GlassBorderDim, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text(
                            text = "[system] Starting process listener...",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                    item {
                        Text(
                            text = "[system] Handshake complete. protocolVersion=2024-11-05",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                    item {
                        Text(
                            text = "[system] Ready to receive calls.",
                            fontFamily = FontFamily.Monospace,
                            color = MutedGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getDefaultConfig(): String {
    return JSONObject()
        .put("mcpServers", JSONObject()
            .put("filesystem", JSONObject()
                .put("enabled", true)
                .put("command", "npx")
                .put("args", JSONArray().put("-y").put("@anthropic/mcp-filesystem-server").put("/data/data/com.clawdroid.app/files/home/projects"))
            )
            .put("github", JSONObject()
                .put("enabled", false)
                .put("command", "npx")
                .put("args", JSONArray().put("-y").put("@modelcontextprotocol/server-github"))
                .put("env", JSONObject().put("GITHUB_TOKEN", ""))
            )
            .put("fetch", JSONObject()
                .put("enabled", true)
                .put("command", "npx")
                .put("args", JSONArray().put("-y").put("@modelcontextprotocol/server-fetch"))
            )
        ).toString(2)
}

private fun updateServerConfig(configJson: String, name: String, enabled: Boolean): String {
    val root = runCatching { JSONObject(configJson) }.getOrDefault(JSONObject())
    val mcp = root.optJSONObject("mcpServers") ?: return configJson
    val server = mcp.optJSONObject(name) ?: return configJson
    server.put("enabled", enabled)
    return root.toString(2)
}

private fun saveServerConfigDetails(configJson: String, updatedServer: McpServerItem): String {
    val root = runCatching { JSONObject(configJson) }.getOrDefault(JSONObject())
    val mcp = root.optJSONObject("mcpServers") ?: return configJson
    val server = mcp.optJSONObject(updatedServer.name) ?: return configJson
    
    server.put("command", updatedServer.command)
    
    val argsArr = JSONArray()
    updatedServer.args.forEach { argsArr.put(it) }
    server.put("args", argsArr)

    val envObj = JSONObject()
    updatedServer.env.forEach { (k, v) -> envObj.put(k, v) }
    server.put("env", envObj)

    return root.toString(2)
}

data class McpServerItem(
    val name: String,
    val enabled: Boolean,
    val command: String,
    val args: List<String>,
    val env: Map<String, String>
)
