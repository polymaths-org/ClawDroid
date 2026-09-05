# ClawDroid Gemini-Style Assistant Service Implementation Plan

This document is a handoff plan for implementing a Gemini-like system assistant experience in ClawDroid while preserving the project's stronger differentiator: MCP tools, the embedded Termux/Linux sandbox, transparent activity steps, and autonomous background work.

The goal is not to clone Gemini branding. The goal is to make ClawDroid feel like a native Android assistant that can be invoked anywhere, understand the current screen, accept voice naturally, let the user circle/doodle over content, search or act on that context, and keep useful agent tasks running in the background.

## Product Target

When complete, the user should be able to:

- Set ClawDroid as the phone's default assistant from Android settings or an in-app setup prompt.
- Invoke ClawDroid from the system assistant gesture/button/long press wherever Android allows.
- See a lightweight assistant overlay above the current app instead of being forced into the main chat.
- Ask about what is on screen using system assist context, screenshot access, and fallback capture flows.
- Ask ClawDroid to operate the current Android UI when Screen Control is enabled: tap buttons, type text, scroll, launch apps, open notifications, and verify every step from the live screen state.
- Doodle/circle/highlight a region on screen, then ask ClawDroid to explain it, search it, summarize it, OCR it, save it, or run an agent task from it.
- Use realtime voice with barge-in, low latency, transcripts, and tool execution.
- Continue tasks in the background through a foreground service, WorkManager, notifications, and the existing MCP/Termux tool stack.
- Get a clean permission/setup prompt exactly when a capability is needed, with clear recovery if the user skips or denies it.
- Inspect every meaningful action later as normal ClawDroid activity steps.

## Hard Android Truths

Do not design this as if an app can silently become Gemini. Android deliberately gates these capabilities:

- Default assistant selection is user controlled. We can request the assistant role and deep-link to default assistant settings, but we cannot silently replace the user's assistant.
- The proper default assistant path is `VoiceInteractionService` plus the Assistant role. Android's `RoleManager.ROLE_ASSISTANT` exists for the assistant app role, and `VoiceInteractionService` is the service class associated with that role.
- A voice interaction service must declare the `android.service.voice.VoiceInteractionService` action and require `android.permission.BIND_VOICE_INTERACTION` so other apps cannot bind to it.
- Assist context/screenshot access is controlled by system settings and runtime gates. Users can configure whether the assistant may access screen text and screenshot.
- Newer Android APIs expose `VoiceInteractionManager` methods for current default assistants to request/read screen context, but these are API-gated and should be wrapped behind a compatibility facade.
- Drawing over other apps requires `SYSTEM_ALERT_WINDOW` and user-granted overlay access.
- Full screen capture through MediaProjection requires explicit user consent and, on modern target SDKs, the correct foreground service type.
- Accessibility automation can perform gestures only after the user enables an accessibility service configured for gesture dispatch.
- Accessibility-based control is powerful enough to affect real apps. Treat it as a high-trust mode: read/gesture/type only after the user has enabled ClawDroid Screen Control, verify actions with `get_screen`, and keep approval gates for irreversible connected-service actions.
- Always-on hotword/background microphone behavior is heavily restricted and should be treated as optional, privacy-sensitive, and device/OEM dependent. The MVP should use explicit invocation and push-to-talk/call mode first.

Official references:

- Android assistant UX and assist context settings: https://developer.android.com/training/articles/assistant
- `RoleManager.ROLE_ASSISTANT`: https://developer.android.com/reference/android/app/role/RoleManager#ROLE_ASSISTANT
- `VoiceInteractionService`: https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- `VoiceInteractionSession`: https://developer.android.com/reference/kotlin/android/service/voice/VoiceInteractionSession
- `VoiceInteractionManager` screen context APIs: https://developer.android.com/reference/kotlin/android/app/voiceinteraction/VoiceInteractionManager
- Assist APIs: https://developer.android.com/reference/android/app/assist/package-summary
- MediaProjection: https://developer.android.com/media/grow/media-projection
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Overlay permission behavior: https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)
- Accessibility service gestures: https://developer.android.com/guide/topics/ui/accessibility/service
- OpenAI Realtime overview: https://developers.openai.com/api/docs/guides/realtime
- OpenAI voice agents: https://developers.openai.com/api/docs/guides/voice-agents

## Existing Repo Anchors

Current useful pieces:

- `app/src/main/AndroidManifest.xml`
  - Already has `SYSTEM_ALERT_WINDOW`, `RECORD_AUDIO`, foreground service permissions, notification listener, boot receiver, foreground services, and the merged `ScreenReaderService` accessibility service declaration.
- `app/src/main/java/com/clawdroid/app/core/control/AndroidControlTools.kt`
  - Merged Android Screen Control tool facade. Exposes `get_screen`, `tap`, `tap_text`, `tap_resource_id`, `long_press`, `swipe`, `scroll`, `type_text`, `clear_text`, `press_back`, `press_home`, `press_recents`, `open_notifications`, `launch_app`, `get_installed_apps`, `screenshot`, and `wait`.
- `app/src/main/java/com/clawdroid/app/core/control/ScreenReaderService.kt`
  - Accessibility service that serializes the live UI tree, dispatches gestures, types into editable fields, performs global actions, and launches apps.
- `app/src/main/java/com/clawdroid/app/core/control/ScreenCaptureManager.kt`
  - MediaProjection-backed screen capture manager used as the vision fallback when the accessibility tree is empty or unhelpful.
