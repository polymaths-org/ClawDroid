# ClawDroid — Project Context

## What It Is

A native Android AI agent with an embedded Linux terminal (Termux bootstrap), browser (WebView), and tool execution — all inside a sandboxed environment. The user sees everything the agent does via collapsible activity steps (Codex-style). Built in Kotlin + Jetpack Compose + Material 3.

## Build

```bash
export ANDROID_HOME=~/.android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- JDK 21, SDK at `~/.android-sdk` (platform android-36, build-tools 37.0.0)
- Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21
- minSdk = 26, targetSdk = 28 (W^X compat for binary execution), compileSdk = 36

## Architecture (45 Kotlin files, 20 packages)

### Voice (`core/voice/` + `ui/voice/`)
- **TtsEngine** (interface) — Pluggable TTS backends (state, isSpeaking, speak, stop, destroy)
- **AndroidTtsEngine** — Android TTS with neural/male voice preference (pitch 0.75, rate 0.82)
- **PiperEngine** — Downloads piper binary (Linux aarch64) + Ryan male voice model (~50MB ONNX) to sandbox. Runs on-device neural TTS via CLI, plays WAV via MediaPlayer. Falls back to AndroidTtsEngine if unavailable.
- **VoiceManager** — Orchestrates TtsEngine selection: tries Piper first, falls back to AndroidTTS. Text processing (markdown stripping, natural breaks). `isSpeaking` + `agentVoiceAmplitude` StateFlows.
- **SpeechRecognizerClient** — Android built-in SpeechRecognizer (offline, instant, no API key), `partialResult` + `userVoiceAmplitude` StateFlows from `onRmsChanged`
- **ThinkingPhrases** — 15 filler phrases, no consecutive repeats
- **AudioVisualizerOrb** — Canvas orb with 4 states (Idle/Listening/Thinking/Speaking), radial/sweep gradients, dual particle rings, amplitude-reactive
- **VoiceOverlay** — Full-screen call-mode overlay with orb, live transcript, mute/end controls

### Agent Engine (`core/engine/`)
- **AgentEngine** — Core loop: build messages → check steering → call LLM → execute tools → loop
- **ToolExecutor** — 14 tools (execute_command, start_process, read_file, write_file, edit_file, browse_web, etc.)
- **CompactionManager** — Context compaction at 80% token limit using cheaper model
- **CostTracker** — Per-project and global cost limits
- **LoopDetector** — Detects 3+ similar tool calls, injects steer message, hard cap at 10
- **SteeringQueue** — Queues user messages mid-run, injects after current tool call

### Terminal (`core/terminal/`)
- **ProcessManager** — Non-blocking command execution via `script -q -c` (PTY), multiple concurrent processes
- **ManagedProcess** — Per-process state machine (RUNNING/WAITING_FOR_INPUT/COMPLETED/FAILED/TIMED_OUT)
- **OutputBuffer** — Ring buffer (500 lines), smart LLM truncation (first 5 + last 30)
- **InputTranslator** — Special key encoding (`[ENTER]`, `[CTRL+C]`, `[UP]`, etc.)
- **AnsiStripper** — Strips ANSI codes for LLM, preserves for UI

### API (`data/api/`)
- **LlmApiClient** — OpenAI-compatible streaming SSE client, configurable base URL/API key/model
- **OpenRouterClient** — OpenRouter-specific headers/metrics
- **DefensiveJsonParser** — Handles malformed JSON from LLM (trailing commas, unescaped quotes, etc.)
- **RetryClient** — Exponential backoff (429/500/network errors)
- **MessageBuilder** — Correct message ordering (tool results before steering)
- **ToolSchemaRegistry** — 14 tool schemas, conditional loading

### Tools (`core/tools/`)
- **CommandTool** — execute_command with timeout → auto-promote to background
- **ProcessTools** — start_process, check_process, send_input, kill_process, list_processes
- **FileTools** — read_file, write_file, edit_file, list_directory
- **WebTools** — browse_web, web_search
- **NotificationTool** — send_notification

### UI (`ui/`)
- **ChatScreen** — LazyColumn chat + collapsible activity steps + InlineActivityTrail
- **SidebarContent** — Drawer with Projects/Chats/Settings/Quick Actions
- **SettingsScreen** — Base URL, masked API key (show/hide), model field, save

### Control (`core/control/`)
- **ScreenReaderService** — `AccessibilityService` that reads the UI tree, truncates it to JSON for the LLM, and dispatches gestures (tap, swipe, type) and global actions.
- **ScreenCaptureManager** — Manages `MediaProjection` to provide screenshot fallback (base64 JPEG) when the accessibility tree is empty (e.g., in games).
- **AndroidControlTools** — Bridge exposing screen tools (`get_screen`, `tap`, `swipe`, `type_text`, `launch_app`) to the agent, returning JSON responses.

### Other
- **BootstrapManager** — Downloads Termux bootstrap (~80MB), extracts Linux environment
- **AppConfigManager** — SharedPreferences for API credentials (fallback to BuildConfig, then hardcoded defaults)
- **AutomationScheduler** — Cron-like scheduler via WorkManager
- **ClawDroidDatabase** — Room DB (conversations, projects, automations)

## Key Decisions

| Decision | Choice |
|----------|--------|
| AI Provider | OpenAI-compatible endpoint (OpenAI, Groq, Together, Ollama) |
| STT | Android built-in SpeechRecognizer (no API key, offline) |
| TTS | Android TextToSpeech (open source AOSP) with male voice preference |
| Sandbox | Termux bootstrap + `sharedUserId=com.termux` |
| PTY | `script -q -c` (zero native code) |
| Storage | SharedPreferences for config, Room DB for data |
| API key | Reused for both LLM and Whisper (when Whisper was used) |
| Architecture | MVVM, single-activity, Navigation Compose, Hilt-less (manual DI) |

## What's Working

- Android Screen Control: AccessibilityService for reading UI trees and executing gestures, with MediaProjection fallback for screenshots. Integrated with agent tool schemas (`get_screen`, `tap`, etc.).
- Voice chat: SpeechRecognizer STT → AgentEngine → TTS response with thinking phrases
- Collapsible activity steps (3 levels: group → individual step → full output)
- Sidebar navigation with Projects/Chats
- Settings screen for API key configuration
- Agent engine loop with tool execution
- Terminal process management (PTY, input, output buffering)

## What's Not Implemented / Future Plans

- **Root-Only Bonus Features:** Bypass `FLAG_SECURE`, persistent accessibility service recovery, silent MediaProjection.
- **Voice input / camera (post-MVP)**
- **Connected Services / OAuth**
- **Native Anthropic / Google API clients** (use OpenAI-compatible)
- **Local LLM inference**
- **Multi-user / cloud sync**
- **End-to-end encryption**
- **Widgets**
- **EncryptedSharedPreferences** (currently plain SharedPreferences)

## Remaining Warning

`VoiceManager.kt:123` — `onError(String)` override is deprecated but harmless (minSdk=26, can use `onError(String?, Int)` overload).

## Contact / Handoff

Built for the hackathon. Questions about the architecture should reference `AGENTS.md` for the full product spec and `implementation_plan.md` for the original build plan.
