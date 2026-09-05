# ClawDroid

ClawDroid is a native Android AI agent app built with Kotlin, Jetpack Compose, and Material 3. It brings a transparent autonomous assistant to Android: chat with it, let it run tools, inspect its activity trail, connect services, control the phone UI when permitted, and keep user-editable agent workspace files inside the app.

The app is designed around one idea: the agent can be capable without being a black box. Tool calls, terminal work, service actions, and automation state are surfaced in the conversation UI so the user can see what happened and intervene when needed.

## Current Capabilities

- Streaming OpenAI-compatible chat client with tool calling.
- Provider-aware AI settings for OpenAI-compatible endpoints, OpenRouter, DeepSeek, Gemini discovery, Anthropic discovery, OpenCode, and custom endpoints.
- Native Android screen control tools through an `AccessibilityService`.
- MediaProjection screenshot fallback for screen-reading gaps.
- Terminal/process tooling for command execution, background process tracking, file reads/writes, and web utilities.
- MCP client and launcher for stdio-based external tool servers.
- OAuth/service integrations for Google, GitHub, Notion, and Spotify.
- Channels scaffolding for WhatsApp, SMS, notification listener flows, and approval-based replies.
- Workspace files inspired by OpenClaw: `AGENTS.md`, `SOUL.md`, `TOOLS.md`, `USER.md`, `IDENTITY.md`, and `HEARTBEAT.md`.
- Skills screen with seeded prompt-only starter skills, enable/disable, edit, reset, and create flows.
- Voice stack with Android TTS fallback and optional Sherpa-ONNX offline VITS TTS model download.
- Thinking-state quote loader while the assistant is processing before visible text appears.
- Settings screens for AI providers, voice, Android control, permissions, channels, skills, workspace files, and app details.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- WorkManager
- OkHttp/SSE-style streaming
- Android AccessibilityService
- Android MediaProjection
- Sherpa-ONNX native runtime for offline TTS
- Gradle Kotlin DSL

## Repository Layout

```text
app/src/main/java/com/clawdroid/app/
+-- core/
|   +-- automation/      # WorkManager scheduling and boot restart
|   +-- bootstrap/       # Embedded Linux/bootstrap helpers
|   +-- channels/        # WhatsApp/SMS/notification channel layer
|   +-- config/          # SharedPreferences-backed app config
|   +-- control/         # Accessibility and screen-capture control tools
|   +-- engine/          # Agent loop, tool executor, MCP, compaction, loop detection
|   +-- service/         # OAuth managers and foreground service helpers
|   +-- skills/          # Built-in/default skill loading and prompt skills
|   +-- terminal/        # Process execution and terminal abstractions
|   +-- tools/           # Native tool implementations exposed to the LLM
|   +-- voice/           # TTS engines, offline model download, voice manager
|   +-- workspace/       # Agent workspace markdown file manager
+-- data/
|   +-- api/             # LLM client, provider discovery, message/tool schema building
|   +-- db/              # Room entities, DAOs, database
+-- ui/
    +-- chat/            # Main conversation UI
    +-- components/      # Shared Compose components and loaders
    +-- settings/        # Settings, MCP, skills, channels, workspace screens
    +-- setup/           # Onboarding/setup UI
    +-- sidebar/         # Drawer/sidebar UI
    +-- splash/          # Splash screen
    +-- theme/           # Compose theme
    +-- voice/           # Voice overlay/input UI
```

## Requirements

- JDK 21
- Android SDK with compile SDK 36 installed
- Android device or emulator running Android 8.0+ (API 26+)
- ADB for install/logging workflows

The app currently uses:

```kotlin
minSdk = 26
targetSdk = 28
compileSdk = 36
```

The lower target SDK is intentional for the embedded Linux/runtime direction. It is suitable for sideloaded builds, but it is not aligned with modern Play Store target SDK requirements.

## Configuration