- `app/src/main/res/xml/accessibility_service_config.xml`
  - Accessibility configuration with `canRetrieveWindowContent` and `canPerformGestures` enabled.
- `app/src/main/java/com/clawdroid/app/core/engine/AgentEngine.kt`
  - Main streaming agent loop, steering queue, tool calls, compaction, MCP startup.
- `app/src/main/java/com/clawdroid/app/core/engine/McpClient.kt`
  - JSON-RPC MCP subprocess client.
- `app/src/main/java/com/clawdroid/app/core/engine/McpServerLauncher.kt`
  - Starts configured MCP servers and injects their tools.
- `app/src/main/java/com/clawdroid/app/core/terminal/ProcessManager.kt`
  - Command/process runtime for Termux-style tools.
- `app/src/main/java/com/clawdroid/app/core/notifications/AgentForegroundService.kt`
  - Lightweight background foreground service.
- `app/src/main/java/com/clawdroid/app/core/service/EnhancedForegroundService.kt`
  - Existing background agent service wrapper.
- `app/src/main/java/com/clawdroid/app/core/voice/SpeechRecognizerClient.kt`
  - Existing Android speech recognizer path.
- `app/src/main/java/com/clawdroid/app/core/voice/VoiceManager.kt`
  - Existing TTS engine selector.
- `app/src/main/java/com/clawdroid/app/core/voice/OpenAIRealtimeClient.kt`
  - Currently creates OpenAI Realtime client secrets, but does not yet implement actual audio transport.
- `app/src/main/java/com/clawdroid/app/ui/chat/ChatScreen.kt`
  - Existing chat and voice UI integration point.
- `app/src/main/java/com/clawdroid/app/core/config/AppConfigManager.kt`
  - Settings storage, including realtime voice and ultra agent toggles.
- `app/src/main/java/com/clawdroid/app/data/api/MessageBuilder.kt`
  - Already injects Android Screen Control instructions into the system prompt when `ScreenReaderService.instance` is active.
- `app/src/main/java/com/clawdroid/app/data/api/ToolSchemaRegistry.kt`
  - Already registers the merged screen-control tools alongside shell, MCP, web, notification, and connected-service tools.
- `app/src/main/java/com/clawdroid/app/core/engine/ToolExecutor.kt`
  - Already routes merged Android Control tool calls to `AndroidControlTools`.
- `app/src/main/java/com/clawdroid/app/ui/settings/SettingsScreen.kt`
  - Already contains Android Control settings/status flow, including Accessibility settings launch and MediaProjection capture start/stop hooks.

Pre-release security cleanup:

- Remove hard-coded development API keys/client secrets from config defaults and local JSON files before any public build.
- Move all provider keys to user setup, encrypted storage, or local-only debug configuration.
- Never send screen captures, notification contents, SMS, or other app content to a model without a visible user action or an explicitly enabled mode.

## High-Level Architecture

Add a new assistant surface around the existing agent engine:

```text
System invocation
  -> ClawVoiceInteractionService
  -> ClawVoiceInteractionSessionService
  -> ClawVoiceInteractionSession
  -> AssistantOverlayCoordinator
  -> AssistantInvocationRouter
  -> AgentEngine + AndroidControlTools + MCP + Termux tools
  -> Activity-step persistence + notifications
```

Core principle: the assistant overlay is another entrypoint into the same agent system. Do not fork a separate "assistant brain" that bypasses projects, memory, MCP tools, cost tracking, approvals, or activity logs.

New principle after merge `be44e94`: do not rebuild Android UI control in the assistant package. The merged `core/control` stack is the canonical phone-control backend. Assistant work should route through it and add consent, context packaging, UI affordances, and policy.

## New Packages

Create these packages:

- `com.clawdroid.app.core.assistant`
- `com.clawdroid.app.core.assistant.context`
- `com.clawdroid.app.core.assistant.overlay`
- `com.clawdroid.app.core.assistant.doodle`
- `com.clawdroid.app.core.assistant.voice`
- `com.clawdroid.app.core.assistant.permissions`

Suggested files:

- `core/assistant/ClawVoiceInteractionService.kt`
- `core/assistant/ClawVoiceInteractionSessionService.kt`
- `core/assistant/ClawVoiceInteractionSession.kt`
- `core/assistant/AssistantInvocationRouter.kt`
- `core/assistant/AssistantInvocation.kt`
- `core/assistant/AssistantMode.kt`
- `core/assistant/context/AssistantContextSnapshot.kt`
- `core/assistant/context/AssistStructureExtractor.kt`
- `core/assistant/context/ScreenContextManager.kt`
- `core/assistant/context/ScreenshotSource.kt`
- `core/assistant/overlay/AssistantOverlayCoordinator.kt`
- `core/assistant/overlay/OverlayWindowService.kt`
- `core/assistant/overlay/AssistantOverlayView.kt`
- `core/assistant/doodle/DoodleOverlayController.kt`
- `core/assistant/doodle/DoodleSelection.kt`
- `core/assistant/doodle/RegionCropper.kt`
- `core/assistant/voice/RealtimeVoiceSession.kt`
- `core/assistant/voice/VoiceTransport.kt`
- `core/assistant/voice/OpenAIRealtimeVoiceTransport.kt`
- `core/assistant/voice/ChainedVoicePipeline.kt`
- `core/assistant/permissions/AssistantPermissionCoordinator.kt`
- `core/assistant/permissions/PermissionRequirement.kt`
- `core/assistant/permissions/PermissionRecoveryAction.kt`
- `core/assistant/permissions/PermissionAwareToolExecutor.kt`
- `core/assistant/context/AndroidControlContextBridge.kt`
- `core/assistant/policy/AndroidActionPolicy.kt`

