# ClawDroid

**A native Android AI agent that works for you — transparently, autonomously, and beautifully.**

> Codex for your pocket. An agent that browses, codes, installs, processes, and automates —
> all inside a sandboxed Linux environment on your phone. You see everything it does.
> You stay in control.

---

## What We're Building

An AI agent app for Android that gives you a **personal autonomous assistant** with real capabilities — not just chat. The agent has a full Linux terminal, a browser, access to your connected services, and the autonomy to figure things out on its own. It can install software, write and run code, browse the web, process files, and automate recurring tasks.

But here's what makes it different: **you can always see what it did.** There's no black box. Every command it ran, every page it browsed, every file it created — it's all logged as collapsible activity steps in the conversation. You can ignore it and let the agent work, or expand any step to see exactly what happened. You can intervene, steer, or just let it cook.

It's like having a junior dev sitting next to you, except it's in your pocket, it never sleeps, and it can run multiple tasks in parallel.

---

## Our Values

### 1. Transparency Over Magic

The agent is not a black box. Every action the agent takes is recorded as a **collapsible activity step** in the conversation thread — just like Codex. Steps are collapsed by default when complete, keeping the chat clean, but the user can expand any step to inspect exactly what happened.

Each step shows a **one-line summary** when collapsed:

- `📂 Explored 5 files in /workspace/src/` 
- `⚙️ Ran "apt install ffmpeg" (12s)`
- `🌐 Visited stackoverflow.com, github.com`
- `📝 Created 3 files in /workspace/output/`
- `🔧 Ran "ffmpeg -i input.mp4 ..." (45s)`

Tapping a collapsed step expands it to show the **full command, output, and context** — with a fixed max height and scroll so it never takes over the screen. Currently running steps stay expanded with live-streaming output and auto-collapse when done.