Build-time defaults can be provided through `local.properties` or `.env`:

```properties
LLM_PROVIDER=openai
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4o-mini
LLM_API_KEY=sk-...
OPENAI_REALTIME_API_KEY=sk-...

GITHUB_OAUTH_CLIENT_ID=...
GITHUB_OAUTH_CLIENT_SECRET=...
GITHUB_OAUTH_TOKEN=...

NOTION_OAUTH_CLIENT_ID=...
NOTION_OAUTH_CLIENT_SECRET=...

SPOTIFY_CLIENT_ID=...
SPOTIFY_CLIENT_SECRET=...
```

Runtime settings can also be edited inside the app. API keys are currently persisted via app preferences, so do not commit local config files or screenshots containing secrets.

## Build

Compile Kotlin:

```bash
./gradlew compileDebugKotlin
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

By default, the APK packages the `arm64-v8a` Sherpa native library to keep the sideload APK smaller for real phones. Add emulator ABIs in `app/build.gradle.kts` only when building emulator-specific APKs.

## Android Permissions And Setup

Some capabilities require explicit Android permissions or user actions:

- Accessibility service: required for UI tree reading, taps, swipes, text entry, and global navigation actions.
- Screen capture: optional MediaProjection fallback for screenshots when the accessibility tree is empty.
- Microphone: required for voice input.
- Notifications and notification listener: required for channel/approval flows.
- Overlay: used for floating controls where enabled.
- Shared folder access: used for importing/exporting files through `Documents/ClawDroid`.
- SMS permissions: needed only if SMS channel features are enabled.

These are managed from the in-app Settings and Android system settings.

## AI Provider Notes

The app supports provider presets and model discovery:

- OpenAI-compatible endpoints
- OpenRouter
- DeepSeek
- Gemini model discovery
- Anthropic model discovery
- OpenCode/OpenCode Zen style custom endpoints
- Custom OpenAI-compatible URLs

OpenAI-compatible providers are the primary runtime path. Providers with native request formats need matching runtime adapters before they can be used for full chat execution.

## Offline TTS Notes

Android system TTS is the guaranteed fallback. The optional offline neural path uses Sherpa-ONNX native libraries and downloads model files into app-internal storage.

The bundled native library currently supports the VITS path used by the default `vits-piper-en_US-glados` preset. Unsupported model families should not be selected unless the native Sherpa library is upgraded to a build that supports them.

## Agent Workspace And Skills

Workspace markdown files are stored in app-internal agent home storage and injected into prompt assembly:

- `AGENTS.md`: operational rules and memory protocol
- `SOUL.md`: personality, tone, and boundaries
- `TOOLS.md`: tool usage notes
- `USER.md`: user profile/preferences
- `IDENTITY.md`: agent display identity
- `HEARTBEAT.md`: background task checklist

Skills are prompt-only markdown by default. Starter skills are seeded on first launch and can be disabled, edited, reset, or extended from the Skills screen.

## Verification Checklist

Before pushing app changes, run:

```bash
./gradlew compileDebugKotlin
./gradlew assembleDebug
```

Useful device checks:

```bash
adb devices -l
adb logcat -d -v time | rg -i "com\\.clawdroid\\.app|FATAL EXCEPTION|AndroidRuntime|Sherpa|OfflineTts|SIGSEGV|crash"
```

## Known Limitations

- Native Gemini and Anthropic chat runtime adapters are still separate work from model discovery.
- Root-only Android capabilities are out of scope.
- MediaProjection consent is session-based and must be granted by the user.
- Local LLM inference is not implemented.
- End-to-end encrypted settings storage is planned but not fully implemented.
- The embedded Linux/bootstrap direction relies on sideload-friendly Android behavior and is not Play Store oriented.

## Project Docs

- Product and architecture direction: `AGENTS.md`
- Current project context: `context.md`
- Development plan/history notes: `implementation_plan.md`
