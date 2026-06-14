# ClawDroid — Implementation Plan

Build a native Android AI agent app with an embedded Linux terminal, streaming LLM integration, and a Material 3 UI with Codex-style collapsible activity steps.

> Reference: [AGENTS.md](file:///home/wraient/Documents/antigravity/elegant-hertz/AGENTS.md) — source of truth for all requirements.

---

## Phase 1 — Project Scaffolding + Bootstrap

**Goal:** Android project compiles, Termux bootstrap downloads and extracts, `bash` runs a command successfully.

### 1.1 Android Project Setup

#### [NEW] Project Root
Scaffold a new Android project via Android Studio template or `android` CLI:
- **Package:** `com.clawdroid.app`
- **Min SDK:** 26 (Android 8.0) — covers ~95% of devices
- **Target SDK:** 28 — required for W^X workaround (see below)
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Build:** Gradle with Kotlin DSL
- **`android:sharedUserId`:** `com.termux` — shares Linux UID with Termux, resolves hardcoded path issue

> [!CAUTION]
> **W^X (Write XOR Execute) Restriction:** Android 10+ enforces W^X — apps targeting SDK 29+ **cannot execute binaries** from their writable data directory. We target `targetSdkVersion = 28` for compatibility mode. This means:
> - ❌ **Cannot distribute on Google Play** (requires targetSdk 34+)
> - ✅ **F-Droid, GitHub Releases, website APK download** all work fine
> - ✅ Same constraint Termux has — they're not on Play Store either
> - The app is not harder to USE, just harder to discover. Users install APKs routinely.

> [!IMPORTANT]
> **sharedUserId = com.termux:** This shares the same Linux UID as the Termux app, allowing our app to use Termux's filesystem paths (which are hardcoded into all Termux-compiled binaries). If Termux is also installed, both apps can access each other's files — which is fine since the user wanted those packages anyway. If Termux is NOT installed, we bootstrap our own environment.

#### [NEW] `build.gradle.kts` (app-level)
Core dependencies:
```
- Jetpack Compose BOM (latest stable)
- Material 3
- Navigation Compose
- Hilt (DI)
- Room (DB)
- OkHttp (HTTP + SSE)
- Retrofit (non-streaming HTTP)
- Markwon (Markdown rendering)
- EncryptedSharedPreferences
- WorkManager
- Moshi / Kotlinx Serialization (JSON)
- Coroutines + Flow
- Coil (image loading)
```

#### [NEW] `AndroidManifest.xml`
Permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Components:
- `MainActivity` (single activity, Compose host)
- `AgentForegroundService` (keeps agent alive in background)
- `BootReceiver` (restart automations after reboot)
- Share Intent filter for receiving files/URLs

### 1.2 Termux Bootstrap Integration

#### [NEW] `core/bootstrap/BootstrapManager.kt`
Handles downloading and extracting the Termux bootstrap:
- Download URL: `https://github.com/termux/termux-packages/releases/download/<TAG>/bootstrap-aarch64.zip`
- TAG format: `bootstrap-YYYY.MM.DD-r1+apt.android-7`
- Extract to: `/data/data/com.clawdroid.app/files/usr/`
- Process `SYMLINKS.txt` for symlink reconstruction (ZIP doesn't preserve symlinks)
- Create `home/` directory structure
- Create `home/.memory/` for persistent memory
- Create `home/projects/` for project sandboxes
- Install `util-linux` package (provides `script` command for PTY)
- Verify extraction: run `bash --version`

**Key steps:**
1. Download `bootstrap-aarch64.zip` (~80MB, show progress in onboarding UI)
2. Extract using `ZipInputStream` into `$PREFIX` (`/data/data/com.clawdroid.app/files/usr/`)
3. Process `SYMLINKS.txt` — format is `target←link_path` (Unicode arrow delimiter):
   ```kotlin
   // Each line: "bash←bin/sh" means: create symlink bin/sh -> bash
   symlinkLines.forEach { line ->
       val (target, linkPath) = line.split("←")
       Os.symlink(target, "$prefix/$linkPath")
   }
   ```
4. Set executable permissions on all files in `usr/bin/` (`chmod 700`)
5. Write `etc/apt/sources.list` pointing to Termux repos:
   ```
   deb https://packages.termux.dev/apt/termux-main stable main
   ```
6. Run first-launch setup script:
   ```bash
   apt update && apt install -y util-linux  # provides 'script' for PTY
   ```
7. Verify: run `bash -c "echo ok"` via ProcessBuilder

> [!NOTE]
> **Hardcoded path resolved:** By using `sharedUserId = com.termux`, our app shares the same Linux UID as Termux. Termux packages have paths hardcoded to `/data/data/com.termux/files/usr` — with the shared UID, we can read/write that directory. If Termux is already installed, we reuse their environment. If not, we extract the bootstrap there ourselves.

#### [NEW] `core/bootstrap/EnvironmentSetup.kt`
Creates the consistent environment for all process execution:
```kotlin
fun buildEnvironment(context: Context): Map<String, String> {
    val filesDir = context.filesDir.absolutePath
    val prefix = "$filesDir/usr"
    return mapOf(
        "PREFIX" to prefix,
        "HOME" to "$filesDir/home",
        "PATH" to "$prefix/bin:$prefix/bin/applets:/system/bin",
        "LD_LIBRARY_PATH" to "$prefix/lib",
        "TMPDIR" to "$prefix/tmp",
        "LANG" to "en_US.UTF-8",
        "TERM" to "xterm-256color",
        "COLORTERM" to "truecolor",
        "SHELL" to "$prefix/bin/bash",
        "ANDROID_DATA" to "/data",
        "ANDROID_ROOT" to "/system"
    )
}
```

> [!IMPORTANT]
> Always clear the inherited Android environment (`env.clear()`) before setting these. The Android app's default environment variables can cause conflicts with the Linux binaries.

### 1.3 JNI PTY Layer

#### [NEW] `core/terminal/jni/pty.c`
Native C code (~200 lines) using `forkpty()` from `<pty.h>`:
- Creates a pseudo-terminal pair (master/slave)
- Forks a child process attached to the PTY
- Returns the master FD to Kotlin for reading/writing
- Handles `SIGCHLD` for process exit detection

#### [NEW] `core/terminal/JniPty.kt`
Kotlin JNI bridge:
```kotlin
object JniPty {
    init { System.loadLibrary("clawdroid-pty") }
    
    external fun createSubprocess(
        cmd: String, cwd: String, args: Array<String>,
        env: Array<String>, rows: Int, cols: Int
    ): IntArray  // returns [pid, masterFd]
    
    external fun waitFor(pid: Int): Int
    external fun close(fd: Int)
    external fun setWindowSize(fd: Int, rows: Int, cols: Int)
}
```

> [!NOTE]
> **Why JNI PTY instead of `script` command:** The `script` command requires `util-linux` which isn't in the bootstrap and depends on `apt install` working first (circular dependency). JNI PTY is compiled into our APK, has zero external dependencies, and is what every production Android terminal emulator uses (including Termux itself). ~2-3 hours of additional upfront work, but eliminates a fragile dependency chain entirely.

### 1.3 Shared Folder Setup

#### [NEW] `core/bootstrap/SharedFolderManager.kt`
- Create `/storage/emulated/0/Documents/ClawDroid/` on first launch
- Create subdirectories: `Inbox/`, `Output/`, `Projects/`, `Exports/`
- Symlink from sandbox's home to shared folder for easy agent access
- Request `MANAGE_EXTERNAL_STORAGE` permission via Settings intent

### Verification
- [ ] Project compiles and runs on Android 12+ device/emulator
- [ ] Bootstrap downloads with progress indicator
- [ ] `bash --version` executes and returns output
- [ ] `apt update` runs successfully
- [ ] Shared folder created and accessible in Files app

---

## Phase 2 — Agent Engine + API Client

**Goal:** Send a message, get a streamed response with tool calls, execute tools, loop.

### 2.1 Database Schema

#### [NEW] `data/db/ClawDroidDatabase.kt`
Room database with entities:

```kotlin
@Entity data class ProjectEntity(id, name, createdAt, updatedAt)
@Entity data class ConversationEntity(id, projectId?, title, createdAt, updatedAt, status, costUsd)
@Entity data class MessageEntity(id, conversationId, role, content, createdAt, tokenCount)
@Entity data class ToolCallEntity(id, messageId, toolName, arguments, result, status, durationMs)
@Entity data class AutomationEntity(id, projectId, name, prompt, cronExpression, enabled, lastRunAt)
@Entity data class SettingsEntity(key, value)
```

DAOs for each entity with Flow-based reactive queries.

### 2.2 API Client

#### [NEW] `data/api/LlmApiClient.kt`
OpenAI-compatible streaming client:
- Uses OkHttp for SSE streaming
- Configurable base URL, API key, model name
- Sends `POST /v1/chat/completions` with `stream: true`
- Parses SSE events line-by-line (`data: {...}`)
- Accumulates tool call fragments per index
- Emits `Flow<StreamEvent>` where:
  ```kotlin
  sealed class StreamEvent {
      data class TextDelta(val text: String) : StreamEvent()
      data class ToolCallComplete(val id: String, val name: String, val args: String) : StreamEvent()
      data class Error(val message: String, val code: Int?) : StreamEvent()
      data object Done : StreamEvent()
  }
  ```

#### [NEW] `data/api/MessageBuilder.kt`
Builds the messages array for API calls:
- System prompt (with personality preset)
- Compaction summary (if any)
- Recent messages
- Tool results (ensuring correct ordering — all tool results before any user message)
- Steering messages (injected after tool results, before the next LLM call)
- Token estimation (~4 chars/token) to check against context limit

#### [NEW] `data/api/ToolSchemaRegistry.kt`
Defines tool JSON schemas for function calling:
- `execute_command(command, cwd, timeout_seconds)`
- `start_process(command, cwd, timeout_seconds)`
- `check_process(process_id)`
- `send_input(process_id, input)`
- `kill_process(process_id)`
- `list_processes()`
- `read_file(path, start_line, end_line)`
- `write_file(path, content)`
- `edit_file(path, search, replace)`
- `list_directory(path)`
- `browse_web(url, action, selector)`
- `web_search(query)`
- `send_notification(title, body, actions)`

#### [NEW] `data/api/DefensiveJsonParser.kt`
Handles malformed JSON from LLM:
1. Standard parse attempt
2. Fix trailing commas, unescaped quotes
3. Extract JSON from surrounding text
4. Return error with schema for model self-correction

### 2.3 Agent Engine

#### [NEW] `core/engine/AgentEngine.kt`
The main agent loop (runs on `Dispatchers.IO`):
```
1. Build messages (system prompt + history + tool results)
2. Check steering queue → inject if present
3. Call LLM API (streaming)
4. On TEXT → emit to UI, auto-collapse activity group
5. On TOOL_CALLS → execute via ToolExecutor
   - Parallel execution for independent tools
   - Check STOP flag after each tool
6. Feed tool results back → loop to step 1
7. On STOP → kill all processes, cancel stream, return
```

#### [NEW] `core/engine/SteeringQueue.kt`
Thread-safe queue for user messages sent while agent is running:
- `offer(message: String)` — called from UI thread
- `drain(): List<String>` — called by engine before each LLM call
- Messages are injected as user messages after all tool results

#### [NEW] `core/engine/LoopDetector.kt`
Tracks recent tool calls to detect loops:
- Maintains a sliding window of last 10 tool calls
- If 3+ calls have same tool name + similar args → inject system message
- Hard cap at 10 identical attempts → force stop

#### [NEW] `core/engine/CostTracker.kt`
Tracks token usage and costs:
- Accumulates input/output tokens per conversation
- Estimates cost based on model pricing table
- Checks against per-project and global limits
- Emits `Flow<CostState>` for UI display

#### [NEW] `core/engine/CompactionManager.kt`
Handles context compaction:
- Estimates tokens before each API call
- At 80% of model's context limit → trigger compaction
- Uses the **compaction model** (user-configurable in Settings, defaults to the active model)
- Retains: key decisions, files modified, packages installed, errors, user preferences
- Stores compaction summary, archives old messages in Room
- Shows `── context compacted ──` indicator in UI

#### [NEW] `core/engine/ToolExecutor.kt`
Routes tool calls to their implementations:
- Validates tool name against registry
- Parses arguments (with defensive JSON)
- Dispatches to correct handler
- Captures result as string
- Returns `ToolResult(callId, content, isError)`

### 2.4 Rate Limiting & Retries

#### [NEW] `data/api/RetryClient.kt`
Wraps OkHttp with retry logic:
- 429 → respect `retry-after`, exponential backoff (1s, 2s, 4s, 8s), max 5 retries
- 400 (context too long) → trigger compaction, retry
- 401 → emit event to prompt user for new API key
- 500/503 → retry 3x with backoff
- Network error → check connectivity, retry with backoff

### Verification
- [ ] Send a text message → get a streamed response displayed in logcat
- [ ] Tool call is parsed correctly from stream
- [ ] Tool call is executed and result fed back
- [ ] Multi-turn tool loop completes (agent uses 2+ tools in sequence)
- [ ] Steering message is picked up between tool turns
- [ ] Stop flag immediately halts execution

---

## Phase 3 — Process Manager + Terminal Tools

**Goal:** Agent can run commands, manage processes, send interactive input, handle PTY.

### 3.1 Process Manager

#### [NEW] `core/terminal/ProcessManager.kt`
Central manager for all shell processes:
```kotlin
class ProcessManager {
    private val processes = ConcurrentHashMap<String, ManagedProcess>()
    
    suspend fun executeCommand(command: String, cwd: String, timeout: Duration): CommandResult
    fun startProcess(command: String, cwd: String, timeout: Duration): String // returns process ID
    fun checkProcess(processId: String): ProcessStatus
    fun sendInput(processId: String, input: String)
    fun killProcess(processId: String)
    fun listProcesses(): List<ProcessInfo>
    fun killAll() // for STOP button
}
```

#### [NEW] `core/terminal/ManagedProcess.kt`
Wraps a single process with JNI PTY support:
- Launches via `JniPty.createSubprocess()` — creates a real pseudo-terminal
- Reads master FD via coroutine on `Dispatchers.IO`
- Writes to master FD for stdin
- Maintains a ring buffer (last 500 lines)
- Tracks state: `RUNNING`, `COMPLETED`, `FAILED`, `TIMED_OUT`, `WAITING_FOR_INPUT`
- Monitors output for prompt patterns (Y/n, password:, >>>, etc.)
- Timeout enforcement via `withTimeoutOrNull`

#### [NEW] `core/terminal/OutputBuffer.kt`
Ring buffer for process output:
- Fixed capacity (500 lines)
- Thread-safe (mutex-protected)
- `getForLlm()`: returns first 5 lines + last 30 lines + omitted count
- `getForUi()`: returns all lines with ANSI codes preserved
- `getRecentLines(n)`: returns last N lines

#### [NEW] `core/terminal/InputTranslator.kt`
Translates human-readable tokens to escape sequences:
```kotlin
fun translate(input: String): ByteArray {
    return input
        .replace("[ENTER]", "\r")
        .replace("[TAB]", "\t")
        .replace("[UP]", "\u001b[A")
        .replace("[DOWN]", "\u001b[B")
        .replace("[LEFT]", "\u001b[D")
        .replace("[RIGHT]", "\u001b[C")
        .replace("[CTRL+C]", "\u0003")
        .replace("[CTRL+D]", "\u0004")
        .replace("[CTRL+Z]", "\u001a")
        .replace("[BACKSPACE]", "\u007f")
        .replace("[HOME]", "\u001b[H")
        .replace("[END]", "\u001b[F")
        .toByteArray()
}
```

#### [NEW] `core/terminal/AnsiStripper.kt`
Strips ANSI escape codes from output before feeding to LLM:
- Regex-based removal of color codes, cursor movement, etc.
- Preserves plain text content

### 3.2 Tool Implementations

#### [NEW] `core/tools/ExecuteCommandTool.kt`
- Runs command via `ProcessManager.executeCommand()`
- Default 30s timeout
- If timeout → auto-promote to background process, return process ID
- Returns: `{exitCode, stdout (truncated for LLM), stderr}`

#### [NEW] `core/tools/StartProcessTool.kt`
- Runs command via `ProcessManager.startProcess()`
- Default 5-minute timeout, configurable up to 3 hours
- Waits 2-3 seconds for initial output (catches immediate failures)
- Returns: `{processId, initialOutput}`

#### [NEW] `core/tools/CheckProcessTool.kt`
- Returns: `{processId, status, recentOutput, waitingForInput, prompt}`

#### [NEW] `core/tools/SendInputTool.kt`
- Translates input through `InputTranslator`
- Writes to process stdin
- Waits 1s for response
- Returns: `{sent, recentOutput}`

#### [NEW] `core/tools/KillProcessTool.kt`
- Kills process group (`kill -TERM -$PID`)
- Cleans up from ProcessManager

#### [NEW] `core/tools/ListProcessesTool.kt`
- Returns list of all tracked processes with status

### Verification
- [ ] `execute_command("ls -la")` returns file listing
- [ ] `execute_command("sleep 60")` times out and promotes to background
- [ ] `start_process("apt install -y curl")` runs in background, agent can check status
- [ ] `send_input(id, "y[ENTER]")` responds to a Y/n prompt
- [ ] `send_input(id, "[CTRL+C]")` interrupts a running process
- [ ] `kill_process(id)` kills process and all children
- [ ] Multiple processes run simultaneously without interference
- [ ] PTY (JNI-based) enables interactive prompts and ANSI output

---

## Phase 4 — Chat UI + Activity Steps

**Goal:** Beautiful Material 3 chat interface with Codex-style collapsible activity steps and streaming Markdown.

### 4.1 Theme & Design System

#### [NEW] `ui/theme/Theme.kt`
Material 3 theme with:
- Material You dynamic colors (Android 12+ only — check `Build.VERSION.SDK_INT >= 31`)
- Fallback palette for Android 8-11: muted teal primary, purple secondary
- Dark/light mode following system
- Custom typography: system default for UI, monospace (JetBrains Mono / Fira Code) for code/terminal

#### [NEW] `ui/theme/Type.kt`
Typography definitions with both sans-serif and monospace families.

#### [NEW] `ui/theme/Color.kt`
Fallback color palette for devices without dynamic color support.

### 4.2 Chat Screen

#### [NEW] `ui/chat/ChatScreen.kt`
Main conversation screen (primary screen of the app):
- `LazyColumn` for message list (reversed, newest at bottom)
- Messages: user bubbles, agent messages (Markdown-rendered), activity step groups
- Auto-scroll to bottom on new messages
- Pull-to-load-more for history (scrolling past compaction boundary loads archived messages)

#### [NEW] `ui/chat/ChatViewModel.kt`
ViewModel for chat screen:
- Holds `Flow<List<ChatItem>>` (messages + activity groups)
- Manages agent engine lifecycle (start, stop, steer)
- Handles file attachment
- Tracks agent state: `IDLE`, `THINKING`, `EXECUTING`, `STREAMING`

#### [NEW] `ui/chat/components/UserMessageBubble.kt`
User message with:
- Rounded bubble, accent color
- Timestamp
- Attached file chip (if any)

#### [NEW] `ui/chat/components/AgentMessageCard.kt`
Agent response with:
- Markwon-rendered Markdown (wrapped in AndroidView)
- Code blocks with syntax highlighting + copy button
- Streaming text with buffered Markdown parsing (buffer incomplete tokens to prevent flickering)

#### [NEW] `ui/chat/components/ActivityStepGroup.kt`
Two-level collapsible step group:
- **Level 1 (collapsed):** One-line summary: `▶ 📂 Explored 4 files · ⚙️ Ran 2 commands · 🌐 Visited 1 page  58s`
- **Level 2 (expanded group):** Shows individual steps, each collapsed
- **Level 3 (expanded step):** Full command + output in a scrollable container (max 200dp)
- Spring physics for expand/collapse animation
- Auto-expand while running, auto-collapse when done

#### [NEW] `ui/chat/components/ActivityStep.kt`
Individual step within a group:
- Icon by type: ⚙️ command, 📂 file, 🌐 web, 📝 edit, 📦 install
- One-line summary when collapsed
- Full output with ANSI color rendering when expanded
- Live-streaming output for currently running step
- Fixed max height (200dp) with internal scroll

#### [NEW] `ui/chat/components/InputBar.kt`
Chat input bar:
- **Idle:** Text field + 📎 attachment + ➤ send
- **Running:** Text field (for steering) + ⏹ Stop button
- Typing `/` opens command menu (Telegram-style)
- File attachment via Android file picker intent
- Haptic feedback on send/stop

#### [NEW] `ui/chat/components/CompactionIndicator.kt`
Subtle `── context compacted ──` divider in the message list.

### 4.3 Markdown Rendering

#### [NEW] `ui/markdown/MarkwonWrapper.kt`
Composable wrapper around Markwon:
- Uses `AndroidView` to host Markwon's `TextView`
- Plugins: `CorePlugin`, `HtmlPlugin`, `SyntaxHighlightPlugin` (Prism4j), `TablePlugin`
- Code blocks: dark background, monospace font, copy button overlay
- Links: tappable, open in WebView or external browser
- Streaming support: re-render on each text delta with buffered incomplete tokens

### Verification
- [ ] Chat screen renders with Material 3 theme and dynamic colors
- [ ] Agent response streams in with Markdown rendering (no flickering)
- [ ] Code blocks have syntax highlighting and copy button
- [ ] Activity steps collapse/expand with smooth animation
- [ ] Running step shows live-streaming output
- [ ] Completed steps auto-collapse
- [ ] Stop button immediately halts agent
- [ ] Steering message is delivered to agent between tool turns
- [ ] File attachment picks a file and copies to shared folder

---

## Phase 5 — File Tools + Shared Folder

**Goal:** Agent can read, write, edit files. User can share files to the app.

### 5.1 File Tool Implementations

#### [NEW] `core/tools/ReadFileTool.kt`
- Reads file content (with optional line range)
- Returns content as string (truncated if too large for LLM context)

#### [NEW] `core/tools/WriteFileTool.kt`
- Creates or overwrites file
- Creates parent directories if needed
- Returns confirmation with file path and size

#### [NEW] `core/tools/EditFileTool.kt`
- Search/replace within a file
- Validates search string exists
- Returns diff preview

#### [NEW] `core/tools/ListDirectoryTool.kt`
- Lists files with sizes, types, permissions
- Sorted by name
- Returns formatted listing

### 5.2 Share Intent Handler

#### [NEW] `ui/share/ShareReceiverActivity.kt`
Handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`:
- Copies received files to `Documents/ClawDroid/Inbox/`
- Opens the app with a new chat pre-filled: "I shared [filename] with you"
- Supports: images, documents, URLs, text

### Verification
- [ ] Agent can read, write, and edit files in sandbox
- [ ] Agent can read files from shared folder
- [ ] Sharing a file from another app opens ClawDroid with the file attached
- [ ] Agent output files appear in `Documents/ClawDroid/Output/`

---

## Phase 6 — Projects + Sidebar

**Goal:** Navigate between projects and chats. Sidebar with Quick Actions, Chats, Projects.

### 6.1 Sidebar / Navigation Drawer

#### [NEW] `ui/sidebar/SidebarDrawer.kt`
Material 3 modal navigation drawer:
- **Quick Actions:** Automations, Connected Services (disabled/post-MVP), Settings
- **Chats:** 4 most recent standalone chats + "Show more..."  
  `[+]` button on section header to start new chat
- **Projects:** 4 most recent projects + "Show more..."  
  `[+]` button on section header to create new project  
  Tap project → expand to show threads with `[+ New Thread]` at top
- All sections sorted by most recently used

#### [NEW] `ui/sidebar/SidebarViewModel.kt`
- Loads projects and chats from Room DB
- Handles create/delete/rename operations
- Tracks which project is expanded

### 6.2 Project Management

#### [NEW] `ui/project/CreateProjectDialog.kt`
Material 3 dialog for creating a new project:
- Project name input
- Creates sandbox directory in `home/projects/`

#### [NEW] `ui/project/ProjectScreen.kt`
Project detail view showing all threads within a project.

### 6.3 Settings Screen

#### [NEW] `ui/settings/SettingsScreen.kt`
Material 3 preference screen:
- **AI Provider:** Base URL, API key (masked), model name, compaction model (defaults to active model)
- **Personality:** Preset picker (Professional, Friendly, Minimal)
- **Approval Mode:** Default / Trusted / Cautious
- **Usage:** Token usage summary, cost per project, global cost, cost limits (per-project, global)
- **About:** Version, licenses

### Verification
- [ ] Sidebar opens/closes with gesture
- [ ] Creating a new project creates sandbox directory
- [ ] Switching between chats preserves conversation state
- [ ] Projects expand to show threads
- [ ] Settings changes persist and take effect

---

## Phase 7 — Web Browsing

**Goal:** Agent can search the web and browse pages via WebView.

### 7.1 Web Search

#### [NEW] `core/tools/WebSearchTool.kt`
- Calls DuckDuckGo Instant Answer API or Brave Search API
- Returns: list of results with title, URL, snippet
- No API key needed for DuckDuckGo (fallback), Brave key optional

### 7.2 WebView Browser

#### [NEW] `core/tools/BrowseWebTool.kt`
- Navigates a headless WebView to URL
- Injects JavaScript to extract page content (title, text, meta)
- Can take screenshots (for vision — post-MVP)
- Returns: `{title, url, textContent (truncated)}`

#### [NEW] `ui/browser/BrowserBottomSheet.kt`
Optional bottom sheet showing the WebView:
- User can see what the agent sees
- Read-only — agent controls navigation
- Can be dismissed

#### [NEW] `core/browser/WebViewManager.kt`
Manages a singleton WebView instance:
- JavaScript bridge for content extraction
- Cookie/session management
- Timeout handling

### Verification
- [ ] `web_search("how to compress video ffmpeg")` returns relevant results
- [ ] `browse_web("https://example.com")` extracts page content
- [ ] WebView bottom sheet shows the browsed page
- [ ] Agent can search → browse → extract info in a multi-step flow

---

## Phase 8 — Automations

**Goal:** Users can schedule recurring agent tasks.

### 8.1 Automation System

#### [NEW] `core/automation/AutomationScheduler.kt`
- Stores automations in Room DB
- Uses WorkManager for scheduling with exact alarms
- Each trigger starts AgentForegroundService with the automation's prompt
- Sends rich notification with results

#### [NEW] `ui/automation/AutomationsScreen.kt`
Automations management screen:
- List of automations with name, schedule, last run, status
- Toggle enable/disable
- Create new automation: name, prompt, schedule (simple picker or cron)
- Edit/delete existing

#### [NEW] `core/automation/BootReceiver.kt`
Re-registers all enabled automations after device reboot.

### Verification
- [ ] Creating an automation schedules it via WorkManager
- [ ] Automation triggers at scheduled time and runs agent
- [ ] Notification shows automation results
- [ ] Automations survive app kill and device reboot

---

## Phase 9 — Polish

**Goal:** Onboarding, notifications, background execution, and visual polish.

### 9.1 Onboarding

#### [NEW] `ui/onboarding/OnboardingScreen.kt`
First-launch flow:
1. Welcome screen with ClawDroid branding
2. Provider selection (base URL preset buttons: OpenAI, Groq, Together, Custom)
3. API key input (with validation — test call)
4. Bootstrap download with progress bar
5. Landing on first chat with a welcome message from the agent

### 9.2 Notifications

#### [NEW] `core/notification/NotificationManager.kt`
Rich, actionable notifications:
- **Task complete:** Shows result summary + action buttons (View, Share, Open Folder)
- **Needs input:** Shows agent's question + Open Task / Reply buttons
- **Automation result:** Shows summary + Read / Dismiss buttons
- **Background progress:** Persistent notification for foreground service

### 9.3 Foreground Service

#### [NEW] `core/service/AgentForegroundService.kt`
Keeps the agent alive when app is backgrounded:
- Persistent notification with progress
- Survives app kill via WorkManager restart
- Resumes from last compaction summary on restart

### 9.4 Memory Persistence

#### [NEW] `core/memory/MemoryManager.kt`
- Writes `~/.memory/memory.md` at end of significant conversations
- Reads memory file on new conversation start → inject into system prompt
- Content: user preferences, installed packages, project context, past decisions

### 9.5 Visual Polish
- Spring physics for all expand/collapse animations
- Haptic feedback on send, stop, and meaningful interactions
- Skeleton shimmer loading states
- Pulsing agent avatar while thinking
- Smooth shared element transitions between screens
- 60fps animations throughout

### Verification
- [ ] Onboarding flow completes and lands on a working chat
- [ ] Background agent task survives app backgrounding
- [ ] Notifications are actionable and informative
- [ ] Agent remembers user preferences across conversations
- [ ] Animations are smooth at 60fps
- [ ] App feels premium on a real device

---

## Verification Plan

### Automated Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

Key test areas:
- `InputTranslator` — escape sequence mapping
- `OutputBuffer` — ring buffer behavior, LLM truncation
- `DefensiveJsonParser` — malformed JSON recovery
- `MessageBuilder` — correct message ordering with tool results and steering
- `LoopDetector` — loop detection thresholds
- `CostTracker` — token estimation and limit enforcement
- `AnsiStripper` — ANSI code removal
- `LlmApiClient` — SSE parsing, chunk accumulation

### Manual Verification
- [ ] **Demo flow:** Share video → agent installs ffmpeg → compresses → notification with result
- [ ] **Multi-agent:** Two agents in same project running simultaneously
- [ ] **Steering:** Type a message while agent is working → agent adjusts course
- [ ] **Stop:** Press stop → everything halts immediately
- [ ] **Compaction:** Long conversation → compaction triggers → agent continues with summary
- [ ] **Background:** Start a task → leave app → come back → task completed
- [ ] **Automations:** Schedule a daily task → verify it runs on time

---

## Open Questions

All major questions have been resolved. Remaining minor items to decide during implementation:

1. **sharedUserId signing:** We need to sign our APK with a key that's compatible with the `com.termux` sharedUserId. If the user has Termux installed from F-Droid (signed with F-Droid's key), our app must use a compatible key — or the user installs our debug/release build WITHOUT Termux's F-Droid build present. Need to test this during Phase 1 prototyping.

2. **Bootstrap source URL stability:** Termux's GitHub releases may change URL patterns. Consider mirroring the bootstrap tarball to a ClawDroid-controlled server for reliability after initial prototyping.

3. **Material You on minSdk 26:** Dynamic colors require API 31+. On Android 8-11 devices, we'll use the muted teal/purple fallback palette. Verify the fallback looks good on older devices.