```
┌─────────────────────────────────────────────┐
│  🐙 Agent                                   │
│  "I'll compress your video to 720p."        │
│                                              │
│  ▶ 📂 Explored workspace/              0.2s │  ← collapsed, tap to expand
│  ▶ ⚙️ Ran "apt install ffmpeg"          12s  │  ← collapsed
│  ▼ 🔧 Ran "ffmpeg -i input.mp4 ..."         │  ← expanded (running)
│  ┌──────────────────────────────────────┐   │
│  │ $ ffmpeg -i input.mp4 -vf scale=... │   │
│  │ frame= 1243 fps=52 time=00:01:23   │   │
│  │ bitrate= 1842.3kbits/s speed=2.1x  │   │
│  │ ░░░░░░░░░░░░████████ 67%           │   │
│  └──────────────────────────────────────┘   │
│  ▶ 🌐 Visited docs.ffmpeg.org          3s   │  ← collapsed
│                                              │
│  ✅ "Done! 154MB → 28MB (82% smaller)"      │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │ Type a message...          [⏹ Abort]│   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

**Why:** Trust comes from transparency. The user doesn't need to babysit the agent — but if it's stuck in a loop or doing something wrong, they can see it immediately and course-correct. The activity log is also invaluable for understanding what the agent did after the fact.

### 2. Human in the Loop — Always

The agent is autonomous inside its sandbox, but the **human is always the boss.** This means:

- **Stop (⏹):** While the agent is working, the send button becomes a **stop button** (⏹). One tap halts everything **immediately** — the current command is killed mid-execution, all running processes are terminated, the LLM stream is cancelled, and control returns to the user. The user can then tell the agent what to do differently.
- **Steer:** The text input field is always available while the agent is working. The user can type a message mid-task — "Actually, use 480p instead of 720p." The message is **queued** and fed to the model after the current tool call / command finishes, before the next turn begins. The agent reads it and adjusts course without starting over. No special button — it's just the normal chat input.
- **Approve:** For actions outside the sandbox (OAuth API calls, deployments, emails), the agent asks first by default. Approval prompts use **clean Material 3 UI** — bottom sheets or dialogs, never raw text confirmations. **This is configurable** — see below.
- **Notifications:** Rich, actionable notifications keep the user informed. Not just "task done" — but "here's what I did, here's the result, here are your options."

#### Approval Settings (Configurable)

Approval behavior is configurable in Settings (Material 3 preference screen). The user picks what works for them:

| Mode | Sandbox | Connected Services | Use Case |
|------|---------|-------------------|----------|
| **Default** | ✅ Full auto | 🔐 Ask before acting | Safe default for new users |
| **Trusted** | ✅ Full auto | ✅ Full auto | Power users who trust the agent |
| **Cautious** | 🔐 Ask for installs | 🔐 Ask before acting | Maximum oversight |

The agent should feel like a **copilot, not an autopilot.** The user delegates, but never loses control.

### 3. Autonomy Inside the Sandbox

Inside the sandbox, the agent has **full freedom.** No permission dialogs, no "are you sure?" prompts, no hand-holding. The sandbox is the permission boundary.

- Install any package? Go ahead.
- Delete files? It's your workspace.
- Build from source? If apt doesn't work, figure it out.
- Run 50 commands in a row debugging an installation? Do it.

The agent should be a **competent sysadmin** of its own environment. It reads error messages, searches for solutions, tries alternatives, and only escalates to the user when it genuinely can't solve something.

The user can always see what the agent is doing via the collapsible activity steps, but **doesn't have to interact** unless they choose to. This is critical — if the agent is stuck in a loop retrying the same failing command, the user can spot it in the activity feed and steer it. But if everything is going well, they can ignore the details entirely.

**The user should never see:** "Permission denied. Would you like to grant access?"  
**The user should see:** A collapsed step `⚙️ Installed ffmpeg (built from source, apt repo was down) — 45s` that they can expand if curious.

### 4. Simplicity Over Feature Count

The interface should be **dead simple.** Inspired by Claude's clean chat interface with Codex's collapsible agent activity steps — adapted for Material 3 on Android.

- One primary screen: the conversation with the agent
- Activity steps (terminal, browser, files) inline and collapsible — never in the way
- Telegram-style command menu: type `/` to see available commands and skills
- No settings pages with 50 toggles
- No complicated workflows to "configure the agent"
- You open the app, you talk to the agent, it works

**We would rather have 5 features that feel magical than 50 that feel clunky.**

### 5. Native Android, Premium Feel

This is not a web wrapper. This is not a React Native compromise. This is a **native Kotlin app with Jetpack Compose and Material 3.** It should feel like it belongs on Android.

- Smooth 60fps animations
- Haptic feedback on meaningful interactions
- System theme integration (dynamic colors, dark mode)
- Proper back navigation, gesture support
- Notifications that look and behave like native Android notifications
- Widgets that feel like they're part of the OS

**The bar:** Would a Google designer be proud of this UI? If not, it's not done.

### 6. Multiplicity — Projects & Agents

Work is organized into **Projects.** Each project has its own sandbox, its own conversation history, and can have multiple agent sessions. This keeps things clean and navigable.

```
┌──────────────────────────────────────────────┐
│  ☰  🐙 ClawDroid                             │
├──────┬───────────────────────────────────────┤
│      │                                        │
│  📁  │  Project: "Video Tools"                │
│  P   │                                        │
│  r   │  ┌─────────────────────────────────┐  │
│  o   │  │ Agent 1: "Compress wedding vid" │  │
│  j   │  │ ✅ Done — 2 hours ago            │  │
│  e   │  └─────────────────────────────────┘  │
│  c   │  ┌─────────────────────────────────┐  │
│  t   │  │ Agent 2: "Extract audio tracks" │  │
│  s   │  │ 🔄 Running...                    │  │
│      │  └─────────────────────────────────┘  │
│  ──  │                                        │
│  📁  │  [+ New Agent in this Project]         │
│  Web │                                        │
│  App │                                        │
│  ──  │                                        │
│  📁  │                                        │
│  Re- │                                        │
│  se- │                                        │
│  arch│                                        │
│      │                                        │
│  ──  │                                        │
│  +   │                                        │
│ New  │                                        │
└──────┴───────────────────────────────────────┘
```

Key principles:
- **Quick Actions** at the top of the sidebar for one-tap access to settings, automations, connected services
- **Chats** are standalone conversations — quick one-off tasks not tied to a project
- **Projects** group related work (sidebar navigation), **sorted by most recently used**
- Tapping a project name reveals its chat threads underneath
- Multiple **agents/threads** can run inside the same project
- Agents in the same project **share the same sandbox** — so one agent can install ffmpeg and the next agent can use it
- Each agent has its own conversation history
- Agents can run in parallel without interference

### 7. Context Management — Compaction

Long conversations get expensive and slow. We handle this with **context compaction:**

- Compaction triggers **only when approaching the model's token limit**, with enough headroom so we're never caught mid-response hitting the ceiling
- The agent summarizes the conversation so far into a compact context block
- Old messages are archived but accessible (user can scroll back in the UI)
- The agent retains key facts, decisions, and task progress in the summary
- A subtle indicator is shown in the UI when compaction occurs: `── context compacted ──` so the user knows it happened, but it's not intrusive

```
Conversation Flow:
  Messages 1-N:    Full context
  Approaching limit: Auto-compact with headroom
                     → "Summary of conversation so far"
  Messages N+1...: Full context + compact summary
  Approaching again: Re-compact (summary of summary + recent)
  ...