Reuse these existing packages:

- `com.clawdroid.app.core.control`
  - Keep `AndroidControlTools`, `ScreenReaderService`, and `ScreenCaptureManager` as the implementation layer for screen reading, screenshots, gestures, app launching, typing, notifications shade, and global navigation.
- `com.clawdroid.app.core.engine`
  - Keep `ToolExecutor` as the single route for model tool calls.

## Permission UX Contract

Permissions and Android settings are part of the assistant experience, not edge-case errors. Any feature that depends on a permission or role must fail with a structured recovery path that the UI can render as a Material 3 bottom sheet or compact assistant overlay prompt.

Use this result shape for permission-blocked tool calls:

```kotlin
data class PermissionBlockedResult(
    val success: Boolean = false,
    val error: String = "permission_required",
    val capability: AssistantCapability,
    val title: String,
    val message: String,
    val recoveryAction: PermissionRecoveryAction,
    val retryableAfterGrant: Boolean = true,
)
```

`AssistantCapability` values:

- `DEFAULT_ASSISTANT`
- `SCREEN_CONTEXT`
- `SCREEN_CONTROL_ACCESSIBILITY`
- `SCREEN_CAPTURE`
- `OVERLAY`
- `MICROPHONE`
- `NOTIFICATIONS`
- `BACKGROUND_AGENT`
- `FILES_AND_MEDIA`
- `CONNECTED_SERVICE`
- `BATTERY_UNRESTRICTED`

`PermissionRecoveryAction` values:

- `REQUEST_RUNTIME_PERMISSION`
- `REQUEST_ROLE`
- `OPEN_ACCESSIBILITY_SETTINGS`
- `OPEN_OVERLAY_SETTINGS`
- `REQUEST_MEDIA_PROJECTION`
- `OPEN_NOTIFICATION_SETTINGS`
- `OPEN_BATTERY_SETTINGS`
- `OPEN_APP_SETTINGS`
- `CONNECT_SERVICE`
- `NONE`

Tool/error rules:

- Every permission-gated tool must return a structured `PermissionBlockedResult` instead of a vague exception.
- The agent should translate that result into a concise user-facing sentence and stop the current subtask until the user acts.
- The UI should show one primary action button, one secondary "Not now" action, and a plain explanation of what the permission allows.
- After the user grants a permission, automatically retry only the blocked read/setup operation. Do not automatically retry sensitive actions such as sending, deleting, purchasing, or submitting forms.
- If the user denies or backs out, keep the conversation state and offer a fallback path.
- Never ask for unrelated permissions together. Ask at the moment the feature needs it.

Fallback copy examples:

- Screen Control missing: "I need ClawDroid Screen Control to read and tap this app for you. Enable it in Accessibility, then I can continue from here."
- Screen Capture missing: "I can read some UI text, but visual screenshot access is off. Enable screen capture for visual questions, or I can answer from the visible text only."
- Overlay missing: "I need Draw over other apps to show the floating assistant here. Without it, I can continue in the main chat."
- Default assistant missing: "ClawDroid is not your default assistant yet. Set it as the assistant to invoke it from anywhere."
- Microphone missing: "I need microphone access for voice mode. You can still type your request."
- Background mode blocked: "Android is restricting background work. I can keep working while the app is open, or you can allow background activity for longer tasks."

## Capability Gates

Each assistant capability must declare its prerequisites in one place. `AssistantPermissionCoordinator` should own these checks so UI, tools, voice, and background tasks agree about the current state.

Capability matrix:

| Capability | Required gate | Fallback |
| --- | --- | --- |
| Default assistant invocation | Assistant role / default assistant setting | Launch main app or overlay bubble if allowed |
| Screen text/context | Assistant context setting or Accessibility service | Ask user to share screenshot/text manually |
| Android UI control | `ScreenReaderService.instance != null` | Explain setup and offer Accessibility settings |
| Visual screen capture | Active `ScreenCaptureManager` MediaProjection session | Use UI tree only, or ask for capture |
| Floating overlay | `Settings.canDrawOverlays(context)` | Open full ClawDroid chat |
| Voice call | `RECORD_AUDIO` | Continue with typed input |
| Notifications/status | `POST_NOTIFICATIONS` where required | In-app status only |
| Long background task | Foreground service allowed and notification available | Continue only while app is open |
| External files | Storage/shared folder availability | Save inside sandbox and explain location |
| Connected service action | OAuth connected and connector enabled | Offer connect/settings flow |

Implementation notes:

- Wrap `ToolExecutor.execute()` with `PermissionAwareToolExecutor` for assistant-originated runs.
- Keep existing raw tools available for internal use, but assistant-facing tool calls should be preflighted.
- Add a `PermissionStep` activity item type so the conversation log shows setup attempts, grants, denials, and fallback choices.
- `SettingsScreen` already has Android Control status hooks; reuse them from the setup prompt instead of adding a second settings flow.
- Store transient "resume after permission" state with `AssistantInvocation.id`, blocked tool name, sanitized arguments, and sensitivity classification.
- Expire pending resumes after a short time or when the source app changes significantly.

Permission recovery flow:

```text
Tool wants capability
  -> PermissionAwareToolExecutor preflight
  -> allowed? run tool
  -> blocked? emit PermissionBlockedResult
  -> overlay/chat renders setup prompt
  -> user grants/skips/denies
  -> coordinator refreshes state
  -> safe retry or fallback
  -> log PermissionStep
```

