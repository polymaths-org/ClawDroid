package com.clawdroid.app.core.assistant

enum class AssistantInvocationSource {
    SYSTEM_ASSIST,
    OVERLAY_BUTTON,
    DOODLE_REGION,
    VOICE_CALL,
    SHARE_SHEET,
    NOTIFICATION_ACTION,
    QUICK_SETTINGS_TILE,
    BACKGROUND_AUTOMATION,
    ANDROID_CONTROL_TASK
}

enum class AssistantMode {
    ASK_SCREEN,
    SEARCH_SCREEN,
    DOODLE_SEARCH,
    VOICE_CHAT,
    RUN_AGENT_TASK,
    SUMMARIZE,
    AUTOMATE
}

enum class CaptureMethod {
    ASSIST_STRUCTURE,
    ASSIST_SCREENSHOT,
    VOICE_INTERACTION_MANAGER,
    MEDIA_PROJECTION,
    ANDROID_CONTROL_TREE,
    ANDROID_CONTROL_SCREENSHOT,
    ACCESSIBILITY_SNAPSHOT,
    USER_SHARED_IMAGE,
    NONE
}

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
