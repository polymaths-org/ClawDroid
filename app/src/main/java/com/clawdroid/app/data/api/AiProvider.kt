package com.clawdroid.app.data.api

enum class ProviderDialect {
    OPENAI_COMPATIBLE,
    GEMINI_NATIVE,
    ANTHROPIC_NATIVE,
}

data class AiProviderPreset(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val dialect: ProviderDialect,
    val description: String,
    val supportsRuntime: Boolean,
)

object AiProviders {
    val presets = listOf(
        AiProviderPreset(
            id = "openai",
            label = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "Official OpenAI API",
            supportsRuntime = true,
        ),
        AiProviderPreset(
            id = "openrouter",
            label = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "Router for many model providers",
            supportsRuntime = true,
        ),
        AiProviderPreset(
            id = "deepseek",
            label = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "DeepSeek OpenAI-compatible API",
            supportsRuntime = true,
        ),
        AiProviderPreset(
            id = "siliconflow",
            label = "SiliconFlow",
            defaultBaseUrl = "https://api.siliconflow.com/v1",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "SiliconFlow OpenAI-compatible API",
            supportsRuntime = true,
        ),
        AiProviderPreset(
            id = "gemini",
            label = "Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            dialect = ProviderDialect.GEMINI_NATIVE,
            description = "Google Gemini native API",
            supportsRuntime = false,
        ),
        AiProviderPreset(
            id = "anthropic",
            label = "Anthropic",
            defaultBaseUrl = "https://api.anthropic.com/v1",
            dialect = ProviderDialect.ANTHROPIC_NATIVE,
            description = "Claude native API",
            supportsRuntime = false,
        ),
        AiProviderPreset(
            id = "opencode",
            label = "OpenCode",
            defaultBaseUrl = "https://opencode.ai/zen/v1",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "OpenCode-compatible endpoint",
            supportsRuntime = true,
        ),
        AiProviderPreset(
            id = "custom",
            label = "Custom",
            defaultBaseUrl = "",
            dialect = ProviderDialect.OPENAI_COMPATIBLE,
            description = "Any OpenAI-compatible endpoint",
            supportsRuntime = true,
        ),
    )

    fun byId(id: String): AiProviderPreset = presets.firstOrNull { it.id == id } ?: presets.first()
}