Sensitive retry policy:

- Safe to retry automatically: `get_screen`, `screenshot`, `get_installed_apps`, opening ClawDroid settings.
- Retry only after explicit user confirmation: `type_text`, `tap` on submit buttons, connected-service writes, message sends, deletes, purchases, account/security settings.
- Never retry automatically after denial. Explain the fallback and wait for the user.

## Data Contracts

### `AssistantInvocation`

Use one contract for every assistant entrypoint:

```kotlin
data class AssistantInvocation(
    val id: String,
    val source: AssistantInvocationSource,
    val mode: AssistantMode,
    val userText: String?,
    val contextSnapshot: AssistantContextSnapshot?,
    val mediaPath: String?,
    val mediaMimeType: String?,
    val projectId: String?,
    val conversationId: String?,
    val createdAt: Long,
)
```

`source` values:

- `SYSTEM_ASSIST`
- `OVERLAY_BUTTON`
- `DOODLE_REGION`
- `VOICE_CALL`
- `SHARE_SHEET`
- `NOTIFICATION_ACTION`
- `QUICK_SETTINGS_TILE`
- `BACKGROUND_AUTOMATION`
- `ANDROID_CONTROL_TASK`

`mode` values:

- `ASK_SCREEN`
- `SEARCH_SCREEN`
- `DOODLE_SEARCH`
- `VOICE_CHAT`
- `RUN_AGENT_TASK`
- `SUMMARIZE`
- `AUTOMATE`

### `AssistantContextSnapshot`

```kotlin
data class AssistantContextSnapshot(
    val sourcePackage: String?,
    val sourceActivity: String?,
    val visibleText: String,
    val contentDescriptionText: String,
    val focusedText: String?,
    val webUri: String?,
    val screenshotPath: String?,
    val selectedRegionPath: String?,
    val capturedAt: Long,
    val captureMethod: CaptureMethod,
)
```

`captureMethod` values:

- `ASSIST_STRUCTURE`
- `ASSIST_SCREENSHOT`
- `VOICE_INTERACTION_MANAGER`
- `MEDIA_PROJECTION`
- `ANDROID_CONTROL_TREE`
- `ANDROID_CONTROL_SCREENSHOT`
- `ACCESSIBILITY_SNAPSHOT`
- `USER_SHARED_IMAGE`
- `NONE`

## Phase 0 - Platform Decision Checkpoint

Before implementation, decide how to handle the current `targetSdk = 28` constraint.

The current project intentionally targets SDK 28 for embedded executable compatibility. That may remain acceptable for sideload/F-Droid style distribution, but every assistant/screen-capture/background capability must be tested on real Android 12-16 devices because OEMs and Android settings screens may treat old-target apps differently.

Recommended path:

1. Keep `targetSdk = 28` while prototyping default assistant, overlay, and voice interaction.
2. Keep `compileSdk` current so newer APIs can be referenced behind runtime checks.
3. Create a spike branch that tries `targetSdk >= 35` only after solving executable sandbox compatibility.
4. If modern target SDK breaks embedded Termux execution, consider a split architecture:
   - Modern assistant shell app handles default assistant, overlay, screen capture, voice, notifications.
   - Runtime companion app or isolated service handles Termux/Linux execution.
   - The two communicate through a bound service or local IPC with explicit user installation.

Do not upgrade target SDK as a drive-by change.

## Phase 1 - Default Assistant Role Skeleton

Goal: ClawDroid appears as a selectable Android assistant and launches a minimal assistant session overlay.

Tasks:

1. Add manifest declarations:

```xml
<service
    android:name=".core.assistant.ClawVoiceInteractionService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true">
    <meta-data
        android:name="android.voice_interaction"
        android:resource="@xml/voice_interaction" />
    <intent-filter>
        <action android:name="android.service.voice.VoiceInteractionService" />
    </intent-filter>
</service>

<service
    android:name=".core.assistant.ClawVoiceInteractionSessionService"
    android:permission="android.permission.BIND_VOICE_INTERACTION"
    android:exported="true" />
```

2. Add `res/xml/voice_interaction.xml`.

The XML must reference the session service and recognition service fields expected by Android role validation. Validate exact attributes against platform samples/CTS while implementing because requirements differ across Android versions.

3. Implement `ClawVoiceInteractionService : VoiceInteractionService`.

Responsibilities:

- Start lightweight service lifecycle.
- Report readiness.
- Keep heavy agent work out of this service.
- Delegate active interactions to `ClawVoiceInteractionSession`.

4. Implement `ClawVoiceInteractionSessionService : VoiceInteractionSessionService`.

Responsibilities:

- Create `ClawVoiceInteractionSession`.
- Own session process/lifecycle if split processes are used.

5. Implement `ClawVoiceInteractionSession : VoiceInteractionSession`.

Responsibilities:

- Render a small Compose-based assistant view through `onCreateContentView()`.
- Handle `onShow()` flags.
- Receive assist data/screenshot callbacks when available.
- Call `finish()` cleanly when the user dismisses.

6. Add `AssistantPermissionCoordinator`.

Capabilities:

- Check whether ClawDroid is current default assistant.
- For API 29+, call `RoleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)` where available.
- Provide a fallback button to Android Settings > Apps > Default apps > Digital assistant app / Assist & voice input.
- Request notification, overlay, microphone, and storage permissions in context, not as one giant first-run wall.

