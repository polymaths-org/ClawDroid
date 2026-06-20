package com.clawdroid.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.DisposableEffect
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
import com.clawdroid.app.core.terminal.ProcessManagerProvider
import com.clawdroid.app.core.terminal.ProcessState
import com.clawdroid.app.ui.components.ClawSkinBackground
import com.clawdroid.app.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lines = remember { mutableStateListOf<String>() }
    val pm = remember { ProcessManagerProvider.get(context) }

    var processId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(ProcessState.RUNNING) }
    var cwd by remember { mutableStateOf("~") }
    var sessionKey by remember { mutableStateOf(0) }
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableStateOf(-1) }

    fun sendRaw(raw: String) {
        processId?.let { id -> scope.launch { runCatching { pm.sendInput(id, raw) } } }
    }

    fun submitCommand(command: String = input) {
        val text = command.trim()
        if (text.isBlank()) return
        if (processId == null || state in terminalStates) {
            lines.add("Shell not running. Press ↻ to restart.")
            return
        }
        val commandToSend = if (text.equals("clear", ignoreCase = true)) "clear" else text
        input = ""
        history.add(commandToSend)
        historyIndex = history.size
        if (commandToSend == "clear") {
            lines.clear()
            processId?.let { id -> scope.launch { runCatching { pm.clearProcessOutput(id) } } }
        }
        sendRaw("$commandToSend[ENTER]")
    }

    fun appendOutput(text: String) {
        val next = text.lines()
        if (next.isNotEmpty()) {
            lines.clear()
            lines.addAll(next.takeLast(500))
        }
    }

    LaunchedEffect(sessionKey) {
        lines.add("Starting ClawDroid Linux shell...")
        val start = runCatching {
            pm.startProcess(
                command = terminalStartupCommand(),
                timeout = 3.hours,
                usePty = true,
            )
        }
        if (start.isFailure) {
            lines.add("Unable to start shell: ${start.exceptionOrNull()?.message ?: "unknown error"}")
            state = ProcessState.FAILED
            return@LaunchedEffect
        }
        val result = start.getOrThrow()
        processId = result.processId
        appendOutput(pm.getProcessOutput(result.processId).ifBlank { result.initialOutput.ifBlank { "Shell ready." } })

        while (isActive) {
            val id = processId ?: break
            val status = runCatching { pm.checkProcess(id) }.getOrNull() ?: break
            state = status.state
            cwd = status.cwd
            appendOutput(pm.getProcessOutput(id))
            delay(600)
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            processId?.let { id -> scope.launch { runCatching { pm.killProcess(id) } } }
        }
    }

    ClawSkinBackground {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(cwd, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            processId?.let { id -> scope.launch { runCatching { pm.killProcess(id) } } }
                            processId = null
                            state = ProcessState.RUNNING
                            cwd = "~"
                            lines.clear()
                            sessionKey++
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Restart shell")
                    }
                    IconButton(
                        onClick = {
                            lines.clear()
                            processId?.let { id -> scope.launch { runCatching { pm.clearProcessOutput(id) } } }
                        },
                    ) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = "Clear")
                    }
                    IconButton(
                        onClick = {
                            processId?.let { id ->
                                scope.launch {
                                    runCatching { pm.sendInput(id, "[CTRL+C]") }
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Interrupt")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.62f)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StatusStrip(state = state)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lines.size) { index ->
                    Text(
                        text = lines[index],
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }

            QuickCommandRow(
                onCommand = { submitCommand(it) },
                onCtrlC = { sendRaw("[CTRL+C]") },
                onCtrlD = { sendRaw("[CTRL+D]") },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(Dimens.sm))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    placeholder = { Text("Run a Linux command...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { submitCommand() },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(modifier = Modifier.width(Dimens.sm))
                IconButton(
                    onClick = {
                        if (history.isNotEmpty()) {
                            historyIndex = when {
                                historyIndex <= 0 -> 0
                                historyIndex > history.lastIndex -> history.lastIndex
                                else -> historyIndex - 1
                            }
                            input = history[historyIndex]
                        }
                    },
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous command", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        if (history.isNotEmpty()) {
                            historyIndex = (historyIndex + 1).coerceAtMost(history.size)
                            input = if (historyIndex in history.indices) history[historyIndex] else ""
                        }
                    },
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next command", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = { submitCommand() },
                ) {
                    Icon(Icons.Rounded.KeyboardReturn, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }
}

@Composable
private fun QuickCommandRow(
    onCommand: (String) -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("pwd", "ls -la", "cd ~", "clear", "whoami", "python --version").forEach { command ->
            TerminalChip(label = command, onClick = { onCommand(command) })
        }
        TerminalChip(label = "Ctrl+C", danger = true, onClick = onCtrlC)
        TerminalChip(label = "Ctrl+D", danger = false, onClick = onCtrlD)
    }
}

@Composable
private fun TerminalChip(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
            )
            .border(
                1.dp,
                if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.36f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StatusStrip(state: ProcessState) {
    val color = when (state) {
        ProcessState.RUNNING, ProcessState.WAITING_FOR_INPUT -> Color(0xFF81C784)
        ProcessState.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = when (state) {
                ProcessState.WAITING_FOR_INPUT -> "Waiting for input"
                ProcessState.RUNNING -> "Linux shell active"
                ProcessState.COMPLETED -> "Shell exited"
                ProcessState.KILLED -> "Shell stopped"
                ProcessState.TIMED_OUT -> "Shell timed out"
                ProcessState.FAILED -> "Shell failed"
            },
            color = Color(0xFFE7ECEF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun terminalStartupCommand(): String {
    val banner = CLAWDROID_TERMINAL_BANNER.replace("'", "'\"'\"'")
    return "printf '%s\\n' '$banner'\n" +
        "export PS1='clawdroid:\\w${'$'} '\n" +
        "clear() { printf '\\033[H\\033[2J\\033[3J'; }\n" +
        "export -f clear\n" +
        "exec \"${'$'}SHELL\" --noprofile --norc -i\n"
}

private val CLAWDROID_TERMINAL_BANNER = """
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                                                                                    
                                              =======+                                              
                                          ===============*                                          
                                        ===================+                                        
                                      +=====================*                                       
                                     =======================#*#                                     
                                     ========================**                                     
                        ======      ===@%====================***      ======                        
                      -=+  #*===    =@=:=@@=================-***    ==-**  +=-                      
                      =      **==   =@-:====================****   ==**      =                      
                      =       *==   =%=:=@=@===============-****   ==*       =                      
                        =     *==   =@=:=@=--==============*****   ==*     +                        
                             **==    @===@%*#%@======@%#*%@****    ==**                             
                            **==+     =@#@#::#@======@#::#@****    ===**                            
                           ***==       %@=@@@@@======@@@@@****      ==**#                           
                           **===        :-=@@=#======@******        ==-**                           
                           #**==        @==##=========*****@        ==**+                           
                            **====      %==============****@      -===**                            
                   ========  **#======:%=================***@=======#**  ========                   
                 ===*****-==== ***=-%@-====================-*+%@-=*** ====-*****===                 
                 =**     ***===========@====@==========@====@===========***     **=                 
                *=*        +***======+@====@============@====@+======***+        *=                 
                 =*           ******@-====@====******====@=====@******           *=                 
                   =+*=           @=====*@-===**@@@@**===-%*=====@           +**=                   
                           ==========-***@===**%    @**===@***-==========                           
                        ========-#*****@ @===*+@    @**===@ @*****#-========                        
                      ==-********+*%     @===**@    @**===@     @****##*+**-==                      
                     ==**                %-==**@    @**===                 **=+                     
                     ==*            +-=   @===**    **===%   --+            *==                     
                      =-*    =    -=       %==**@  @**==%      +=-    =    *-=                      
                        ====      ==       @==***  @**==@       ==     *====                        
                                  ==       -==**    **==-       ==                                  
                                  -==== +====**      **====+ ====-                                  
                                    ========*          *========                                    
                                                                                                    
                                                                                                    
                                                                                                    
Control the Sandbox with own expertise, scriptkiddies stay away!
""".trim('\n', '\r')

private val terminalStates = setOf(
    ProcessState.COMPLETED,
    ProcessState.FAILED,
    ProcessState.TIMED_OUT,
    ProcessState.KILLED,
)