```

Additional context strategies:
- **No file context bloat:** Don't inject the full file tree into the agent's context. Let the agent explore the workspace on its own — use tools to list files, read them. Compaction summaries naturally retain awareness of what files exist and what's been done.
- **Memory persistence:** Key facts survive across conversations ("User prefers Python over JS"). Stored in a lightweight memory file the agent can read.
- **Task continuity:** If the app is killed and reopened, the agent picks up where it left off using the last compaction summary + conversation history from Room DB.

---

## Architecture Principles

### The Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| UI | Kotlin + Jetpack Compose + Material 3 | Native, fast, beautiful |
| AI | Configurable — see Model Selection below | Flexibility, future-proof |
| Runtime | Embedded Linux (Termux bootstrap) | Full bash, apt, everything |
| Browser | WebView + JavaScript bridge | Agent controls, user watches |
| Storage | Room DB + EncryptedSharedPreferences | Secure, persistent |
| Background | WorkManager + Foreground Service | Survives app kills |
| Voice | OpenAI Whisper + TTS | Best quality |
| Vision | GPT-4o / multimodal model | Camera + screenshot analysis |

#### Model Selection

For MVP, the app supports a single **OpenAI-compatible endpoint** interface. This covers OpenAI, Groq, Together, local Ollama, and any provider with an OpenAI-compatible API. The user configures: **base URL** + **API key** + **model name**.

Native Anthropic and Google API support will be added post-MVP.

**Configuration:**
- Select default model in Settings
- Can change model per chat session
- Can switch model mid-session
- API keys stored in EncryptedSharedPreferences

### The Sandbox

The agent's Linux environment is embedded directly in the app. No companion apps, no remote servers.

```
/data/data/com.clawdroid.app/files/
├── usr/                    ← Linux binaries (bash, python, ffmpeg...)
│   ├── bin/
│   ├── lib/
│   ├── etc/
│   └── var/
├── home/                   ← Agent's home directory
│   ├── projects/
│   │   ├── video-tools/    ← Project sandbox (shared across agents)
│   │   ├── web-app/        ← Another project sandbox
│   │   └── research/       ← Another project sandbox
│   ├── scripts/            ← Reusable scripts agent writes
│   └── .memory/            ← Persistent memory store
└── tmp/                    ← Temporary processing
```

User-accessible shared folder:
```
/storage/emulated/0/Documents/ClawDroid/
├── Inbox/                  ← User drops files here for the agent
├── Output/                 ← Agent puts finished files here
├── Projects/               ← Organized by project
└── Exports/                ← Conversation exports, reports
```

First launch downloads the bootstrap (~80MB), extracts it, and the agent has a full Linux environment. The agent can install any additional packages via apt.

---

## Agent Engine

The agent engine is the core loop that orchestrates everything — receiving user messages, calling the LLM, executing tools, and feeding results back. Getting this right is make-or-break.

### The Agent Loop

```
┌──────────────────────────────────────────────────────────┐
│                      AGENT LOOP                           │
│                                                           │
│  ┌───────────┐                                           │
│  │   START    │                                           │
│  └─────┬─────┘                                           │
│        ▼                                                  │
│  ┌──────────────────┐                                    │
│  │ Build messages:   │                                    │
│  │ system prompt     │                                    │
│  │ + compacted ctx   │                                    │
│  │ + recent msgs     │                                    │
│  │ + tool results    │                                    │
│  └─────┬────────────┘                                    │
│        ▼                                                  │
│  ┌──────────────────┐     ┌───────────────────┐          │
│  │ CHECK: any        │──Y─►│ Inject steering   │          │
│  │ steering msgs     │     │ msg into context  │          │
│  │ from user?        │     │ before this turn   │          │
│  └─────┬────────────┘     └─────────┬─────────┘          │
│        │ N                          │                     │
│        ◄────────────────────────────┘                     │
│        ▼                                                  │
│  ┌──────────────────┐                                    │
│  │ Call LLM API      │                                    │
│  │ (streaming)       │                                    │
│  └─────┬────────────┘                                    │
│        ▼                                                  │
│  ┌──────────────────┐                                    │
│  │ Response type?    │                                    │
│  └──┬──────────┬────┘                                    │
│   TEXT      TOOL_CALL(s)                                 │
│     │          │                                          │
│     ▼          ▼                                          │
│  ┌──────┐   ┌───────────────────┐                        │
│  │RENDER│   │ Execute tool(s)    │                        │
│  │to UI │   │                    │                        │
│  │      │   │ Parallel if        │                        │
│  │Group │   │ independent:       │                        │
│  │auto- │   │ • read file A      │                        │
│  │close │   │ • read file B      │                        │
│  │      │   │ • list dir C       │                        │
│  │DONE  │   │                    │                        │
│  └──────┘   │ Serial if          │                        │
│             │ dependent:         │                        │
│             │ • install pkg      │                        │
│             │   └→ then use pkg  │                        │
│             └────────┬──────────┘                        │
│                      │                                    │
│                      ▼                                    │
│             ┌────────────────┐                            │
│             │ CHECK: user     │                            │
│             │ pressed STOP?   │──► YES ──► HALT + CLEANUP │
│             └────────┬───────┘                            │
│                      │ NO                                 │
│                      ▼                                    │
│             Feed tool results                             │
│             back to LLM ──────────► (loop back to top)    │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

### Key Engine Rules

1. **Steering injection:** Before every LLM call, check the steering queue. If the user typed a message while the agent was running, inject it as a user message after all tool results, before the next model turn. The agent adjusts course naturally.

2. **Parallel tool execution:** When the LLM returns multiple tool calls in one response, execute independent ones simultaneously. This is critical for speed — reading 3 files shouldn't take 3x the time.

3. **Stop is immediate:** When the user presses Stop, **everything halts instantly** — the current command is killed mid-execution, all running processes are terminated, the current LLM stream is cancelled, and control returns to the user. No waiting for the current step to finish. The stop signal is checked on a separate thread/coroutine, not polled between steps.

