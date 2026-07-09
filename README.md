# ClawDroid

![ClawDroid banner](docs/assets/clawdroid-banner.png)

**A native Android AI agent that can chat, think, run Linux commands, control Android apps with permission, connect services, and show every action it takes.**

Built by **TEAM POLYMATH** for the Codex Community Hackathon.

Creators: **Nabil**, **Paris**, **Rushi**, and **Talib**.

Website: [clawdriod.pages.dev](https://clawdriod.pages.dev)

---

## What Is ClawDroid?

ClawDroid is an Android AI agent built like a real native app, not a web wrapper. It gives you a transparent assistant that can work inside chat, run commands in a sandboxed Linux environment, operate supported Android UI flows, connect to external services, and keep you in control through visible activity steps and a floating out-of-app widget.

The core idea is simple:

> Give the agent real capabilities, but never hide what it is doing.

When ClawDroid runs a task, you can see commands, screen actions, file edits, service calls, notifications, and progress. Outside the app, the floating widget stays visible, shows live action history like screen capture, clicks, typing, waits, and app launches, then gets out of the way so the phone remains usable.

![ClawDroid showcase](docs/assets/clawdroid-showcase.png)

---

## Why It Is Different

- **No ADB hacks.** ClawDroid does not rely on ADB shell tricks to control your phone.
- **Native Android.** Kotlin, Jetpack Compose, Material 3, foreground services, notifications, accessibility, and Android system APIs.
- **Transparent agent activity.** Tool calls and actions are shown as collapsible steps in chat.
- **Out-of-app control widget.** A draggable themed overlay shows what the agent is doing while it works in other apps.
- **Linux sandbox.** The agent has a terminal-style workspace for commands, scripts, packages, files, and automation.
- **Human in control.** Stop, steer, approve, inspect, or continue from the widget or chat.
- **Provider flexible.** Supports OpenAI-compatible providers plus native Anthropic Messages API streaming.
- **Designed for power users.** Terminal, MCP, connectors, prompt files, permissions, themes, and agent behavior settings.

---

## How It Works

ClawDroid combines a few Android-native systems:

1. **Agent engine**
   - Streams model responses.
   - Executes tools.
   - Tracks actions.
   - Saves conversation and activity history.
   - Handles stop, steering, notifications, loop checks, and title generation.

2. **Android screen control**
   - Uses Android Accessibility APIs when the user explicitly enables them.
   - Uses screen capture only with user-granted permission.
   - Performs taps, typing, scrolling, back/home actions, and UI inspection through Android permission boundaries.

3. **Floating widget**
   - Runs as a small overlay window, not a full-screen touch blocker.
   - Shows live action history: capture, click, type, scroll, wait, open app, send message, command execution.
   - Follows the selected theme, including Liquid Glass light/dark.

4. **Linux-style workspace**
   - Runs terminal commands and background processes.
   - Reads/writes files.
   - Keeps project memory and markdown config files.
   - Supports task automation and connected services.

5. **Provider layer**
   - OpenAI-compatible chat completions for OpenAI, OpenRouter, Gemini-compatible endpoint, Groq, Mistral, DeepSeek, xAI, Together, Ollama, SiliconFlow, and custom endpoints.
   - Native Anthropic Messages API streaming adapter.

ADB is useful for developers installing and reading logs, but it is **not** part of the runtime control system.

---

## Features

### Agent Chat

- Streaming responses.
- Markdown rendering.
- Better response animation.
- Thinking/status messages.
- Auto-generated chat subjects after tasks.
- Collapsible action steps for tools, commands, files, and services.

### Out-Of-App Assistant

- Draggable widget with ClawDroid logo.
- Live one-line status.
- Action timeline for what the agent is executing.
- Follow-up input after task completion.
- Stop/close controls.
- Theme-aware appearance.

### Terminal

- Interactive terminal UI.
- Quick commands.
- Command history.
- Restart shell, clear, stop, Ctrl+C/Ctrl+D support.
- Linux command execution inside the app sandbox.

### Providers

Preset support includes:

- Anthropic
- OpenAI
- Google Gemini OpenAI-compatible endpoint
- OpenRouter
- SiliconFlow
- Groq
- Mistral AI
- DeepSeek
- xAI
- Together AI
- Ollama
- Custom OpenAI-compatible endpoint

### Settings

- Provider configuration.
- Agent identity and behavior.
- Permission manager.
- Audio/TTS configuration.
- MCP and connections.
- Channels and automations.
- Prompt files: `AGENTS.md`, `SOUL.md`, `TOOLS.md`, `SKILL.md`, `SYSTEM.md`.
- Themes: Light, Dark, Minimalist, Liquid Glass Light, Liquid Glass Dark.

### First Wake

After onboarding, the agent hatches, introduces itself, asks about the user, and generates its memory/config markdown files from the user response.

---

## Project Structure

```text
app/src/main/java/com/clawdroid/app/
├── core/
│   ├── assistant/       # Overlay, invocation, permissions, Android assistant mode
│   ├── automation/      # Background and heartbeat scheduling
│   ├── channels/        # WhatsApp/SMS/notification channel layer
│   ├── config/          # App configuration
│   ├── control/         # Accessibility and screen-control tools
│   ├── engine/          # Agent loop, tool execution, process handling
│   ├── notifications/   # Smart task notifications
│   ├── service/         # OAuth/service helpers
│   ├── terminal/        # Process and terminal management
│   └── voice/           # Android TTS and voice pipeline
├── data/
│   ├── api/             # LLM client, provider adapters, tool schema
│   └── db/              # Room database
└── ui/
    ├── chat/            # Main chat experience
    ├── components/      # Shared Compose components
    ├── settings/        # Android-style settings screens
    ├── setup/           # Onboarding and first wake
    ├── splash/          # Hatch/splash screens
    ├── terminal/        # Terminal UI
    └── theme/           # Material 3 themes
```

---

## Build Requirements

- JDK 21
- Android SDK with compile SDK 36
- Android 8.0+ device or emulator
- Gradle wrapper from this repo

The app currently uses:

```kotlin
minSdk = 26
targetSdk = 28
compileSdk = 36
```

`targetSdk` is intentionally lower than `compileSdk` for the current overlay/accessibility behavior. Revisit it during Android compatibility hardening before Play Store distribution.

---

## Build

Compile:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
```

Build debug APK:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug
```

Build release APK:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease
```

If `signing/release-signing.properties` exists, `assembleRelease` produces a signed release APK. That file and the keystore are ignored by git.

Expected local signing file:

```properties
storeFile=signing/clawdroid-release.jks
storePassword=...
keyAlias=clawdroid-release
keyPassword=...
```

Keep `signing/` backed up and never commit it. Losing the release keystore means future APK updates cannot use the same signature.

GitHub release builds require these repository secrets:

```text
RELEASE_KEYSTORE_BASE64
RELEASE_KEYSTORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Push a tag like `v0.1.0` after secrets are configured to let CI build and publish the signed APK.

---

## Runtime Permissions

ClawDroid asks for only the Android capabilities it needs:

- Accessibility service for screen reading and UI actions.
- Overlay permission for the floating assistant widget.
- Notification permission for smart task completion updates.
- Microphone permission for voice input.
- Storage/document access for user-visible files.
- Screen capture permission when screenshot fallback is required.

All phone control happens through Android permissions and user-enabled services. No hidden ADB bridge is required.

---

## Contributing

We welcome contributions from builders who care about native Android, agent transparency, automation, UX, and safe tool execution.

Contribution flow:

1. Create an issue for the bug, feature, update, or design improvement.
2. Create a branch for that issue/update.
3. Make the change with focused commits.
4. Run the verification commands.
5. Open a pull request.
6. TEAM POLYMATH will review it.

Recommended branch names:

```text
fix/overlay-touch-pass-through
feature/provider-settings
update/readme-assets
ui/liquid-glass-theme
```

Before opening a PR:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug
```

---

## Team

**TEAM POLYMATH**

- Nabil
- Paris
- Rushi
- Talib

Built for people who want an agent that can actually do things on Android, without turning into a black box.
