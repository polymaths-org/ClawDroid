package com.clawdroid.app.core.config

import android.content.Context
import android.content.SharedPreferences
import com.clawdroid.app.BuildConfig
import com.clawdroid.app.core.agent.AgentConfig
import com.clawdroid.app.core.agent.AgentConfigLoader
import com.clawdroid.app.core.agent.ChannelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppConfigManager {
    private const val PREFS = "clawdroid_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    private var prefs: SharedPreferences? = null
    private val _appThemeFlow = MutableStateFlow("claw_magic")
    val appThemeFlow: StateFlow<String> = _appThemeFlow.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _appThemeFlow.value = appTheme
    }

    private val p: SharedPreferences get() = prefs!!

    const val DEV_MODE = true
    private const val DEV_API_KEY = "sk-pkuyklgbgcdqyezsehpvachsgyadwgghnplflashqjlysmwu"

    val baseUrl: String
        get() = p.getString(KEY_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: if (DEV_MODE) "https://api.siliconflow.com/v1" else BuildConfig.LLM_BASE_URL.trimEnd('/').takeIf { it.isNotBlank() } ?: "https://openrouter.ai/api/v1"

    val apiKey: String
        get() = p.getString(KEY_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: if (DEV_MODE) DEV_API_KEY else BuildConfig.LLM_API_KEY.takeIf { it.isNotBlank() } ?: ""

    val model: String
        get() = p.getString(KEY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: if (DEV_MODE) "moonshotai/Kimi-K2.6" else BuildConfig.LLM_MODEL.takeIf { it.isNotBlank() } ?: "openai/gpt-4o"

    val provider: String
        get() = p.getString(KEY_PROVIDER, null)
            ?.takeIf { it.isNotBlank() }
            ?: if (DEV_MODE) "siliconflow" else "openrouter"

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    val isOnboardingComplete: Boolean
        get() = p.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    var hasSeenHatching: Boolean
        get() = p.getBoolean("has_seen_hatching", false)
        set(value) = p.edit().putBoolean("has_seen_hatching", value).apply()

    var hasCompletedPostHatchIntro: Boolean
        get() = p.getBoolean("has_completed_post_hatch_intro", false)
        set(value) = p.edit().putBoolean("has_completed_post_hatch_intro", value).apply()

    fun save(baseUrl: String, apiKey: String, model: String) {
        p.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .apply()
    }

    fun save(provider: String, baseUrl: String, apiKey: String, model: String) {
        p.edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    // TTS Settings Configuration
    const val KEY_TTS_ENGINE = "tts_engine"
    const val KEY_TTS_VOICE = "tts_voice"
    const val KEY_TTS_SPEED = "tts_speed"

    var ttsEngine: String
        get() = p.getString(KEY_TTS_ENGINE, "device") ?: "device"
        set(value) = p.edit().putString(KEY_TTS_ENGINE, value).apply()

    var ttsVoice: String
        get() = p.getString(KEY_TTS_VOICE, "") ?: ""
        set(value) = p.edit().putString(KEY_TTS_VOICE, value).apply()

    var ttsSpeed: Float
        get() = p.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = p.edit().putFloat(KEY_TTS_SPEED, value).apply()

    const val KEY_REALTIME_VOICE_ENABLED = "realtime_voice_enabled"
    const val KEY_REALTIME_VOICE_MODEL = "realtime_voice_model"
    const val KEY_REALTIME_VOICE_VOICE = "realtime_voice_voice"

    var realtimeVoiceEnabled: Boolean
        get() = p.getBoolean(KEY_REALTIME_VOICE_ENABLED, false)
        set(value) = p.edit().putBoolean(KEY_REALTIME_VOICE_ENABLED, value).apply()

    var realtimeVoiceModel: String
        get() = p.getString(KEY_REALTIME_VOICE_MODEL, "gpt-realtime-2") ?: "gpt-realtime-2"
        set(value) = p.edit().putString(KEY_REALTIME_VOICE_MODEL, value).apply()

    var realtimeVoiceVoice: String
        get() = p.getString(KEY_REALTIME_VOICE_VOICE, "marin") ?: "marin"
        set(value) = p.edit().putString(KEY_REALTIME_VOICE_VOICE, value).apply()

    // Cloud TTS API keys
    const val KEY_OPENAI_TTS_API_KEY = "openai_tts_api_key"
    const val KEY_OPENAI_REALTIME_API_KEY = "openai_realtime_api_key"
    const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    const val KEY_DEEPGRAM_API_KEY = "deepgram_api_key"

    var openaiTtsApiKey: String
        get() = p.getString(KEY_OPENAI_TTS_API_KEY, "") ?: ""
        set(value) = p.edit().putString(KEY_OPENAI_TTS_API_KEY, value).apply()

    var openaiRealtimeApiKey: String
        get() = p.getString(KEY_OPENAI_REALTIME_API_KEY, "")
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENAI_REALTIME_API_KEY.takeIf { it.isNotBlank() }
            ?: ""
        set(value) = p.edit().putString(KEY_OPENAI_REALTIME_API_KEY, value).apply()

    var elevenlabsApiKey: String
        get() = p.getString(KEY_ELEVENLABS_API_KEY, "") ?: ""
        set(value) = p.edit().putString(KEY_ELEVENLABS_API_KEY, value).apply()

    var deepgramApiKey: String
        get() = p.getString(KEY_DEEPGRAM_API_KEY, "") ?: ""
        set(value) = p.edit().putString(KEY_DEEPGRAM_API_KEY, value).apply()

    // Agent Customization Configuration
    const val KEY_AGENT_NAME = "agent_name"
    const val KEY_AGENT_PERSONALITY = "agent_personality"
    const val KEY_AGENT_PURPOSE = "agent_purpose"
    const val KEY_AGENT_VOICE_PROFILE = "agent_voice_profile"
    const val KEY_ULTRA_AGENT_ENABLED = "ultra_agent_enabled"
    const val KEY_AGENT_RESPONSE_MODE = "agent_response_mode"

    var agentName: String
        get() = p.getString(KEY_AGENT_NAME, "Nova") ?: "Nova"
        set(value) = p.edit().putString(KEY_AGENT_NAME, value).apply()

    var agentPersonality: String
        get() = p.getString(KEY_AGENT_PERSONALITY, "Cyberpunk") ?: "Cyberpunk"
        set(value) = p.edit().putString(KEY_AGENT_PERSONALITY, value).apply()

    var agentPurpose: String
        get() = p.getString(KEY_AGENT_PURPOSE, "System Controls & Diagnostics") ?: "System Controls & Diagnostics"
        set(value) = p.edit().putString(KEY_AGENT_PURPOSE, value).apply()

    var agentVoiceProfile: String
        get() = p.getString(KEY_AGENT_VOICE_PROFILE, "female") ?: "female"
        set(value) = p.edit().putString(KEY_AGENT_VOICE_PROFILE, value).apply()

    var ultraAgentEnabled: Boolean
        get() = p.getBoolean(KEY_ULTRA_AGENT_ENABLED, false)
        set(value) = p.edit().putBoolean(KEY_ULTRA_AGENT_ENABLED, value).apply()

    var agentBehaviorMode: String
        get() = p.getString("agent_behavior_mode", "balanced") ?: "balanced"
        set(value) = p.edit().putString("agent_behavior_mode", value).apply()

    var agentResponseMode: String
        get() = p.getString(KEY_AGENT_RESPONSE_MODE, "balanced") ?: "balanced"
        set(value) = p.edit().putString(
            KEY_AGENT_RESPONSE_MODE,
            when (value) {
                "fast", "thinking", "balanced" -> value
                else -> "balanced"
            },
        ).apply()

    var approvalMode: String
        get() = p.getString("approval_mode", "default") ?: "default"
        set(value) = p.edit().putString("approval_mode", value).apply()

    var appTheme: String
        get() = p.getString("app_theme", "claw_magic") ?: "claw_magic"
        set(value) {
            p.edit().putString("app_theme", value).apply()
            _appThemeFlow.value = value
        }

    var dynamicThinkingEnabled: Boolean
        get() = p.getBoolean("dynamic_thinking_enabled", true)
        set(value) = p.edit().putBoolean("dynamic_thinking_enabled", value).apply()

    var promptEnhancementEnabled: Boolean
        get() = p.getBoolean("prompt_enhancement_enabled", true)
        set(value) = p.edit().putBoolean("prompt_enhancement_enabled", value).apply()

    var emojiToneEnabled: Boolean
        get() = p.getBoolean("emoji_tone_enabled", true)
        set(value) = p.edit().putBoolean("emoji_tone_enabled", value).apply()

    var mcpEnabled: Boolean
        get() = p.getBoolean("mcp_enabled", true)
        set(value) = p.edit().putBoolean("mcp_enabled", value).apply()

    var mcpSandboxOnly: Boolean
        get() = p.getBoolean("mcp_sandbox_only", true)
        set(value) = p.edit().putBoolean("mcp_sandbox_only", value).apply()

    var mcpServerList: String
        get() = p.getString("mcp_server_list", "[]") ?: "[]"
        set(value) = p.edit().putString("mcp_server_list", value).apply()

    var mcpServers: String
        get() = p.getString("mcp_servers", "") ?: ""
        set(value) = p.edit().putString("mcp_servers", value).apply()

    var interpoleEnabled: Boolean
        get() = p.getBoolean("interpole_enabled", false)
        set(value) = p.edit().putBoolean("interpole_enabled", value).apply()

    var interpoleConnectionType: String
        get() = p.getString("interpole_connection_type", "local") ?: "local"
        set(value) = p.edit().putString(
            "interpole_connection_type",
            when (value) {
                "local", "tailscale" -> value
                else -> "local"
            },
        ).apply()

    var interpoleHost: String
        get() = p.getString("interpole_host", "") ?: ""
        set(value) = p.edit().putString("interpole_host", value).apply()

    var interpolePort: Int
        get() = p.getInt("interpole_port", 8765)
        set(value) = p.edit().putInt("interpole_port", value.coerceIn(1, 65535)).apply()

    var interpoleDefaultEnvironment: String
        get() = p.getString("interpole_default_environment", "android") ?: "android"
        set(value) = p.edit().putString(
            "interpole_default_environment",
            when (value) {
                "android", "desktop" -> value
                else -> "android"
            },
        ).apply()

    var interpoleTrustMode: String
        get() = p.getString("interpole_trust_mode", "zero_trust") ?: "zero_trust"
        set(value) = p.edit().putString(
            "interpole_trust_mode",
            when (value) {
                "zero_trust", "trusted", "ask_every_time" -> value
                else -> "zero_trust"
            },
        ).apply()

    var interpoleTrustedFolders: String
        get() = p.getString("interpole_trusted_folders", "") ?: ""
        set(value) = p.edit().putString("interpole_trusted_folders", value).apply()

    var interpoleAllowExecute: Boolean
        get() = p.getBoolean("interpole_allow_execute", false)
        set(value) = p.edit().putBoolean("interpole_allow_execute", value).apply()

    var interpolePairedDeviceName: String
        get() = p.getString("interpole_paired_device_name", "") ?: ""
        set(value) = p.edit().putString("interpole_paired_device_name", value).apply()

    var interpoleDeviceId: String
        get() = p.getString("interpole_device_id", "") ?: ""
        set(value) = p.edit().putString("interpole_device_id", value).apply()

    var interpoleDeviceToken: String
        get() = p.getString("interpole_device_token", "") ?: ""
        set(value) = p.edit().putString("interpole_device_token", value).apply()

    var skillStoreEnabled: Boolean
        get() = p.getBoolean("skill_store_enabled", true)
        set(value) = p.edit().putBoolean("skill_store_enabled", value).apply()

    var agentsMd: String
        get() = p.getString("agents_md", "") ?: ""
        set(value) = p.edit().putString("agents_md", value).apply()

    var soulMd: String
        get() = p.getString("soul_md", "") ?: ""
        set(value) = p.edit().putString("soul_md", value).apply()

    var toolsMd: String
        get() = p.getString("tools_md", "") ?: ""
        set(value) = p.edit().putString("tools_md", value).apply()

    var skillMd: String
        get() = p.getString("skill_md", "") ?: ""
        set(value) = p.edit().putString("skill_md", value).apply()

    var systemMd: String
        get() = p.getString("system_md", null) ?: p.getString("claude_md", "") ?: ""
        set(value) = p.edit().putString("system_md", value).apply()

    // Skills & Channels Integration Configuration
    const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
    const val KEY_ACTIVE_CONVERSATION_ID = "active_conversation_id"
    const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"
    const val KEY_WHATSAPP_ALLOWED_CONTACTS = "whatsapp_allowed_contacts"
    const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
    const val KEY_HEARTBEAT_INTERVAL_MIN = "heartbeat_interval_min"

    var activeProjectId: String?
        get() = p.getString(KEY_ACTIVE_PROJECT_ID, null)
        set(value) = p.edit().putString(KEY_ACTIVE_PROJECT_ID, value).apply()

    var activeConversationId: String?
        get() = p.getString(KEY_ACTIVE_CONVERSATION_ID, null)
        set(value) = p.edit().putString(KEY_ACTIVE_CONVERSATION_ID, value).apply()

    var whatsappEnabled: Boolean
        get() = p.getBoolean(KEY_WHATSAPP_ENABLED, false)
        set(value) = p.edit().putBoolean(KEY_WHATSAPP_ENABLED, value).apply()

    var whatsappAllowedContacts: String
        get() = p.getString(KEY_WHATSAPP_ALLOWED_CONTACTS, "") ?: ""
        set(value) = p.edit().putString(KEY_WHATSAPP_ALLOWED_CONTACTS, value).apply()

    var whatsappCliCommand: String
        get() = p.getString(
            "whatsapp_cli_command",
            "npx -y @open-wa/wa-automate --sessionId clawdroid --qr-terminal --popup false"
        ) ?: "npx -y @open-wa/wa-automate --sessionId clawdroid --qr-terminal --popup false"
        set(value) = p.edit().putString("whatsapp_cli_command", value).apply()

    var whatsappSendCommand: String
        get() = p.getString("whatsapp_send_command", "") ?: ""
        set(value) = p.edit().putString("whatsapp_send_command", value).apply()

    var whatsappPollCommand: String
        get() = p.getString("whatsapp_poll_command", "") ?: ""
        set(value) = p.edit().putString("whatsapp_poll_command", value).apply()

    var whatsappNotificationFallbackEnabled: Boolean
        get() = p.getBoolean("whatsapp_notification_fallback_enabled", false)
        set(value) = p.edit().putBoolean("whatsapp_notification_fallback_enabled", value).apply()

    var whatsappPhone: String
        get() = p.getString("whatsapp_phone", "") ?: ""
        set(value) = p.edit().putString("whatsapp_phone", value).apply()

    var whatsappAutoReply: Boolean
        get() = p.getBoolean("whatsapp_auto_reply", true)
        set(value) = p.edit().putBoolean("whatsapp_auto_reply", value).apply()

    var whatsappNotifyOnMessage: Boolean
        get() = p.getBoolean("whatsapp_notify_on_message", true)
        set(value) = p.edit().putBoolean("whatsapp_notify_on_message", value).apply()

    var whatsappAutoDownloadMedia: Boolean
        get() = p.getBoolean("whatsapp_auto_download_media", false)
        set(value) = p.edit().putBoolean("whatsapp_auto_download_media", value).apply()

    var whatsappSendReadReceipts: Boolean
        get() = p.getBoolean("whatsapp_send_read_receipts", false)
        set(value) = p.edit().putBoolean("whatsapp_send_read_receipts", value).apply()

    var whatsappShowTypingIndicator: Boolean
        get() = p.getBoolean("whatsapp_show_typing_indicator", false)
        set(value) = p.edit().putBoolean("whatsapp_show_typing_indicator", value).apply()

    var whatsappReplyDelaySeconds: Int
        get() = p.getInt("whatsapp_reply_delay_seconds", 2)
        set(value) = p.edit().putInt("whatsapp_reply_delay_seconds", value).apply()

    var whatsappReplyMode: String
        get() = p.getString("whatsapp_reply_mode", "smart") ?: "smart"
        set(value) = p.edit().putString("whatsapp_reply_mode", value).apply()

    var whatsappReplyLanguage: String
        get() = p.getString("whatsapp_reply_language", "english") ?: "english"
        set(value) = p.edit().putString("whatsapp_reply_language", value).apply()

    var whatsappCustomReplyPrompt: String
        get() = p.getString("whatsapp_custom_reply_prompt", "") ?: ""
        set(value) = p.edit().putString("whatsapp_custom_reply_prompt", value).apply()

    var whatsappMonitoredChats: String
        get() = p.getString("whatsapp_monitored_chats", "") ?: ""
        set(value) = p.edit().putString("whatsapp_monitored_chats", value).apply()

    var whatsappMaxStoredMessages: Int
        get() = p.getInt("whatsapp_max_stored_messages", 5000)
        set(value) = p.edit().putInt("whatsapp_max_stored_messages", value).apply()

    var whatsappListenerPort: Int
        get() = p.getInt("whatsapp_listener_port", 8765)
        set(value) = p.edit().putInt("whatsapp_listener_port", value).apply()

    var whatsappAutoStartOnLaunch: Boolean
        get() = p.getBoolean("whatsapp_auto_start_on_launch", true)
        set(value) = p.edit().putBoolean("whatsapp_auto_start_on_launch", value).apply()

    var heartbeatEnabled: Boolean
        get() = p.getBoolean(KEY_HEARTBEAT_ENABLED, false)
        set(value) = p.edit().putBoolean(KEY_HEARTBEAT_ENABLED, value).apply()

    var heartbeatIntervalMin: Int
        get() = p.getInt(KEY_HEARTBEAT_INTERVAL_MIN, 15)
        set(value) = p.edit().putInt(KEY_HEARTBEAT_INTERVAL_MIN, value).apply()

    // Owner info
    const val KEY_OWNER_NAME = "owner_name"
    const val KEY_OWNER_INFO = "owner_info"

    var ownerName: String
        get() = p.getString(KEY_OWNER_NAME, "") ?: ""
        set(value) = p.edit().putString(KEY_OWNER_NAME, value).apply()

    var ownerInfo: String
        get() = p.getString(KEY_OWNER_INFO, "") ?: ""
        set(value) = p.edit().putString(KEY_OWNER_INFO, value).apply()

    // Agent execution
    const val KEY_MAX_AGENT_TURNS = "max_agent_turns"

    var maxAgentTurns: Int
        get() = p.getInt(KEY_MAX_AGENT_TURNS, 200)
        set(value) = p.edit().putInt(KEY_MAX_AGENT_TURNS, value).apply()

    // SMS channel
    const val KEY_SMS_ENABLED = "sms_enabled"

    var smsEnabled: Boolean
        get() = p.getBoolean(KEY_SMS_ENABLED, false)
        set(value) = p.edit().putBoolean(KEY_SMS_ENABLED, value).apply()

    var telegramEnabled: Boolean
        get() = p.getBoolean("telegram_enabled", false)
        set(value) = p.edit().putBoolean("telegram_enabled", value).apply()

    var telegramBotToken: String
        get() = p.getString("telegram_bot_token", "") ?: ""
        set(value) = p.edit().putString("telegram_bot_token", value).apply()

    var telegramAllowedChats: String
        get() = p.getString("telegram_allowed_chats", "") ?: ""
        set(value) = p.edit().putString("telegram_allowed_chats", value).apply()

    var slackEnabled: Boolean
        get() = p.getBoolean("slack_enabled", false)
        set(value) = p.edit().putBoolean("slack_enabled", value).apply()

    var slackBotToken: String
        get() = p.getString("slack_bot_token", "") ?: ""
        set(value) = p.edit().putString("slack_bot_token", value).apply()

    var slackSigningSecret: String
        get() = p.getString("slack_signing_secret", "") ?: ""
        set(value) = p.edit().putString("slack_signing_secret", value).apply()

    var discordEnabled: Boolean
        get() = p.getBoolean("discord_enabled", false)
        set(value) = p.edit().putBoolean("discord_enabled", value).apply()

    var discordBotToken: String
        get() = p.getString("discord_bot_token", "") ?: ""
        set(value) = p.edit().putString("discord_bot_token", value).apply()

    var emailChannelEnabled: Boolean
        get() = p.getBoolean("email_channel_enabled", false)
        set(value) = p.edit().putBoolean("email_channel_enabled", value).apply()

    var emailChannelAddress: String
        get() = p.getString("email_channel_address", "") ?: ""
        set(value) = p.edit().putString("email_channel_address", value).apply()

    var emailChannelPassword: String
        get() = p.getString("email_channel_password", "") ?: ""
        set(value) = p.edit().putString("email_channel_password", value).apply()

    var webhookEnabled: Boolean
        get() = p.getBoolean("webhook_enabled", false)
        set(value) = p.edit().putBoolean("webhook_enabled", value).apply()

    var webhookUrl: String
        get() = p.getString("webhook_url", "") ?: ""
        set(value) = p.edit().putString("webhook_url", value).apply()

    var webhookSecret: String
        get() = p.getString("webhook_secret", "") ?: ""
        set(value) = p.edit().putString("webhook_secret", value).apply()

    var permissionsAsked: Boolean
        get() = p.getBoolean("permissions_asked", false)
        set(value) = p.edit().putBoolean("permissions_asked", value).apply()

    var notificationsEnabled: Boolean
        get() = p.getBoolean("notifications_enabled", true)
        set(value) = p.edit().putBoolean("notifications_enabled", value).apply()

    var taskStartedNotificationsEnabled: Boolean
        get() = p.getBoolean("task_started_notifications_enabled", true)
        set(value) = p.edit().putBoolean("task_started_notifications_enabled", value).apply()

    var taskFailedNotificationsEnabled: Boolean
        get() = p.getBoolean("task_failed_notifications_enabled", true)
        set(value) = p.edit().putBoolean("task_failed_notifications_enabled", value).apply()

    var taskCompletionNotificationMode: String
        get() = p.getString("task_completion_notification_mode", "notify") ?: "notify"
        set(value) = p.edit().putString("task_completion_notification_mode", value).apply()

    var notificationAskListenSeconds: Int
        get() = p.getInt("notification_ask_listen_seconds", 8)
        set(value) = p.edit().putInt("notification_ask_listen_seconds", value.coerceIn(3, 30)).apply()

    var assistantModeEnabled: Boolean
        get() = p.getBoolean("assistant_mode_enabled", false)
        set(value) = p.edit().putBoolean("assistant_mode_enabled", value).apply()

    var doodleOverlayEnabled: Boolean
        get() = p.getBoolean("doodle_overlay_enabled", false)
        set(value) = p.edit().putBoolean("doodle_overlay_enabled", value).apply()

    var screenContextEnabled: Boolean
        get() = p.getBoolean("screen_context_enabled", false)
        set(value) = p.edit().putBoolean("screen_context_enabled", value).apply()

    var screenContextMode: String
        get() = p.getString("screen_context_mode", "tree_first") ?: "tree_first"
        set(value) = p.edit().putString("screen_context_mode", value).apply()

    var visualContextFallbackEnabled: Boolean
        get() = p.getBoolean("visual_context_fallback_enabled", true)
        set(value) = p.edit().putBoolean("visual_context_fallback_enabled", value).apply()

    var saveScreenshotsToHistory: Boolean
        get() = p.getBoolean("save_screenshots_to_history", false)
        set(value) = p.edit().putBoolean("save_screenshots_to_history", value).apply()

    var remindersEnabled: Boolean
        get() = p.getBoolean("reminders_enabled", true)
        set(value) = p.edit().putBoolean("reminders_enabled", value).apply()

    var reminderDefaultDeliveryMode: String
        get() = p.getString("reminder_default_delivery_mode", "notification") ?: "notification"
        set(value) = p.edit().putString("reminder_default_delivery_mode", value).apply()

    var scheduledAgentRunsEnabled: Boolean
        get() = p.getBoolean("scheduled_agent_runs_enabled", true)
        set(value) = p.edit().putBoolean("scheduled_agent_runs_enabled", value).apply()

    var proactiveAssistantEnabled: Boolean
        get() = p.getBoolean("proactive_assistant_enabled", false)
        set(value) = p.edit().putBoolean("proactive_assistant_enabled", value).apply()

    var proactiveAssistantMode: String
        get() = p.getString("proactive_assistant_mode", "daily_brief") ?: "daily_brief"
        set(value) = p.edit().putString("proactive_assistant_mode", value).apply()

    var proactiveDeliveryMode: String
        get() = p.getString("proactive_delivery_mode", "notification") ?: "notification"
        set(value) = p.edit().putString("proactive_delivery_mode", value).apply()

    var proactiveLastPromptAt: Long
        get() = p.getLong("proactive_last_prompt_at", 0L)
        set(value) = p.edit().putLong("proactive_last_prompt_at", value).apply()

    var wakeOnVoiceEnabled: Boolean
        get() = p.getBoolean("wake_on_voice_enabled", false)
        set(value) = p.edit().putBoolean("wake_on_voice_enabled", value).apply()

    var wakeDetectionMode: String
        get() = p.getString("wake_detection_mode", "system") ?: "system"
        set(value) = p.edit().putString("wake_detection_mode", value).apply()

    var wakePhrase: String
        get() = p.getString("wake_phrase", "Hey $agentName") ?: "Hey $agentName"
        set(value) = p.edit().putString("wake_phrase", value).apply()

    var voiceNoiseGateEnabled: Boolean
        get() = p.getBoolean("voice_noise_gate_enabled", true)
        set(value) = p.edit().putBoolean("voice_noise_gate_enabled", value).apply()

    var voiceNoiseGate: Float
        get() = p.getFloat("voice_noise_gate", 0.12f)
        set(value) = p.edit().putFloat("voice_noise_gate", value.coerceIn(0.03f, 0.60f)).apply()

    var voiceInterruptThreshold: Float
        get() = p.getFloat("voice_interrupt_threshold", 0.32f)
        set(value) = p.edit().putFloat("voice_interrupt_threshold", value.coerceIn(0.08f, 0.80f)).apply()

    var speechRecognitionEngine: String
        get() = p.getString("speech_recognition_engine", "system") ?: "system"
        set(value) = p.edit().putString("speech_recognition_engine", value).apply()

    var speechRecognitionLanguage: String
        get() = p.getString("speech_recognition_language", "auto") ?: "auto"
        set(value) = p.edit().putString("speech_recognition_language", value).apply()

    var speechLanguageSwitchEnabled: Boolean
        get() = p.getBoolean("speech_language_switch_enabled", true)
        set(value) = p.edit().putBoolean("speech_language_switch_enabled", value).apply()

    var speechPreferOnDevice: Boolean
        get() = p.getBoolean("speech_prefer_on_device", false)
        set(value) = p.edit().putBoolean("speech_prefer_on_device", value).apply()

    var whisperModelSize: String
        get() = p.getString("whisper_model_size", "lite") ?: "lite"
        set(value) = p.edit().putString("whisper_model_size", value).apply()

    // Google OAuth & MCP configurations
    const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
    const val KEY_GOOGLE_CLIENT_SECRET = "google_client_secret"
    const val KEY_GOOGLE_REFRESH_TOKEN = "google_refresh_token"
    const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
    const val KEY_GOOGLE_GMAIL_ENABLED = "google_gmail_enabled"
    const val KEY_GOOGLE_CALENDAR_ENABLED = "google_calendar_enabled"
    const val KEY_GOOGLE_CONNECTOR_ENABLED = "google_connector_enabled"
    const val KEY_GITHUB_CLIENT_SECRET = "github_client_secret"
    const val KEY_NOTION_CLIENT_SECRET = "notion_client_secret"
    const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
    const val KEY_MCP_SERVERS_CONFIG = "mcp_servers_config"

    var googleClientId: String
        get() = p.getString(KEY_GOOGLE_CLIENT_ID, "430112870946-niqg2aadqk31uhmitaqdapp3mt17bfu9.apps.googleusercontent.com") ?: "430112870946-niqg2aadqk31uhmitaqdapp3mt17bfu9.apps.googleusercontent.com"
        set(value) = p.edit().putString(KEY_GOOGLE_CLIENT_ID, value).apply()

    var googleClientSecret: String
        get() = p.getString(KEY_GOOGLE_CLIENT_SECRET, "") ?: ""
        set(value) = p.edit().putString(KEY_GOOGLE_CLIENT_SECRET, value).apply()

    var googleRefreshToken: String
        get() = p.getString(KEY_GOOGLE_REFRESH_TOKEN, "") ?: ""
        set(value) = p.edit().putString(KEY_GOOGLE_REFRESH_TOKEN, value).apply()

    var googleAccountEmail: String
        get() = p.getString(KEY_GOOGLE_ACCOUNT_EMAIL, "") ?: ""
        set(value) = p.edit().putString(KEY_GOOGLE_ACCOUNT_EMAIL, value).apply()

    var googleGmailEnabled: Boolean
        get() = p.getBoolean(KEY_GOOGLE_GMAIL_ENABLED, true)
        set(value) = p.edit().putBoolean(KEY_GOOGLE_GMAIL_ENABLED, value).apply()

    var googleCalendarEnabled: Boolean
        get() = p.getBoolean(KEY_GOOGLE_CALENDAR_ENABLED, true)
        set(value) = p.edit().putBoolean(KEY_GOOGLE_CALENDAR_ENABLED, value).apply()

    var googleConnectorEnabled: Boolean
        get() = p.getBoolean(KEY_GOOGLE_CONNECTOR_ENABLED, true)
        set(value) = p.edit().putBoolean(KEY_GOOGLE_CONNECTOR_ENABLED, value).apply()

    var mcpServersConfig: String
        get() = p.getString(KEY_MCP_SERVERS_CONFIG, "") ?: ""
        set(value) = p.edit().putString(KEY_MCP_SERVERS_CONFIG, value).apply()

    var githubClientSecret: String
        get() = p.getString(KEY_GITHUB_CLIENT_SECRET, "")
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GITHUB_OAUTH_CLIENT_SECRET
        set(value) = p.edit().putString(KEY_GITHUB_CLIENT_SECRET, value).apply()

    var githubToken: String
        get() = p.getString("github_token", "")?.takeIf { it.isNotBlank() } ?: BuildConfig.GITHUB_OAUTH_TOKEN
        set(value) = p.edit().putString("github_token", value).apply()

    var githubConnectorEnabled: Boolean
        get() = p.getBoolean("github_connector_enabled", true)
        set(value) = p.edit().putBoolean("github_connector_enabled", value).apply()

    var notionToken: String
        get() = p.getString("notion_token", "") ?: ""
        set(value) = p.edit().putString("notion_token", value).apply()

    var notionClientSecret: String
        get() = p.getString(KEY_NOTION_CLIENT_SECRET, "")
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.NOTION_OAUTH_CLIENT_SECRET
        set(value) = p.edit().putString(KEY_NOTION_CLIENT_SECRET, value).apply()

    var notionConnectorEnabled: Boolean
        get() = p.getBoolean("notion_connector_enabled", true)
        set(value) = p.edit().putBoolean("notion_connector_enabled", value).apply()

    var spotifyRefreshToken: String
        get() = p.getString("spotify_refresh_token", "") ?: ""
        set(value) = p.edit().putString("spotify_refresh_token", value).apply()

    var spotifyClientSecret: String
        get() = p.getString(KEY_SPOTIFY_CLIENT_SECRET, "")
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SPOTIFY_OAUTH_CLIENT_SECRET
        set(value) = p.edit().putString(KEY_SPOTIFY_CLIENT_SECRET, value).apply()

    var spotifyConnectorEnabled: Boolean
        get() = p.getBoolean("spotify_connector_enabled", true)
        set(value) = p.edit().putBoolean("spotify_connector_enabled", value).apply()

    fun syncToSandbox(context: Context) {
        val channelsList = mutableListOf<ChannelConfig>()
        channelsList.add(ChannelConfig(
            type = "whatsapp",
            enabled = whatsappEnabled,
            config = mapOf(
                "allowed_contacts" to whatsappAllowedContacts,
                "cli_command" to whatsappCliCommand,
                "send_command" to whatsappSendCommand,
                "poll_command" to whatsappPollCommand,
                "phone" to whatsappPhone,
                "auto_reply" to whatsappAutoReply.toString(),
                "notify_on_message" to whatsappNotifyOnMessage.toString(),
                "auto_download_media" to whatsappAutoDownloadMedia.toString(),
                "send_read_receipts" to whatsappSendReadReceipts.toString(),
                "show_typing_indicator" to whatsappShowTypingIndicator.toString(),
                "reply_delay_seconds" to whatsappReplyDelaySeconds.toString(),
                "reply_mode" to whatsappReplyMode,
                "reply_language" to whatsappReplyLanguage,
                "custom_reply_prompt" to whatsappCustomReplyPrompt,
                "monitored_chats" to whatsappMonitoredChats,
                "max_stored_messages" to whatsappMaxStoredMessages.toString(),
                "listener_port" to whatsappListenerPort.toString(),
                "auto_start_on_launch" to whatsappAutoStartOnLaunch.toString(),
            )
        ))
        channelsList.add(ChannelConfig(type = "sms", enabled = smsEnabled))
        channelsList.add(ChannelConfig(type = "telegram", enabled = telegramEnabled, config = mapOf("bot_token" to telegramBotToken, "allowed_chats" to telegramAllowedChats)))
        channelsList.add(ChannelConfig(type = "slack", enabled = slackEnabled, config = mapOf("bot_token" to slackBotToken, "signing_secret" to slackSigningSecret)))
        channelsList.add(ChannelConfig(type = "discord", enabled = discordEnabled, config = mapOf("bot_token" to discordBotToken)))
        channelsList.add(ChannelConfig(type = "email", enabled = emailChannelEnabled, config = mapOf("user" to emailChannelAddress, "pass" to emailChannelPassword)))
        channelsList.add(ChannelConfig(type = "webhook", enabled = webhookEnabled, config = mapOf("url" to webhookUrl, "secret" to webhookSecret)))

        val config = AgentConfig(
            name = agentName,
            personality = agentPersonality,
            purpose = agentPurpose,
            providerBaseUrl = baseUrl,
            providerApiKey = apiKey,
            model = model,
            voice = agentVoiceProfile,
            ttsEngine = ttsEngine,
            channels = channelsList
        )
        AgentConfigLoader.save(context, config)
    }
}