4. **Loop detection:** Track recent tool calls. If the agent makes 3+ very similar calls in a row, inject a system message: "You've attempted this multiple times. Try a fundamentally different approach or tell the user you can't do it." Hard cap at 10 identical attempts.

5. **Cost tracking:** Every LLM call records token usage. Per-project and global cost limits are enforced. The user can configure these in Settings.

### The Tool Set

The agent has these tools — lean, well-defined, no overlap:

| Tool | Purpose | Blocking? |
|------|---------|-----------|
| `execute_command` | Run short commands (ls, cat, grep). Waits for completion with a timeout. | Sync (up to timeout) |
| `start_process` | Start long-running commands (servers, builds, ffmpeg). Returns immediately. | Async |
| `check_process` | Check status + recent output of a running process. Detects if waiting for input. | Instant |
| `send_input` | Send text, special keys (arrow keys, Ctrl+C, y/n) to a running process. | Instant |
| `kill_process` | Kill a running process + all its children. | Instant |
| `list_processes` | List all active and recently completed processes. | Instant |
| `read_file` | Read file contents. | Instant |
| `write_file` | Create or overwrite a file. Used instead of interactive editors. | Instant |
| `edit_file` | Apply targeted edits to an existing file (search/replace). | Instant |
| `list_directory` | List directory contents with sizes. | Instant |
| `browse_web` | Navigate WebView to a URL, extract content. | Sync |
| `web_search` | Search the web and return results. | Sync |
| `use_service` | Call a connected OAuth service API. | Sync |
| `send_notification` | Send a notification to the user. | Instant |

**Important: No interactive editors.** The agent never opens vim, nano, or any interactive editor. All file creation and editing is done via `write_file` and `edit_file` tools. This avoids the entire class of problems with terminal-based editors.

---

## Terminal & Process Management

This is the most critical engineering subsystem. If the agent can't manage terminals properly, nothing else works.

### Non-Blocking Execution

Every command runs asynchronously. The agent is **never blocked** waiting for a command to finish.

- **Short commands** (`execute_command`): Run with a timeout (default 30s). If the command finishes in time, return the result. If it times out, automatically promote to a background process and return the process ID.
- **Long commands** (`start_process`): Start the command, wait 2-3 seconds for initial output (catches immediate failures), then return the process ID. The agent can check on it later. Has a **default 5-minute timeout**, but the agent can specify a custom timeout up to a **maximum of 3 hours**. For truly indefinite processes (servers), the agent sets the max and kills the process when done.

### Multiple Concurrent Terminals

Each process is independent. The agent can run as many as needed simultaneously:

```
Shell #1: apt install -y ffmpeg     (RUNNING, 12s elapsed)
Shell #2: npm run dev               (RUNNING, server on :3000)
Shell #3: curl localhost:3000       (COMPLETED, got response)
```

A central ProcessManager tracks all processes by ID, reads output asynchronously via coroutines, enforces timeouts, and handles cleanup.

### PTY Support (Pseudo-Terminal)

Plain `ProcessBuilder` pipes don't work for interactive programs — the program detects it's not in a real terminal and disables prompts, colors, and interactive features.

We use the `script` command as a PTY wrapper — zero native code, works immediately:

```bash
script -q -c "apt install imagemagick" /dev/null
```

This makes every process think it's running in a real terminal. Benefits:
- Programs show interactive prompts (Y/n, password, etc.)
- Colors and progress bars work
- Readline/tab completion works when sent via escape codes
- No JNI or NDK required

### Interactive Input — Special Key Support

The agent can send any input to a running process via `send_input`, including special keys encoded as human-readable tokens:

| Token | Sends | Use Case |
|-------|-------|----------|
| `[ENTER]` | `\r` | Confirm prompts |
| `[TAB]` | `\t` | Tab completion |
| `[UP]` / `[DOWN]` | `\033[A` / `\033[B` | Navigate menus, history |
| `[LEFT]` / `[RIGHT]` | `\033[C` / `\033[D` | Cursor movement |
| `[CTRL+C]` | `\x03` + SIGINT | Interrupt/kill a command |
| `[CTRL+D]` | `\x04` | Send EOF (exit REPL) |
| `[CTRL+Z]` | `\x1a` | Suspend a process |
| `[HOME]` / `[END]` | `\033[H` / `\033[F` | Jump to start/end of line |
| `[BACKSPACE]` | `\x7f` | Delete character |
| Regular text | As-is | Normal input |

**Example — responding to apt prompt:**
```
Agent runs:  start_process("apt install imagemagick")
Output:      "Do you want to continue? [Y/n]"
Agent sends: send_input(process_id, "y[ENTER]")
```

**Example — navigating a menu:**
```
Agent runs:  start_process("npx create-next-app@latest")
Output:      "? Would you like to use TypeScript? › No / Yes"
Agent sends: send_input(process_id, "[RIGHT][ENTER]")
```

**Example — stopping a server:**
```
Agent sends: send_input(process_id, "[CTRL+C]")
```

**However:** The agent should prefer non-interactive flags when available (`-y`, `--yes`, `-q`). Interactive input via `send_input` is the fallback, not the default.

