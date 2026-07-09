package com.clawdroid.app.core.assistant.overlay

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager

enum class OverlayInputMode(val id: String, val label: String) {
    VOICE("voice", "Voice"),
    KEYBOARD("keyboard", "Keyboard"),
    HYBRID("hybrid", "Hybrid"),
}

enum class OverlayPosition(val id: String, val label: String) {
    BOTTOM_RIGHT("bottom_right", "Bottom-right"),
    BOTTOM_LEFT("bottom_left", "Bottom-left"),
    TOP_RIGHT("top_right", "Top-right"),
    TOP_LEFT("top_left", "Top-left"),
    FLOATING("floating", "Floating"),
}

data class OverlayConfig(
    val inputMode: OverlayInputMode = OverlayInputMode.KEYBOARD,
    val assistantInputMode: OverlayInputMode = OverlayInputMode.VOICE,
    val fontSize: Int = 14,
    val maxLines: Int = 10,
    val expandable: Boolean = true,
    val launchGreeting: Boolean = false,
    val voiceLanguage: String = "en-US",
    val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM_RIGHT,
    val autoDismissSeconds: Int = 10,
    val showOnLockscreen: Boolean = true,
    val ttsStreaming: Boolean = true,
    val ttsAutoplay: Boolean = true,
)

class OverlaySettingsRepository(context: Context) {
    @Suppress("unused")
    private val appContext = context.applicationContext

    fun getConfig(): OverlayConfig = OverlayConfig(
        inputMode = OverlayInputMode.entries.firstOrNull { it.id == AppConfigManager.overlayInputMode } ?: OverlayInputMode.KEYBOARD,
        assistantInputMode = OverlayInputMode.entries.firstOrNull { it.id == AppConfigManager.assistantOverlayInputMode } ?: OverlayInputMode.VOICE,
        fontSize = AppConfigManager.overlayFontSize,
        maxLines = AppConfigManager.overlayMaxLines,
        expandable = AppConfigManager.overlayExpandable,
        launchGreeting = AppConfigManager.voiceLaunchGreetingEnabled,
        voiceLanguage = AppConfigManager.overlayVoiceLanguage,
        overlayPosition = OverlayPosition.entries.firstOrNull { it.id == AppConfigManager.overlayPosition } ?: OverlayPosition.BOTTOM_RIGHT,
        autoDismissSeconds = AppConfigManager.overlayAutoDismissSeconds,
        showOnLockscreen = AppConfigManager.overlayShowOnLockscreen,
        ttsStreaming = AppConfigManager.overlayTtsStreamingEnabled,
        ttsAutoplay = AppConfigManager.overlayTtsAutoplay,
    )

    fun saveConfig(config: OverlayConfig) {
        AppConfigManager.overlayInputMode = config.inputMode.id
        AppConfigManager.assistantOverlayInputMode = config.assistantInputMode.id
        AppConfigManager.overlayFontSize = config.fontSize
        AppConfigManager.overlayMaxLines = config.maxLines
        AppConfigManager.overlayExpandable = config.expandable
        AppConfigManager.voiceLaunchGreetingEnabled = config.launchGreeting
        AppConfigManager.overlayVoiceLanguage = config.voiceLanguage
        AppConfigManager.overlayPosition = config.overlayPosition.id
        AppConfigManager.overlayAutoDismissSeconds = config.autoDismissSeconds
        AppConfigManager.overlayShowOnLockscreen = config.showOnLockscreen
        AppConfigManager.overlayTtsStreamingEnabled = config.ttsStreaming
        AppConfigManager.overlayTtsAutoplay = config.ttsAutoplay
    }
}
