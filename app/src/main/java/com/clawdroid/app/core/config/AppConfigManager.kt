package com.clawdroid.app.core.config

import android.content.Context
import android.content.SharedPreferences
import com.clawdroid.app.BuildConfig

object AppConfigManager {
    private const val PREFS = "clawdroid_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val p: SharedPreferences get() = prefs!!

    val baseUrl: String
        get() = p.getString(KEY_BASE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENROUTER_BASE_URL.trimEnd('/')
            .takeIf { it.isNotBlank() }
            ?: "https://openrouter.ai/api/v1"

    val apiKey: String
        get() = p.getString(KEY_API_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENROUTER_API_KEY

    val model: String
        get() = p.getString(KEY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.OPENROUTER_MODEL
            .takeIf { it.isNotBlank() }
            ?: "openai/gpt-4o"

    val provider: String
        get() = p.getString(KEY_PROVIDER, null)
            ?.takeIf { it.isNotBlank() }
            ?: "openrouter"

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    val isOnboardingComplete: Boolean
        get() = p.getBoolean(KEY_ONBOARDING_COMPLETE, false) || isConfigured

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

    // Cloud TTS API keys
    const val KEY_OPENAI_TTS_API_KEY = "openai_tts_api_key"
    const val KEY_ELEVENLABS_API_KEY = "elevenlabs_api_key"
    const val KEY_DEEPGRAM_API_KEY = "deepgram_api_key"

    var openaiTtsApiKey: String
        get() = p.getString(KEY_OPENAI_TTS_API_KEY, "") ?: ""
        set(value) = p.edit().putString(KEY_OPENAI_TTS_API_KEY, value).apply()

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
    const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"
    const val KEY_WHATSAPP_ALLOWED_CONTACTS = "whatsapp_allowed_contacts"
    const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
    const val KEY_HEARTBEAT_INTERVAL_MIN = "heartbeat_interval_min"

    var activeProjectId: String?
        get() = p.getString(KEY_ACTIVE_PROJECT_ID, null)
        set(value) = p.edit().putString(KEY_ACTIVE_PROJECT_ID, value).apply()

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
}