### Prompt Detection

The ProcessManager monitors output for common prompt patterns and reports when a process is waiting for input:

- `[Y/n]`, `[y/N]` — Package manager confirmations
- `password:` — Authentication prompts
- `>>>`, `>` — REPL prompts (Python, Node)
- `? ... ›` — Interactive CLI frameworks (inquirer.js, etc.)
- No output for 10+ seconds — possibly stuck

When detected, the tool result includes `"status": "WAITING_FOR_INPUT"` with the detected prompt, so the agent can respond appropriately.

### Output Management

LLM context is precious. Terminal output is managed carefully:

- **Ring buffer:** Each process keeps the last 500 lines. Old output is evicted, not accumulated indefinitely. Prevents memory issues from commands that produce infinite output.
- **Smart truncation for LLM:** First 5 lines (initial context) + last 30 lines (most recent output/errors) + omitted line count. If exit code ≠ 0, stderr is prioritized.
- **Full output for UI:** The activity step stores complete output so the user can scroll through everything.
- **ANSI stripping:** Terminal color codes and cursor sequences are stripped before feeding to the LLM, but preserved for the UI activity step rendering.

### Shell Environment

Each command runs as a new process via `bash -c "command"` with consistent environment variables (PATH, HOME, LD_LIBRARY_PATH, etc.). There is **no persistent shell session** — this is intentional:

- ✅ Clean slate per command — no state leakage
- ✅ Multiple commands can run in parallel
- ✅ Timeout per command is trivial
- ✅ Crash recovery — just retry
- ✅ Process completion detection — process exits

The agent manages its own state by specifying the `cwd` (working directory) parameter for each command.

### Failure Handling

| Scenario | Solution |
|----------|----------|
| **Command hangs forever** | `execute_command` has a default 30s timeout (promotes to background if exceeded). `start_process` has a **default 5-minute timeout**, configurable by the agent up to a **maximum of 3 hours**. For servers and other indefinite processes, the agent sets the max timeout and kills the process explicitly when done. |
| **Infinite output** | Ring buffer with max 500 lines. Old lines evicted. Memory is bounded. |
| **Command needs stdin** | Prefer non-interactive flags (`-y`). Fallback: prompt detection → agent sends input via `send_input`. |
| **Command forks a daemon** | Track child processes via process groups. On kill, kill the entire process group (`kill -TERM -$PID`). |
| **Agent stuck in retry loop** | Loop detector: 3+ similar commands → inject "try a different approach" system message. Hard cap at 10. |
| **Destructive commands** | Sandbox boundary protects the phone. Agent can't escape `/data/data/com.clawdroid.app/files/`. The sandbox is the permission model. |

---

## API Integration

### Streaming Responses

All LLM responses are streamed via SSE. This is non-negotiable — a non-streaming call can take 30-60 seconds, and the user stares at a blank screen.

**Key challenge:** Tool call arguments arrive as fragmented JSON chunks. The function name and arguments are split across multiple SSE events and must be accumulated per tool call index before parsing. If the connection drops mid-stream and the JSON is incomplete, the **entire tool call is discarded and the request is retried.**

Multiple parallel tool calls can arrive in the same stream, each identified by an `index` field. Each is accumulated independently.

### Message Ordering (Critical)

Multi-turn tool use follows strict ordering rules with the OpenAI API:

1. Every `tool_calls` in an assistant message **must** have a matching `tool` result message. Missing one → 400 error.
2. `tool_call_id` must match exactly between the request and response.
3. Tool results must be **strings**, even if the content is JSON.
4. Steering messages go **after** all tool results, **before** the next LLM call. Never between an assistant's tool_calls and the tool results.

```
CORRECT:
  assistant → tool_calls: [call_1, call_2]
  tool      → result for call_1
  tool      → result for call_2
  user      → "[STEERING] use 480p instead"    ← injected here
  → next LLM call

WRONG:
  assistant → tool_calls: [call_1, call_2]
  tool      → result for call_1
  user      → "use 480p instead"               ← BREAKS: call_2 missing
  tool      → result for call_2
```

### Token Management

Token usage is estimated **before** every API call. If approaching the model's context limit (with 20% headroom), compaction triggers automatically before sending.

**Compaction uses a cheaper/faster model** (e.g., GPT-4o-mini) — not the main model. The compaction summarizes the conversation retaining: key decisions, files modified, packages installed, current task state, errors encountered, and user preferences.

### Rate Limiting & Retries

All API calls go through a rate-limited client with exponential backoff:

| HTTP Code | Meaning | Response |
|-----------|---------|----------|
| 429 | Rate limited | Respect `retry-after` header, exponential backoff (1s, 2s, 4s, 8s), max 5 retries |
| 400 | Context too long | Compact conversation, retry |
| 401 | Bad API key | Prompt user to re-enter key in Settings |
| 500/503 | Server error | Retry 3x with backoff, then show error |
| Network error | Connection failed | Check connectivity, retry with backoff, show "reconnecting..." |

### Cost Control

An autonomous agent can burn through API credits fast — especially if stuck in a retry loop. Two simple safeguards:

