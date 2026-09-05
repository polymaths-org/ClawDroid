package com.clawdroid.app.core.config

import android.content.Context
import android.content.SharedPreferences
import com.clawdroid.app.BuildConfig
import com.clawdroid.app.core.agent.AgentConfig
import com.clawdroid.app.core.agent.AgentConfigLoader
import com.clawdroid.app.core.agent.ChannelConfig
import com.clawdroid.app.data.api.AiProviders
import com.clawdroid.app.data.api.ProviderDialect

object AppConfigManager {
    private const val PREFS = "clawdroid_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_PROVIDER_DIALECT = "provider_dialect"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    val providerDialect: ProviderDialect
        get() = runCatching {
            ProviderDialect.valueOf(
                p.getString(KEY_PROVIDER_DIALECT, null)
                    ?: AiProviders.byId(provider).dialect.name,
            )
        }.getOrDefault(ProviderDialect.OPENAI_COMPATIBLE)

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    val isOnboardingComplete: Boolean
        get() = p.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun save(baseUrl: String, apiKey: String, model: String) {
        p.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .apply()
    }

    fun save(provider: String, baseUrl: String, apiKey: String, model: String) {
        val dialect = AiProviders.byId(provider).dialect
        save(provider, baseUrl, apiKey, model, dialect)
    }

    fun save(provider: String, baseUrl: String, apiKey: String, model: String, dialect: ProviderDialect) {
        p.edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .putString(KEY_PROVIDER_DIALECT, dialect.name)
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    // TTS Settings Configuration
    const val KEY_TTS_ENGINE = "tts_engine"
    const val KEY_TTS_VOICE = "tts_voice"
    const val KEY_TTS_SPEED = "tts_speed"
    const val KEY_OFFLINE_TTS_MODEL_ID = "offline_tts_model_id"
    const val KEY_OFFLINE_TTS_SPEAKER_ID = "offline_tts_speaker_id"

    var ttsEngine: String
        get() = p.getString(KEY_TTS_ENGINE, "device") ?: "device"
        set(value) = p.edit().putString(KEY_TTS_ENGINE, value).apply()

    var ttsVoice: String
        get() = p.getString(KEY_TTS_VOICE, "") ?: ""
        set(value) = p.edit().putString(KEY_TTS_VOICE, value).apply()

    var ttsSpeed: Float
        get() = p.getFloat(KEY_TTS_SPEED, 1.0f)
        set(value) = p.edit().putFloat(KEY_TTS_SPEED, value).apply()

    var offlineTtsModelId: String
        get() = p.getString(KEY_OFFLINE_TTS_MODEL_ID, "vits-piper-en_US-glados") ?: "vits-piper-en_US-glados"
        set(value) = p.edit().putString(KEY_OFFLINE_TTS_MODEL_ID, value).apply()

    var offlineTtsSpeakerId: Int
        get() = p.getInt(KEY_OFFLINE_TTS_SPEAKER_ID, 0)
        set(value) = p.edit().putInt(KEY_OFFLINE_TTS_SPEAKER_ID, value).apply()

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

    var permissionsAsked: Boolean
        get() = p.getBoolean("permissions_asked", false)
        set(value) = p.edit().putBoolean("permissions_asked", value).apply()

    // Google OAuth & MCP configurations
    const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
    const val KEY_GOOGLE_CLIENT_SECRET = "google_client_secret"
    const val KEY_GOOGLE_REFRESH_TOKEN = "google_refresh_token"
    const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
    const val KEY_GOOGLE_GMAIL_ENABLED = "google_gmail_enabled"
    const val KEY_GOOGLE_CALENDAR_ENABLED = "google_calendar_enabled"
    const val KEY_GOOGLE_CONNECTOR_ENABLED = "google_connector_enabled"
    const val KEY_MCP_SERVERS_CONFIG = "mcp_servers_config"

    var googleClientId: String
        get() = p.getString(KEY_GOOGLE_CLIENT_ID, "430112870946-niqg2aadqk31uhmitaqdapp3mt17bfu9.apps.googleusercontent.com") ?: "430112870946-niqg2aadqk31uhmitaqdapp3mt17bfu9.apps.googleusercontent.com"
        set(value) = p.edit().putString(KEY_GOOGLE_CLIENT_ID, value).apply()

    var googleClientSecret: String
        get() = p.getString(KEY_GOOGLE_CLIENT_SECRET, "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") ?: "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"
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

    var githubToken: String
        get() = p.getString("github_token", "")?.takeIf { it.isNotBlank() } ?: BuildConfig.GITHUB_OAUTH_TOKEN
        set(value) = p.edit().putString("github_token", value).apply()

    var githubConnectorEnabled: Boolean
        get() = p.getBoolean("github_connector_enabled", true)
        set(value) = p.edit().putBoolean("github_connector_enabled", value).apply()

    var notionToken: String
        get() = p.getString("notion_token", "") ?: ""
        set(value) = p.edit().putString("notion_token", value).apply()

    var notionConnectorEnabled: Boolean
        get() = p.getBoolean("notion_connector_enabled", true)
        set(value) = p.edit().putBoolean("notion_connector_enabled", value).apply()

    var spotifyRefreshToken: String
        get() = p.getString("spotify_refresh_token", "") ?: ""
        set(value) = p.edit().putString("spotify_refresh_token", value).apply()

    var spotifyConnectorEnabled: Boolean
        get() = p.getBoolean("spotify_connector_enabled", true)
        set(value) = p.edit().putBoolean("spotify_connector_enabled", value).apply()

    fun syncToSandbox(context: Context) {
        val channelsList = mutableListOf<ChannelConfig>()
        channelsList.add(ChannelConfig(type = "whatsapp", enabled = whatsappEnabled, config = mapOf("phone" to whatsappAllowedContacts)))
        channelsList.add(ChannelConfig(type = "sms", enabled = smsEnabled))

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
