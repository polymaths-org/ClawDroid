package com.clawdroid.app.core.agent

import org.json.JSONArray
import org.json.JSONObject

data class AgentConfig(
    val name: String = "Nova",
    val personality: String = "Professional",
    val purpose: String = "General assistant",
    val providerBaseUrl: String = "",
    val providerApiKey: String = "",
    val model: String = "gpt-4o",
    val voice: String = "female",
    val ttsEngine: String = "device",
    val skills: List<SkillConfig> = emptyList(),
    val channels: List<ChannelConfig> = emptyList(),
    val heartbeats: List<HeartbeatConfig> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("personality", personality)
        put("purpose", purpose)
        put("provider_base_url", providerBaseUrl)
        put("provider_api_key", providerApiKey)
        put("model", model)
        put("voice", voice)
        put("tts_engine", ttsEngine)
        put("skills", JSONArray(skills.map { it.toJson() }))
        put("channels", JSONArray(channels.map { it.toJson() }))
        put("heartbeats", JSONArray(heartbeats.map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JSONObject): AgentConfig = AgentConfig(
            name = json.optString("name", "Nova"),
            personality = json.optString("personality", "Professional"),
            purpose = json.optString("purpose", "General assistant"),
            providerBaseUrl = json.optString("provider_base_url", ""),
            providerApiKey = json.optString("provider_api_key", ""),
            model = json.optString("model", "gpt-4o"),
            voice = json.optString("voice", "female"),
            ttsEngine = json.optString("tts_engine", "device"),
            skills = json.optJSONArray("skills")?.let { arr ->
                (0 until arr.length()).map { SkillConfig.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            channels = json.optJSONArray("channels")?.let { arr ->
                (0 until arr.length()).map { ChannelConfig.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            heartbeats = json.optJSONArray("heartbeats")?.let { arr ->
                (0 until arr.length()).map { HeartbeatConfig.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
        )
    }
}

data class SkillConfig(
    val name: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("enabled", enabled)
        put("config", JSONObject(config))
    }

    companion object {
        fun fromJson(json: JSONObject): SkillConfig = SkillConfig(
            name = json.getString("name"),
            enabled = json.optBoolean("enabled", true),
            config = json.optJSONObject("config")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap(),
        )
    }
}

data class ChannelConfig(
    val type: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("enabled", enabled)
        put("config", JSONObject(config))
    }

    companion object {
        fun fromJson(json: JSONObject): ChannelConfig = ChannelConfig(
            type = json.getString("type"),
            enabled = json.optBoolean("enabled", true),
            config = json.optJSONObject("config")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.getString(it) }
            } ?: emptyMap(),
        )
    }
}

data class HeartbeatConfig(
    val id: String,
    val cron: String,
    val prompt: String,
    val enabled: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("cron", cron)
        put("prompt", prompt)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): HeartbeatConfig = HeartbeatConfig(
            id = json.getString("id"),
            cron = json.getString("cron"),
            prompt = json.optString("prompt", ""),
            enabled = json.optBoolean("enabled", true),
        )
    }
}