- **Per-project cost limit** (configurable in project settings)
- **Global cost limit** (configurable in app settings, applies across all projects)

### Defensive JSON Parsing

The LLM sometimes produces invalid JSON for tool call arguments (trailing commas, unescaped quotes, extra text outside JSON). The parser attempts:
1. Standard parse
2. Fix common issues (trailing commas, etc.)
3. Extract JSON from surrounding text
4. If all fail → return an error message to the model listing the schema. The model self-corrects on retry.

### Tool Definition Efficiency

Tool schemas eat ~200-400 tokens each. We keep the tool set lean (14 tools max). Conditional tools (camera, connected services) are only loaded when relevant. Each schema is precisely written — vague descriptions cause the model to guess wrong parameters.

### Model Behavior Edge Cases

| Edge Case | Handling |
|-----------|----------|
| **Model refuses** (content policy) | Detect refusal, inform user gracefully |
| **Model hallucinates tool name** | Validate against tool registry, return error listing available tools |
| **Model returns text AND tool calls** | Handle both — render text, execute tools |
| **Model enters infinite tool loop** | Loop detector → inject system message → hard cap |
| **Model produces partial/malformed response** | Defensive parsing, retry on failure |

### Network Reliability on Mobile

Mobile networks are unreliable. The client handles:
- **Wi-Fi → cellular handoff:** Auto-retry from last complete message
- **Complete network loss (tunnel/elevator):** Queue the request, retry when connectivity returns
- **Slow 3G:** Long timeouts (120s), show "thinking..." indicator
- **Airplane mode:** Immediate error, offer to queue for later

### API Key Security

The user's API key is stored with `EncryptedSharedPreferences` (AES-256-GCM). It is:
- Never logged
- Never included in crash reports
- Never sent to any server except the provider's API endpoint
- Masked in the UI (`sk-...4xJ2`)

### Communication Flow

```
User message
    │
    ▼
Agent Engine (Kotlin)
    │
    ├──► Check steering queue → inject if present
    │
    ├──► AI Provider API (streaming SSE)
    │       │
    │       ▼
    │    Accumulate response chunks
    │    (text → render live, tool calls → accumulate JSON fragments)
    │       │
    │       ▼
    │    Response complete
    │       │
    │       ├── TEXT → Render to UI, auto-collapse activity group, DONE
    │       │
    │       └── TOOL_CALLS → Execute via ProcessManager
    │            │
    │            ├──► execute_command ──► PTY (script -q -c) ──► bash
    │            │    (non-blocking, timeout, output ring buffer)
    │            │
    │            ├──► start_process ──► PTY ──► bash (background)
    │            │    (returns immediately, agent checks later)
    │            │
    │            ├──► send_input ──► write to process stdin
    │            │    (supports [ENTER], [UP], [CTRL+C], etc.)
    │            │
    │            ├──► read_file / write_file / edit_file
    │            │    (never interactive editors — always tool-based)
    │            │
    │            ├──► browse_web ──► WebView + JS extraction
    │            │
    │            ├──► use_service ──► OAuth API call
    │            │    (approval via Material 3 bottom sheet)
    │            │
    │            └──► Collect all results → check STOP
    │                 → feed results back to LLM (loop)
    │
    ▼
Response streamed to UI + activity steps logged
All tool activity grouped into collapsible step group
```

---

## Design Language

### Visual Identity

The UI draws inspiration from **Anthropic's Claude app** — clean, conversational, content-first — combined with **Codex's collapsible activity steps** for agent transparency. Navigation uses a **sidebar** like Claude (projects + history), and the input bar supports **Telegram-style `/` commands** for quick access to skills and actions.

- **Color palette:** Clean, minimal base. Light/dark mode following system theme. Accent color is intentional — not generic blue. Think muted purples, teals, or the user's Material You dynamic color.
- **Typography:** Inter or system default. Clean, modern, readable. Monospace (JetBrains Mono or Fira Code) for code and terminal output.
- **Cards:** Subtle elevation for activity steps. Clean borders, not heavy shadows. No overdone glassmorphism.
- **Spacing:** Generous. Let the content breathe. Don't cram.
- **Icons:** Material Symbols (rounded, filled). Consistent weight.

### Motion

- **Transitions:** Shared element transitions between screens. Nothing jarring.
- **Loading:** Skeleton shimmer, not spinners. Pulsing agent avatar while thinking.
- **Activity steps:** Smooth expand/collapse animations with spring physics.
- **Notifications:** Slide in from top, fade out. Not intrusive.

### The Activity Step System

This replaces the traditional "terminal window" concept. Instead of an always-visible terminal, agent actions are logged as **inline collapsible steps** within the conversation — exactly like Codex.

Steps use a **two-level collapsible structure.** All tool calls, web requests, and commands that happen between two agent text responses are grouped under a **single parent group collapse.** Expanding the group reveals the individual steps, each of which can be expanded further to see full details.

**Level 1 — Group collapse** (all activity between two text outputs):
```
▶ 📂 Explored 4 files  ·  ⚙️ Ran 2 commands  ·  🌐 Visited 1 page    58s
```

