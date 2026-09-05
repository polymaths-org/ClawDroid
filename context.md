# ClawDroid — Project Context

ClawDroid is a native Android AI agent app built in Kotlin, Jetpack Compose, and Material 3. It gives the user a personal autonomous assistant that runs commands, browses the web, automates tasks, and integrates with services natively on their phone.

---

## 🚀 How to Build & Run

### Prerequisites
* **JDK**: Version 21 (e.g., OpenJDK 21)
* **Android SDK**: installed and pointing to `~/.android-sdk` or configured in `local.properties` (platform android-36, build-tools 37.0.0)
* **Connected Device**: Running Android 8.0+ (API 26+) with ADB enabled

### Environment Configuration
Ensure your environment variables are set before building:
```bash
export ANDROID_HOME=~/.android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

### Build Commands
* **Compile Kotlin sources**:
  ```bash
  ./gradlew compileDebugKotlin
  ```
* **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
  The resulting APK is generated at: [app-debug.apk](file:///home/wraient/Projects/ClawDroid/app/build/outputs/apk/debug/app-debug.apk)
* **Install to connected device/emulator**:
  ```bash
  ./gradlew installDebug
  ```

---

## 🏗️ Architecture & Component Overview

ClawDroid consists of 45+ Kotlin files across 20+ packages. Here is the structure of the primary systems:

### 1. Agent Engine (`core/engine/`)
* **AgentEngine**: Runs the primary LLM orchestration loop. It gathers context, handles user steering, queries the OpenAI-compatible endpoint, receives tool calls, triggers parallel execution, and feeds results back to the LLM.
* **ToolExecutor**: Routes LLM tool requests to native tool definitions (e.g. commands, filesystem, Google integration).
* **CompactionManager**: Triggers context compaction at 80% token utilization, using a cheaper model turn to summarize the conversation so far, maintaining task status.
* **LoopDetector**: Tracks and stops repeating or circular tool calls (max 3 identical retries, hard cap of 10).
* **SteeringQueue**: Safely queues user messages sent mid-execution, appending them to the chat model's input context on the very next turn.

### 2. Google OAuth & Native APIs (`core/service/` & `core/tools/`)
* **GoogleAuthManager**: Handles native OAuth2 flows, exchanges authorization codes for tokens, caches tokens in memory, and handles secure refreshes using Keystore-encrypted refresh tokens.
* **GoogleTools**: Native high-performance REST wrappers for Google Services (Gmail/Calendar) mapped to LLM tools:
  * `gmail_list_messages` & `gmail_get_message`
  * `gmail_send_message` & `gmail_create_draft`
  * `calendar_list_events` & `calendar_create_event`
* **Access Configuration & Security**:
  * SharedPreferences support enabling/disabling the entire Google connector (`googleConnectorEnabled`), Gmail tools (`googleGmailEnabled`), and Calendar tools (`googleCalendarEnabled`).
  * `ToolSchemaRegistry` checks these states and dynamically filters Gmail/Calendar schemas from the agent's active tool list. If a service is disabled or offline, its tools are completely hidden from the agent.
  * `ToolExecutor` runs runtime validation checks on incoming tool calls, throwing errors if a client attempts to invoke disabled/disconnected tools.
* **SMTP Header Formatting**: Standard SMTP email payloads are assembled with CRLF (`\r\n`) line endings. To prevent header folding (which causes `Invalid To header` rejections), raw email strings are concatenated directly, bypassing indentation stripping utilities like Kotlin's `trimIndent()`.

### 3. Model Context Protocol (MCP) Support (`core/engine/`)
* **McpClient**: Implements stdio JSON-RPC 2.0 communication to orchestrate external MCP servers running Node.js or Python packages inside the sandboxed Termux environment.
* **McpServerLauncher**: Handles the lifecycle (auto-start, configure, and shutdown) of background MCP subprocesses (e.g., Filesystem, GitHub, Fetch).

### 4. Sandbox Terminal (`core/terminal/`)
* **ProcessManager**: Non-blocking command execution runner using a pseudoterminal (`script -q -c`) to manage multiple concurrent Linux terminal sessions.
* **ManagedProcess**: Thread-safe state machine representing process states (`RUNNING`, `WAITING_FOR_INPUT`, `COMPLETED`, etc.).
* **OutputBuffer**: Ring buffer preserving terminal stdout/stderr logs (up to 500 lines) with smart truncation for LLM consumption.
* **InputTranslator**: Translates LLM virtual key codes (e.g., `[ENTER]`, `[CTRL+C]`, arrow keys) into raw terminal characters.

### 5. Voice Chat (`core/voice/` & `ui/voice/`)
* **SpeechRecognizerClient**: Configures Android's offline `SpeechRecognizer` for low-latency, zero-cost, privacy-safe voice-to-text.
* **TtsEngine / PiperEngine**: Custom neural TTS client that runs Piper (ONNX) locally in the Linux environment with fallback to standard `AndroidTtsEngine`.
* **VoiceManager**: Handles speech state, markdown removal, filler phrase insertion, and controls the audio visualizer.
* **AudioVisualizerOrb**: Implements an interactive Compose visualizer canvas (particle rings, gradients) reacting to microphone and speech amplitudes.

### 6. User Interface (`ui/`)
* **ChatScreen**: Standard conversation view with collapsible/expandable nested activity steps displaying terminal commands, web visits, and tool results.
* **McpScreen**: A premium, card-based dashboard for MCP settings, displaying connection statuses, server switches, JSON configuration editors, and an inline bottom sheet for live logs.
* **SidebarContent**: Telegram-style drawer showing Projects (grouped chats sharing a sandboxed sandbox folder), Chats, Settings, and Actions.

---

## 🗄️ Database Schema & Room Entities

Database files are managed via Room in `ClawDroidDatabase.kt`. Key tables include:
1. `conversations`: ID, project connection, titles, status, and cost.
2. `messages`: ID, parent conversation (Foreign Key with `ON DELETE CASCADE`), role, content, timestamp, token count, and attachment metadata.
3. `tool_calls`: ID, parent message, tool name, arguments, outputs, execution status, and execution duration.
4. `projects`: Sandbox settings, root paths, and metadata.

---

## ⚠️ Key Bug Fixes to Remember

### 1. SMTP Header folding Rejection
* **Issue**: Sending multi-line emails resulted in `POST Error 400: Invalid To header`.
* **Cause**: Kotlin's `trimIndent()` evaluated the multi-line email body. If body lines had zero indentation, `trimIndent()` skipped stripping leading spaces from the header lines (`Subject:`, `Content-Type:`), which were then treated by SMTP parsers as continuation/folding lines of the `To:` header.
* **Fix**: Built emails via direct string concatenation and normalized all newlines (`\n` and `\r\n`) to clean `\r\n` line breaks.

### 2. SQLite Foreign Key Constraint Crash
* **Issue**: Tapping send crashed the application with `SQLiteConstraintException: FOREIGN KEY constraint failed`.
* **Cause**: On launch, the app restored `currentConversationId` from SharedPreferences. If the conversation had been deleted or pruned from SQLite, the ID became orphaned. Because the ID was non-null, the startup check skipped recreating a default chat session. Sending a message inserted a child row referencing a non-existent conversation parent.
* **Fix**: Added validation to verify that `currentConversationId` exists in the database on startup; if not, the app resets it to the latest valid conversation or creates a new one.

---

## 🤝 Project Contact & Specs
* Core Specifications: Refer to [AGENTS.md](file:///home/wraient/Projects/ClawDroid/AGENTS.md)
* Implementation Log: Refer to [walkthrough.md](file:///home/wraient/.gemini/Antigravity/brain/169fc980-2f9d-4600-882e-3bdb26ef99b5/walkthrough.md)