Verification:

- ClawDroid appears in default assistant picker on at least one Pixel/AOSP emulator and one physical device.
- Assistant invocation opens ClawDroid overlay without launching the full main activity.
- Dismiss returns the user to the previous app.
- No agent work starts until the user speaks/types/taps an action.

## Phase 2 - Assistant Overlay Surface

Goal: A native overlay that feels instant, light, and safe.

Build two overlay variants:

1. `VoiceInteractionSession` content view
   - Used when invoked as the default assistant.
   - Preferred path because Android provides assist context and a system-managed interaction layer.

2. `OverlayWindowService`
   - Used for floating ClawDroid bubble, doodle mode, and quick actions outside default assistant invocation.
   - Uses `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
   - Requires `Settings.canDrawOverlays(context)`.

Overlay UI states:

- `CollapsedChip`: small ClawDroid pill/orb near screen edge.
- `PromptBar`: text input, mic button, doodle button, search button.
- `Listening`: realtime voice orb, partial transcript, cancel.
- `Thinking`: compact progress row with current activity step summary.
- `Answer`: short answer with buttons: Open chat, Search deeper, Run agent, Save.
- `Doodle`: transparent full-screen canvas with tools.

Design rules:

- Keep overlay compact and movable.
- Never cover the whole screen unless the user enters doodle mode.
- Provide obvious close/cancel.
- Show when screen content will be sent to the model.
- Tap "Open chat" to continue inside the main ClawDroid conversation with full activity logs.

Verification:

- Overlay can be opened, moved, collapsed, expanded, and dismissed.
- Overlay permission denial produces a clear setup step, not a crash.
- Overlay does not intercept touches outside its visible UI except during explicit doodle mode.

## Phase 3 - Screen Context Capture

Goal: Give the agent a structured understanding of "what I am looking at" and, when explicitly enabled, the ability to act on the current Android UI.

The latest merge already implemented the Android Control backend. This phase should integrate it into assistant mode instead of duplicating it.

Implement `ScreenContextManager` with a priority order:

1. `AssistStructure` from default assistant invocation.
2. Assistant screenshot from `VoiceInteractionSession` callbacks when system settings allow.
3. API-gated `VoiceInteractionManager` screen context APIs when compile/runtime support exists and ClawDroid is current assistant.
4. `AndroidControlTools.getScreen(context)` when `ScreenReaderService.instance` is active.
5. MediaProjection screenshot/session after explicit user consent.
6. `AndroidControlTools.screenshot(context)` when `ScreenCaptureManager` is already active.
7. User-shared image/file fallback.

Implement `AssistStructureExtractor`:

- Walk windows and view nodes.
- Collect visible text, content descriptions, hints, focused field text, package/activity names, URL-like strings.
- Preserve a small hierarchy map for grounding, but do not dump the full tree into the model.
- Redact obvious sensitive fields when nodes indicate password/input secret types.

Implement `ScreenshotSource`:

- Store screenshots in app cache by default.
- Attach only the current screenshot or selected crop to the invocation.
- Delete temporary captures after run unless the user saves them or the conversation needs a media attachment.

Use existing screen-control tools:

- `get_screen`
- `tap`
- `tap_text`
- `tap_resource_id`
- `long_press`
- `swipe`
- `scroll`
- `type_text`
- `clear_text`
- `press_back`
- `press_home`
- `press_recents`
- `open_notifications`
- `launch_app`
- `get_installed_apps`
- `screenshot`
- `wait`

Add assistant wrapper/context tools only if needed:

- `get_current_assistant_context`
- `crop_screen_region`
- `describe_screen_region`

Do not add duplicate versions of `tap`, `swipe`, `type_text`, or app-launching tools.

Implement `AndroidControlContextBridge`:

- Converts `AndroidControlTools.getScreen()` output into `AssistantContextSnapshot`.
- Marks tree results as `ANDROID_CONTROL_TREE`.
- Marks screenshot fallback results as `ANDROID_CONTROL_SCREENSHOT`.
- Normalizes node bounds into a compact list useful for overlay grounding.
- Detects empty/unhelpful trees and prompts the setup UI to request screen capture permission.

Implement `AndroidActionPolicy`:

- Allows read-only tools (`get_screen`, `get_installed_apps`) whenever Screen Control is active.
- Allows local navigation and UI exploration (`tap`, `scroll`, `press_back`, `wait`) during user-initiated assistant tasks.
- Requires explicit confirmation before typing or submitting sensitive content into external apps unless the user gave the exact content.
- Requires explicit confirmation before sending messages, emails, purchases, payments, account changes, deletion, or irreversible actions.
- Requires `get_screen` after every action to verify state before continuing.
- Delegates missing permission/setup states to `AssistantPermissionCoordinator` and returns structured permission fallback results.

Tool policy:

- Tools return a compact JSON summary plus a local media path when needed.
- The agent should ask before capturing if the current invocation did not already include user consent.
- Never capture in the background silently.
- When using Android Control, prefer `tap_text` or `tap_resource_id` from the tree before coordinate `tap`.
- If using coordinate `tap` from a screenshot, state uncertainty in the activity step and verify immediately with `get_screen`.

Verification:

- Ask "what is on my screen?" from another app and get a grounded answer from visible text.
- If screenshot access is disabled, answer from text structure and explain that visual screenshot access is off.
- With Accessibility enabled, ask "open Spotify and play my liked songs" and observe `get_screen -> launch_app/tap/... -> get_screen` loops.
- With Accessibility disabled, the assistant should offer the setup path rather than attempting blind coordinates.
- If the user declines Accessibility setup, the assistant should keep the request alive and offer a non-control fallback.
- Password fields and obvious secrets are redacted before model input.
- Typed/sent external messages still require user-provided content or approval.

## Phase 4 - Doodle / Circle-to-Search Style Selection

Goal: The user can draw over something, and ClawDroid acts on just that region.

Implement `DoodleOverlayController`:

- Full-screen transparent Compose overlay.
- Tool modes:
  - Freehand lasso
  - Rectangle
  - Arrow/mark
  - Eraser/clear
  - Confirm
  - Cancel
- Haptic feedback on mode changes and confirm.
- Stable bounds and no layout jumping.

Implement `DoodleSelection`:

```kotlin
data class DoodleSelection(
    val boundsPx: Rect,
    val pathSvgLike: String?,
    val screenshotPath: String,
    val cropPath: String,
    val userPrompt: String?,
)
```

Flow:

1. User invokes ClawDroid.
2. User taps doodle/circle.
3. We obtain a screenshot from assistant screenshot, `ScreenCaptureManager`, or MediaProjection.
4. User draws a region.
5. `RegionCropper` crops the bitmap.
6. `AssistantInvocationRouter` creates a `DOODLE_REGION` invocation with the crop.
7. Agent receives:
   - crop image
   - surrounding screen text if available
   - user text such as "search this" or "what is this?"
8. Agent can answer directly, run `web_search`, open browser, save to notes, or continue in chat.

Use the merged Android Control stack in doodle mode:

- If the user says "tap this", convert the doodle region center into a guarded `tap` call and verify with `get_screen`.
- If the user says "copy this text", first try nearby accessibility nodes from `get_screen`; use image/OCR fallback only when the tree is insufficient.
- If the user says "search this", avoid controlling the UI unless the user asks to open a browser/app. Prefer `web_search` and a concise overlay answer first.
- If the user circles an app control and asks ClawDroid to complete a workflow, promote to `ANDROID_CONTROL_TASK` and show the transparent activity trail.

MVP prompts:

- "Search this"
- "What is this?"
- "Summarize this"
- "Copy text"
- "Save to project"
- "Ask agent to work on this"

Verification:

- Circle a product/image/text region and get a result that uses only that region.
- Circle text in another app and OCR or structured text extraction finds it.
- Cancel leaves no capture artifact.
- Doodle overlay cannot be triggered from background without a visible user action.

## Phase 5 - Realtime Voice

Goal: Voice feels like a live assistant, not a record-then-send form.

The current `OpenAIRealtimeClient` only creates client secrets. Add actual audio transport.

Define:

```kotlin
interface VoiceTransport {
    val events: Flow<RealtimeVoiceEvent>
    suspend fun connect(config: RealtimeVoiceConfig)
    suspend fun sendAudio(frame: ByteArray)
    suspend fun sendText(text: String)
    suspend fun interrupt()
    suspend fun disconnect()
}
```

Implement two voice modes:

1. `OpenAIRealtimeVoiceTransport`
   - Speech-to-speech/live audio.
   - Prefer WebRTC for mobile-client realtime sessions where possible.
   - Use ephemeral client secrets from `OpenAIRealtimeClient`.
   - Implement AudioRecord input, AudioTrack output, VAD/turn events, barge-in, transcripts, and tool-call handoff.

2. `ChainedVoicePipeline`
   - Existing Android SpeechRecognizer or cloud STT -> `AgentEngine` -> `VoiceManager` TTS.
   - More deterministic and easier to debug.
   - Fallback for providers without live speech-to-speech.

Realtime voice integration with tools:

- Voice session creates an `AssistantInvocation` with mode `VOICE_CHAT`.
- Short conversational turns can stay in the realtime voice layer.
- Any tool call, MCP call, Termux task, or long-running action is routed to `AgentEngine`.
- Android UI tasks must use the merged `get_screen -> action -> wait -> get_screen` loop.
- Voice confirmations must be natural and short before sensitive Android Control actions: "I can send exactly that. Send it now?"
- While the agent runs, voice says brief status updates and the notification/activity step shows details.
- User can interrupt speech and steer the running agent.

Background voice policy:

- Explicit "live call" mode may use a foreground service with microphone type and persistent notification.
- No always-listening default in MVP.
- Optional wake phrase should be a later experimental feature with clear battery/privacy settings.

Verification:

- First audio latency is low enough to feel conversational.
- Barge-in stops TTS/output audio and starts listening.
- A voice request can call a tool, run a shell command, and speak a concise result.
- If realtime transport fails, app falls back to chained STT/TTS without losing the conversation.
- If microphone permission is missing, voice mode shows a one-tap setup prompt and falls back to typed input.

## Phase 6 - MCP, Termux, and Android Control Integration

Goal: Assistant mode has all the power of normal ClawDroid, not just chat.

Refactor `AgentEngine.run()` entrypoints:

- Keep existing prompt-based `run()` for chat.
- Add `runInvocation(invocation: AssistantInvocation)`.
- Convert screen context, crop images, voice transcripts, and source app metadata into message context through `ContextBuilder`.

Important changes:

- Add a `toolVisibilityProfile`:
  - `CHAT_FULL`: all local tools and enabled service tools.
  - `ASSISTANT_QUICK`: screen context, `get_screen`, web search, safe file/search tools, notifications.
  - `ANDROID_CONTROL`: full merged screen-control tools, but behind `AndroidActionPolicy`.
  - `VOICE_QUICK`: no huge noisy tools until user asks for "work on this"; allow read-only `get_screen` if enabled.
  - `BACKGROUND`: no screen capture/mic/tools that operate other apps unless the run was user-initiated with valid consent and an active foreground notification.
- Keep MCP startup lazy and cache tool lists per session to reduce assistant cold-start time.
- Do not start every MCP server for a one-sentence screen question unless needed.
- Long-running Termux work should immediately move from overlay into a foreground notification plus activity step.
- Screen-control actions should be logged as first-class activity steps, e.g. `📱 Read screen`, `👆 Tapped "Send"`, `⌨️ Typed into Messages`, `📸 Captured screen`.

Add assistant-specific tools:

- `open_current_result_in_chat`
- `save_screen_note`
- `search_selected_region`
- `create_project_from_screen`
- `continue_task_in_background`
- `request_android_control_setup`

Verification:

- Assistant overlay can answer simple screen questions quickly without booting heavy MCP unnecessarily.
- Assistant overlay can operate another app through `AndroidControlTools` when Accessibility is active.
- Assistant overlay shows a clear permission/setup prompt when Android Control, overlay, screen capture, or connected-service permissions are missing.
- A "run this in background" command starts the normal agent engine and foreground notification.
- MCP tools remain available in full chat and long-running assistant tasks.

## Phase 7 - Background Agent Mode

Goal: The app can keep working when useful, while staying Android-compliant and transparent.

Use existing:

- `AgentForegroundService`
- `EnhancedForegroundService`
- `BackgroundAgentRunner`
- `AutomationWorker`
- `BootReceiver`
- notifications

Add `BackgroundModeController`:

- Settings:
  - Off
  - Notifications and automations only
  - Agent can continue current task
  - Trusted background agent
- Foreground notification actions:
  - Open
  - Pause
  - Stop task
  - Read status aloud
- Persist currently running invocation/task id.
- On service restart, recover active conversation and last compaction summary.

Rules:

- Background mode can continue already-approved work.
- Background mode cannot newly capture screen/mic/location without foreground user action.
- Background mode should not drive arbitrary Android UI with Accessibility unless the user started that specific workflow and the foreground notification remains visible.
- Connected service actions still obey approval settings.
- Every background action becomes an activity step visible in the conversation.

Verification:

- Start a long Termux task from assistant overlay, lock phone, and get completion notification.
- If notifications/background service are blocked, the user sees an actionable setup/fallback prompt before the task is sent to background.
- Stop action kills current process group and cancels model stream.
- Reboot restores scheduled automations but not stale one-off mic/screen sessions.

## Phase 8 - Permission Flow, Settings, and Onboarding

Goal: Make setup understandable without a giant permission dump.

Add an "Assistant Mode" setup card:

- Set as default assistant
- Allow screen context
- Allow screenshot/capture
- Allow draw over apps
- Enable ClawDroid Screen Control accessibility service
- Enable microphone
- Enable background continuation

Each row should show:

- Current state
- Why it is needed
- What it allows
- Button to open exact settings/request flow
- "Skip for now"
- What fallback ClawDroid will use if skipped

Add settings toggles:

- Default assistant status
- Doodle overlay enabled
- Screen context enabled
- Android Screen Control status
- Android Screen Capture status
- Save screenshots to history: off by default
- Realtime voice provider/mode
- Background agent mode
- Android control automation mode: off, ask each workflow, trusted for current session
- Quick Settings tile enablement instructions

Do not ask for all permissions on first launch. Ask at the moment a feature needs them.

Add contextual setup prompts:

- Assistant role prompt when the user tries to invoke/enable "use anywhere".
- Overlay prompt when the user asks for the floating assistant or doodle UI outside the app.
- Accessibility prompt when the agent needs to read/tap/type in another app.
- MediaProjection prompt when visual screenshot fallback is required.
- Microphone prompt when voice mode starts.
- Notification prompt before long-running background work.
- Connected-service prompt when a tool needs Google/GitHub/Notion/Spotify access.

Error fallback behavior:

- Permission prompt dismissed: keep the conversation intact and explain the non-permission fallback.
- Permission denied twice: stop prompting automatically for that capability in the same session; show a small "Enable later in Settings" action.
- Settings opened but not granted: refresh status on return and explain what is still missing.
- OS/OEM blocks permission path: offer the closest manual settings path and continue with degraded mode.
- Tool still fails after permission grant: show the tool error separately from permission state so the user knows setup succeeded but the action failed.

## Phase 9 - Testing Matrix

Device/API coverage:

- Android 10/API 29 physical or emulator
- Android 12/API 31 physical or emulator
- Android 14/API 34 physical or emulator
- Android 15/API 35 physical or emulator
- Android 16/API 36 physical or emulator
- At least one OEM skin if possible: Samsung, Xiaomi, OnePlus, or similar

Feature tests:

- Default assistant selection appears.
- System assistant invocation opens session.
- AssistStructure text extraction works.
- Accessibility service setup opens Android settings and returns to ClawDroid status.
- Permission-blocked tools return structured recovery results, not raw exceptions.
- Permission prompts have a working primary action, "Not now", and a sensible degraded fallback.
- Returning from Android settings refreshes permission state and safely resumes only non-sensitive blocked reads.
- Denying a permission twice does not trap the user in a prompt loop.
- `get_screen` returns a useful tree for native apps.
- `tap_text`, `tap_resource_id`, `type_text`, `scroll`, `press_back`, and `launch_app` work and verify with `get_screen`.
- Screenshot access denied path works.
- Screenshot access allowed path works.
- `get_screen` falls back to screenshot only when the tree is empty/unhelpful and capture is active.
- Overlay permission flow works.
- Doodle crop accuracy works in portrait and landscape.
- MediaProjection consent flow works.
- Voice chained mode works.
- Realtime voice mode connects, speaks, interrupts, and disconnects.
- Background task survives app swipe away.
- Stop button kills shell processes.
- MCP tools load and execute from assistant-initiated tasks.
- Screen captures are deleted when not saved.

Regression tests:

- Normal chat still works.
- Existing voice input still works.
- Existing TTS settings still work.
- Existing MCP settings still work.
- Existing automations still work.
- Existing notification listener/channel flows still work.
- Existing Android Control settings and tools still work from normal chat.
- Existing permission prompts in chat/settings do not regress.

## Milestone Build Order

### Milestone A - Selectable Default Assistant

Deliver:

- Manifest services and XML metadata.
- Assistant role request UI.
- Minimal `VoiceInteractionSession` with a "Hi, I am ClawDroid" prompt.

Acceptance:

- User can select ClawDroid as assistant.
- System invocation opens and dismisses cleanly.

### Milestone B - Ask About Screen

Deliver:

- Assist context extraction.
- `AndroidControlContextBridge` for merged `get_screen` output.
- Compact overlay prompt.
- `AssistantInvocationRouter` into `AgentEngine`.

Acceptance:

- From another app, ask "summarize this screen" and get a grounded answer.

### Milestone B2 - Control The Current App

Deliver:

- Assistant policy wrapper around `AndroidControlTools`.
- Setup flow for Screen Control and Screen Capture.
- Structured permission fallback results for missing Accessibility and screen capture.
- Activity-step rendering for Android UI actions.

Acceptance:

- From assistant overlay or voice, ask ClawDroid to perform a small app task. It reads the screen, acts, waits, verifies, and stops cleanly if permission is missing.
- With Screen Control disabled, the user gets a clear setup prompt and a fallback instead of a failed tool dump.

### Milestone B3 - Clean Permission Recovery

Deliver:

- `AssistantPermissionCoordinator`.
- `PermissionAwareToolExecutor`.
- `PermissionStep` activity item.
- Resume-after-permission state for safe read-only retries.

Acceptance:

- Every assistant capability either runs, prompts with a clear recovery action, or falls back gracefully. The user always understands what happened and what to do next.

### Milestone C - Doodle Search

Deliver:

- Overlay doodle mode.
- Screenshot/crop pipeline.
- Region prompt into agent.
- Web search result action.

Acceptance:

- Circle a visible thing and ask "search this"; answer includes useful web/context result.

### Milestone D - Real Voice

Deliver:

- Chained voice polish.
- Realtime transport implementation.
- Barge-in and interrupt handling.

Acceptance:

- A hands-free assistant call can answer, run a lightweight tool, and speak result.

### Milestone E - Background Continuation

Deliver:

- Background mode controller.
- Notification controls.
- Long-running assistant tasks routed through existing foreground service.

Acceptance:

- Start a task from anywhere, leave app, receive status/completion, inspect activity steps later.

## Implementation Notes for the Next Agent

- Start with default assistant plumbing. Without `VoiceInteractionService` working, screen context and invocation will be guesswork.
- Keep all screen/voice/doodle inputs flowing into `AssistantInvocation`; avoid special-case prompt strings scattered through UI code.
- Use existing `AgentEngine`, `ToolExecutor`, `McpServerLauncher`, `ProcessManager`, and the merged `AndroidControlTools`. Extend them with profiles and invocation context rather than creating a second agent runtime.
- Do not reimplement Accessibility gestures, tree dumps, app launching, or MediaProjection capture in assistant modules. Build wrappers and policy around `core/control`.
- Do not let tools throw user-facing permission exceptions. Convert them to structured permission-blocked results and render them as setup prompts.
- Treat overlay and MediaProjection as permission-gated enhancements, not the source of truth.
- Build fallback paths early. Some devices will not expose all assistant screenshot/context settings the same way.
- Keep every background or tool action visible as a normal ClawDroid activity step.
- Keep privacy visible: show a chip when screen context, screenshot, mic, or notification content is being used.
- For realtime voice, implement transport and audio lifecycle first, then tune personality.

## Open Questions

- Can ClawDroid remain target SDK 28 and still be reliably accepted as default assistant on the user's target devices?
- Should we ship only sideload/F-Droid builds, or split assistant shell/runtime to support future Play-compatible builds?
- Which realtime provider is primary for MVP: OpenAI Realtime, chained OpenAI-compatible STT/TTS, local Whisper/Piper, or provider-selectable?
- How aggressive should Android Control be by default: ask every workflow, trusted current session, or full trusted mode?
- How long should pending permission resumes stay valid, and should they survive process death?
- How much screen context should be stored in Room by default? Recommended answer: text summary yes, raw screenshots no unless user saves.

## Definition of Done

This feature is done when a user can set ClawDroid as the default assistant, invoke it from any supported app, ask or speak about the current screen, circle something to search or act on it, ask ClawDroid to operate the current Android UI through the merged Screen Control tools, get clean setup prompts and graceful fallbacks whenever a permission or Android role is missing, continue into a full agent task with MCP/Termux tools, send that task to background, stop it immediately, and later inspect what happened in the conversation activity log.