**Level 2 — Expanded group** (shows individual steps, each still collapsed):
```
▼ 📂 Explored 4 files  ·  ⚙️ Ran 2 commands  ·  🌐 Visited 1 page    58s
│
│  ▶ 📂 Explored /workspace/src/                              0.2s
│  ▶ ⚙️ Ran "apt install ffmpeg"                               12s
│  ▶ 🌐 Visited docs.ffmpeg.org                                 3s
│  ▶ ⚙️ Ran "ffmpeg -i input.mp4 -vf scale=-2:720 output.mp4"  45s
```

**Level 3 — Expanded individual step** (full command + output):
```
│  ▼ ⚙️ Ran "ffmpeg -i input.mp4 -vf scale=-2:720 output.mp4"  45s
│  ┌───────────────────────────────────────────────────────┐
│  │ $ ffmpeg -i input.mp4 -vf scale=-2:720 -c:v libx264  │
│  │   -crf 23 output.mp4                                  │
│  │ Input #0, mov,mp4, from 'input.mp4':                  │
│  │   Duration: 00:05:23.12, bitrate: 3842 kb/s           │
│  │ Stream mapping: Stream #0:0 -> #0:0 (h264 -> libx264) │
│  │ frame= 7720 fps=52 q=28.0 size=  28416kB              │
│  │ [exit code: 0]                                        │
│  └───────────────────────────────────────────────────────┘
```

**While the agent is actively running**, the current group stays expanded and the currently executing step streams output live. When the step completes, it auto-collapses. When the agent finishes and produces a text response, the entire group auto-collapses into the Level 1 summary.

**Key rules:**
- Expanded steps have a **fixed max height** (e.g., 200dp). Content scrolls within. A step never takes over the entire screen.
- Currently running steps are **auto-expanded** with live streaming output.
- Completed steps **auto-collapse** when done. Completed groups **auto-collapse** when the agent produces its next text response.
- Step types:
  - `⚙️` Command execution (shows command + output)
  - `📂` File exploration (shows files read/listed)
  - `🌐` Web browsing (shows URLs visited + extracted content)
  - `📝` File creation/edit (shows file path + diff preview)
  - `🔌` Service API call (shows service + endpoint)
  - `📦` Package install (shows package + status)

### The Input Bar

The input bar follows the Codex pattern:

**Idle state:**
```
┌─────────────────────────────────────────┐
│  Type a message...     [📎] [🎤] [➤]   │
└─────────────────────────────────────────┘
```

**Agent running:**
```
┌─────────────────────────────────────────┐
│  Steer the agent...           [⏹ Stop] │
└─────────────────────────────────────────┘
```

- While the agent is working, the send button becomes **⏹ Stop** (abort). Tapping it halts everything **immediately** — kills the current command, terminates all processes, cancels the LLM stream. The user can then type what to do differently.
- The user can also type in the input field **without stopping** to steer the agent. The message is queued and injected into context after the current tool call finishes, before the next model turn. This lets the user course-correct without interrupting work in progress.
- Typing `/` opens a **Telegram-style command menu** above the input showing available commands and skills.

### The Sidebar

```
┌────────────────────────┐
│  🐙 ClawDroid          │
│                         │
│  ── Quick Actions ───  │
│  ⏰ Automations         │
│  🔌 Connected Services  │
│  ⚙️ Settings             │
│                         │
│  ── Chats ───────  [+] │
│  💬 "Compress video"   │
│  💬 "Parse CSV data"   │
│  💬 "Quick question"   │
│  💬 "Rename files"     │
│  Show more...           │
│                         │
│  ── Projects ────  [+] │
│  📁 Video Tools        │
│  │  [+ New Thread]     │
│  │  💬 "Compress wed.."│
│  │  💬 "Extract audio" │
│  📁 Web App            │
│  📁 Research           │
│  📁 Daily Tasks        │
│  Show more...           │
└────────────────────────┘
```

Key sidebar behavior:
- **Quick Actions** are always at the top — one-tap access to automations, connected services, settings
- **Chats** are standalone conversations not tied to any project — quick one-off tasks. `[+]` button on the section header starts a new chat. Shows the **4 most recent** with a "Show more..." to expand.
- **Projects** group related work; tapping a project name expands it to reveal its chat threads underneath, with `[+ New Thread]` at the top. `[+]` on the section header creates a new project. Shows the **4 most recent** with "Show more..." to expand.
- Everything is sorted by most recently used within its section

### Notification Design

Notifications are **actionable and informative**, not just pings:

```
┌──────────────────────────────────────┐
│ 🐙 ClawDroid                  2m ago │
│                                      │
│ ✅ Video compressed                  │
│ 154MB → 28MB (82% smaller)          │
│                                      │
│ [📹 View] [📤 Share] [📂 Open Folder]│
└──────────────────────────────────────┘
```

```
┌──────────────────────────────────────┐
│ 🐙 ClawDroid                  just now│
│                                      │
│ ⚠️ Need your input                   │
│ "Found 3 matching flights. Cheapest  │
│  is ₹12,400 but has a 4hr layover.  │
│  Direct flight is ₹18,200."         │
│                                      │
│ [Open Task] [Reply]                  │
└──────────────────────────────────────┘
```

