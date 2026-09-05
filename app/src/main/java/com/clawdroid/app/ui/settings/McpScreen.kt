package com.clawdroid.app.ui.settings

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.clawdroid.app.R
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
    var googleConnectorEnabled by remember { mutableStateOf(AppConfigManager.googleConnectorEnabled) }
    var googleGmailEnabled by remember { mutableStateOf(AppConfigManager.googleGmailEnabled) }
    var googleCalendarEnabled by remember { mutableStateOf(AppConfigManager.googleCalendarEnabled) }

    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(AppConfigManager.googleClientId, true)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/gmail.modify"),
                Scope("https://www.googleapis.com/auth/calendar"),
                Scope("https://www.googleapis.com/auth/drive.file"),
                Scope("https://www.googleapis.com/auth/documents")
            )
            .build()
    }
    
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    // GitHub Connector State
    var isGithubConnected by remember { mutableStateOf(com.clawdroid.app.core.service.GithubAuthManager.isConnected) }
    var githubUsername by remember { mutableStateOf("") }
    var githubConnectorEnabled by remember { mutableStateOf(AppConfigManager.githubConnectorEnabled) }

    LaunchedEffect(isGithubConnected) {
        if (isGithubConnected) {
            githubUsername = com.clawdroid.app.core.service.GithubAuthManager.fetchUsername() ?: "Connected"
        }
    }

    // Notion Connector State
    var isNotionConnected by remember { mutableStateOf(com.clawdroid.app.core.service.NotionAuthManager.isConnected) }
    var notionWorkspace by remember { mutableStateOf("") }
    var notionConnectorEnabled by remember { mutableStateOf(AppConfigManager.notionConnectorEnabled) }

    LaunchedEffect(isNotionConnected) {
        if (isNotionConnected) {
            notionWorkspace = com.clawdroid.app.core.service.NotionAuthManager.fetchWorkspaceName() ?: "Connected"
        }
    }

    // Spotify Connector State
    var isSpotifyConnected by remember { mutableStateOf(com.clawdroid.app.core.service.SpotifyAuthManager.isConnected) }
    var spotifyUser by remember { mutableStateOf("") }
    var spotifyConnectorEnabled by remember { mutableStateOf(AppConfigManager.spotifyConnectorEnabled) }

    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected) {
            spotifyUser = com.clawdroid.app.core.service.SpotifyAuthManager.fetchDisplayName() ?: "Connected"
        }
    }

    // Reactive Lifecycle Observer to refresh connection states when user returns to foreground from OAuth browser
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGoogleConnected = GoogleAuthManager.isGoogleConnected
                googleEmail = AppConfigManager.googleAccountEmail
                isGithubConnected = com.clawdroid.app.core.service.GithubAuthManager.isConnected
                isNotionConnected = com.clawdroid.app.core.service.NotionAuthManager.isConnected
                isSpotifyConnected = com.clawdroid.app.core.service.SpotifyAuthManager.isConnected
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            isGoogleConnected = GoogleAuthManager.isGoogleConnected
            googleEmail = AppConfigManager.googleAccountEmail
            isGithubConnected = com.clawdroid.app.core.service.GithubAuthManager.isConnected
            isNotionConnected = com.clawdroid.app.core.service.NotionAuthManager.isConnected
            isSpotifyConnected = com.clawdroid.app.core.service.SpotifyAuthManager.isConnected
        }
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
                        googleConnectorEnabled = true
                        AppConfigManager.googleConnectorEnabled = true
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
                // ── Connectors Title ──
                item {
                    Text(
                        text = "Connectors",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MutedGray,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Google Connector Card
                item {
                    GoogleConnectorCard(
                        isConnected = isGoogleConnected,
                        email = googleEmail,
                        connectorEnabled = googleConnectorEnabled,
                        gmailEnabled = googleGmailEnabled,
                        calendarEnabled = googleCalendarEnabled,
                        onConnectorToggle = { enabled ->
                            AppConfigManager.googleConnectorEnabled = enabled
                            googleConnectorEnabled = enabled
                        },
                        onGmailToggle = { enabled ->
                            AppConfigManager.googleGmailEnabled = enabled
                            googleGmailEnabled = enabled
                        },
                        onCalendarToggle = { enabled ->
                            AppConfigManager.googleCalendarEnabled = enabled
                            googleCalendarEnabled = enabled
                        },
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

                // GitHub Connector Card
                item {
                    val githubClientId = com.clawdroid.app.BuildConfig.GITHUB_OAUTH_CLIENT_ID
                    GithubConnectorCard(
                        isConnected = isGithubConnected,
                        username = githubUsername,
                        connectorEnabled = githubConnectorEnabled,
                        onConnectorToggle = { enabled ->
                            AppConfigManager.githubConnectorEnabled = enabled
                            githubConnectorEnabled = enabled
                        },
                        onConnect = {
                            val authUrl = "https://github.com/login/oauth/authorize?client_id=$githubClientId&redirect_uri=clawdroid://github-auth&scope=repo%20read:user"
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                            context.startActivity(browserIntent)
                        },
                        onDisconnect = {
                            com.clawdroid.app.core.service.GithubAuthManager.disconnect()
                            isGithubConnected = false
                            githubUsername = ""
                            Toast.makeText(context, "GitHub Account Disconnected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Notion Connector Card
                item {
                    val notionClientId = com.clawdroid.app.BuildConfig.NOTION_OAUTH_CLIENT_ID
                    NotionConnectorCard(
                        isConnected = isNotionConnected,
                        workspace = notionWorkspace,
                        connectorEnabled = notionConnectorEnabled,
                        onConnectorToggle = { enabled ->
                            AppConfigManager.notionConnectorEnabled = enabled
                            notionConnectorEnabled = enabled
                        },
                        onConnect = {
                            val authUrl = "https://api.notion.com/v1/oauth/authorize?client_id=$notionClientId&response_type=code&owner=user&redirect_uri=clawdroid://notion-auth"
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                            context.startActivity(browserIntent)
                        },
                        onDisconnect = {
                            com.clawdroid.app.core.service.NotionAuthManager.disconnect()
                            isNotionConnected = false
                            notionWorkspace = ""
                            Toast.makeText(context, "Notion Account Disconnected", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Spotify Connector Card
                item {
                    val spotifyClientId = com.clawdroid.app.BuildConfig.SPOTIFY_OAUTH_CLIENT_ID
                    SpotifyConnectorCard(
                        isConnected = isSpotifyConnected,
                        username = spotifyUser,
                        connectorEnabled = spotifyConnectorEnabled,
                        onConnectorToggle = { enabled ->
                            AppConfigManager.spotifyConnectorEnabled = enabled
                            spotifyConnectorEnabled = enabled
                        },
                        onConnect = {
                            val authUrl = "https://accounts.spotify.com/authorize?client_id=$spotifyClientId&response_type=code&redirect_uri=clawdroid://spotify-auth&scope=user-read-currently-playing%20playlist-read-private%20playlist-modify-private"
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                            context.startActivity(browserIntent)
                        },
                        onDisconnect = {
                            com.clawdroid.app.core.service.SpotifyAuthManager.disconnect()
                            isSpotifyConnected = false
                            spotifyUser = ""
                            Toast.makeText(context, "Spotify Account Disconnected", Toast.LENGTH_SHORT).show()
                        }
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
private fun GoogleConnectorCard(
    isConnected: Boolean,
    email: String,
    connectorEnabled: Boolean,
    gmailEnabled: Boolean,
    calendarEnabled: Boolean,
    onConnectorToggle: (Boolean) -> Unit,
    onGmailToggle: (Boolean) -> Unit,
    onCalendarToggle: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon with brand colors
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected && connectorEnabled) Color(0xFF4285F4).copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(22.dp)
                            .then(if (isConnected && connectorEnabled) Modifier else Modifier.alpha(0.4f))
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google",
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) email else "Connect Gmail & Calendar",
                        color = if (isConnected) MutedGray else Color(0xFF4285F4),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isConnected) {
                    // Entire connector enable/disable switch
                    Switch(
                        checked = connectorEnabled,
                        onCheckedChange = onConnectorToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4285F4),
                            checkedTrackColor = Color(0xFF4285F4).copy(alpha = 0.4f),
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                } else {
                    // Small "Disconnected" indicator badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.1f))
                            .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Offline",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isConnected) {
                // Prompt to sign in
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // If connected, show child options (Gmail & Calendar switches) + Disconnect button
                if (connectorEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Gmail Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Email,
                                contentDescription = null,
                                tint = if (gmailEnabled) Color(0xFFEA4335) else MutedGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Gmail Access",
                                    color = SoftWhite,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Allow reading/writing email & drafts",
                                    color = MutedGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = gmailEnabled,
                            onCheckedChange = onGmailToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFEA4335),
                                checkedTrackColor = Color(0xFFEA4335).copy(alpha = 0.4f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Calendar Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = if (calendarEnabled) Color(0xFF34A853) else MutedGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Calendar Access",
                                    color = SoftWhite,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Allow reading/updating primary calendar",
                                    color = MutedGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = calendarEnabled,
                            onCheckedChange = onCalendarToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF34A853),
                                checkedTrackColor = Color(0xFF34A853).copy(alpha = 0.4f),
                                uncheckedThumbColor = MutedGray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Disconnect Button
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect Account",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
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

@Composable
private fun GithubConnectorCard(
    isConnected: Boolean,
    username: String,
    connectorEnabled: Boolean,
    onConnectorToggle: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected && connectorEnabled) Color(0xFF24292E).copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = "GitHub Logo",
                        tint = if (isConnected && connectorEnabled) SoftWhite else MutedGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub",
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) (if (username.isBlank() || username == "Connected") "Connected" else "@$username") else "Connect repositories & issues",
                        color = if (isConnected) MutedGray else Color(0xFF808080),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isConnected) {
                    Switch(
                        checked = connectorEnabled,
                        onCheckedChange = onConnectorToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SoftWhite,
                            checkedTrackColor = SoftWhite.copy(alpha = 0.4f),
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.1f))
                            .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Offline",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with GitHub",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect Account",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NotionConnectorCard(
    isConnected: Boolean,
    workspace: String,
    connectorEnabled: Boolean,
    onConnectorToggle: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected && connectorEnabled) Color.Black.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = "Notion Logo",
                        tint = if (isConnected && connectorEnabled) SoftWhite else MutedGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notion",
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) workspace else "Connect docs & databases",
                        color = if (isConnected) MutedGray else Color(0xFF808080),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isConnected) {
                    Switch(
                        checked = connectorEnabled,
                        onCheckedChange = onConnectorToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SoftWhite,
                            checkedTrackColor = SoftWhite.copy(alpha = 0.4f),
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.1f))
                            .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Offline",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Notion",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect Account",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotifyConnectorCard(
    isConnected: Boolean,
    username: String,
    connectorEnabled: Boolean,
    onConnectorToggle: (Boolean) -> Unit,
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isConnected && connectorEnabled) Color(0xFF1DB954).copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Spotify Logo",
                        tint = if (isConnected && connectorEnabled) Color(0xFF1DB954) else MutedGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spotify",
                        color = SoftWhite,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) username else "Control playback & search music",
                        color = if (isConnected) MutedGray else Color(0xFF1DB954),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isConnected) {
                    Switch(
                        checked = connectorEnabled,
                        onCheckedChange = onConnectorToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1DB954),
                            checkedTrackColor = Color(0xFF1DB954).copy(alpha = 0.4f),
                            uncheckedThumbColor = MutedGray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.1f))
                            .border(1.dp, Color.Red.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Offline",
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Spotify",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GlassBorderDim, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disconnect Account",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
