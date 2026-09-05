package com.clawdroid.app.ui.components

fun providerDisplayName(providerId: String): String {
    return when (providerId.trim().lowercase()) {
        "opencode_zen", "opencode" -> "OpenCode Zen"
        "openai" -> "OpenAI"
        "anthropic" -> "Anthropic"
        "gemini" -> "Google Gemini"
        "openrouter" -> "OpenRouter"
        "siliconflow" -> "SiliconFlow"
        "groq" -> "Groq"
        "mistral" -> "Mistral AI"
        "deepseek" -> "DeepSeek"
        "xai" -> "xAI"
        "together" -> "Together AI"
        "ollama" -> "Ollama"
        "custom" -> "Custom"
        else -> providerId.trim().replace('_', ' ').split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
            .ifBlank { providerId }
    }
}