```
┌──────────────────────────────────────┐
│ 🐙 ClawDroid                  5s ago  │
│                                      │
│ ⏰ Scheduled: Morning Briefing       │
│ "Checked HN, 3 new PRs on your      │
│  repos, 2 calendar events today."    │
│                                      │
│ [Read Briefing] [Dismiss]            │
└──────────────────────────────────────┘
```

---

## What Success Looks Like

### For the User
- Open app → tell agent what to do → see it work in collapsible steps → get result
- Never feel confused about what the agent is doing
- Never feel like they've lost control
- Trust the agent enough to fire-and-forget tasks
- Navigate easily between projects and agents
- Show it to a friend and they immediately want it

### For the Hackathon Demo
- Someone in the audience should think "I want this on my phone right now"
- The demo should show: task creation → agent working (collapsible activity steps) → result
- Multiple agents running in the same project
- At least one "wow" moment (camera scan, voice command, or live deployment)
- The UI should look like it was built by a design team, not hacked together

### For the Code
- Clean Kotlin with clear separation of concerns
- No god classes, no 1000-line files
- Every component testable in isolation
- README that a new contributor can follow
- Architecture that could scale to production

---

## MVP Decisions

These decisions were locked in during design review and define the scope of the hackathon build:

| Decision | Choice |
|---|---|
| **App name** | ClawDroid |
| **Package** | `com.clawdroid.app` |
| **Min API** | 31 (Android 12) |
| **First launch** | Clean onboarding → Pick provider → API key → Bootstrap downloads → First chat |
| **AI provider** | OpenAI-compatible endpoint only (covers OpenAI, Groq, Together, Ollama) |
| **Linux bootstrap** | Termux official bootstrap tarballs |
| **Agent personality** | Configurable — 3 presets (Professional, Friendly, Minimal) |
| **File access** | Shared folder (`Documents/ClawDroid/`), 📎 button + Android Share Intent |
| **Browser** | WebView + JS extraction, bottom sheet preview, DuckDuckGo/Brave for search |
| **Voice/Camera** | Post-MVP |
| **Connected Services** | Post-MVP |
| **Automations** | Simple cron scheduler in Room DB, WorkManager triggers |
| **Theming** | Material You dynamic colors + muted teal/purple fallback |
| **Background** | Foreground Service with persistent notification |
| **Database** | Room DB for everything, EncryptedSharedPreferences for secrets |
| **Architecture** | MVVM + Hilt DI, single-activity, Navigation Compose |
| **HTTP client** | OkHttp + manual SSE parsing, Retrofit for non-streaming |
| **Markdown** | Markwon wrapped in AndroidView |
| **Token estimation** | Character-based (~4 chars/token), compact at 80% limit |
| **Agent avatar** | Octopus 🐙 |
| **Memory** | Agent writes `~/.memory/memory.md`, reads on new conversation start |

**Build priority order:**
1. Bootstrap + Shell (get bash running)
2. Agent Engine + API Client (streaming, tool loop)
3. ProcessManager + Tools (execute_command, start_process, send_input)
4. Chat UI + Activity Steps (collapsible steps, markdown)
5. File tools + Shared folder
6. Projects / Sidebar
7. Web browsing
8. Automations
9. Polish (notifications, onboarding, theming)

---

## Non-Goals (For This Hackathon)

Things we explicitly **won't** build this week:

- ❌ Voice input / TTS (post-MVP)
- ❌ Camera / Vision (post-MVP)
- ❌ Connected Services / OAuth (post-MVP)
- ❌ Native Anthropic / Google API clients (post-MVP — use OpenAI-compatible)
- ❌ Local LLM inference (separate project entirely)
- ❌ Root access or system-level modifications
- ❌ App store compliance (this is a demo, sideloaded is fine)
- ❌ Multi-user / cloud sync
- ❌ Plugin marketplace
- ❌ End-to-end encryption for conversations
- ❌ iOS version

---

## The One-Line Pitch

**"An AI agent on your phone with its own Linux terminal, browser, and files — you see everything it does, and it figures out the rest."**

---

## Current Agent Skills Update

### TUI via INTERPOLE

When a task requires interacting with a terminal UI program on the paired desktop, use the tmux-backed INTERPOLE terminal tools:

1. Create a session with `interpole_terminal_create` using a short name, desktop cwd, and optional command such as `lazygit` or `nvim main.py`.
2. Send keys with `interpole_terminal_send`; use `enter=false` for navigation keys such as `j`, `k`, `q`, `[UP]`, `[DOWN]`, or `[ESC]`.
3. Read the visible terminal buffer with `interpole_terminal_read`.
4. Resize with `interpole_terminal_resize` when layouts are cramped.
5. Kill the session with `interpole_terminal_kill` when finished.

### Layered Memory

At session start, always include the compact skill index from `home/.skills/INDEX.md`. Load detailed skill files and long-term memory from `home/.memory/` only when relevant to the current task.

### Proactive Questions

Use `agent_ask` to create a proactive user question. When the user responds to an `answer_question:<id>` notification flow, call `agent_answer` with the question id and the user's answer so the pending question is closed out.
