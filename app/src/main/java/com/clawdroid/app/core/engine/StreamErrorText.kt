package com.clawdroid.app.core.engine

fun mapStreamErrorToUserText(raw: String, hasMedia: Boolean): String {
    val lower = raw.lowercase()
    val cause = raw.trim().take(300)
    fun withCause(message: String): String {
        if (cause.isBlank() || message.contains(cause)) return message
        return "$message\n\nUnderlying error: $cause"
    }
    if (hasMedia && (lower.contains("image_url") || lower.contains("vision") || lower.contains("image input"))) {
        return withCause("This model rejected the screenshot image. Pick a vision-capable model, or retry with screen-control context only.")
    }
    if (lower.contains("401") || lower.contains("api key")) {
        return withCause("The model provider rejected the API key. Check Settings and try again.")
    }
    if (lower.contains("429") || lower.contains("rate limit")) {
        return withCause("The model provider is rate limiting requests. Wait a moment and try again.")
    }
    if (
        lower.contains("context_length_exceeded") ||
        lower.contains("maximum context") ||
        lower.contains("context window") ||
        lower.contains("too many tokens") ||
        lower.contains("prompt is too long") ||
        lower.contains("input is too long") ||
        (lower.contains("400") && lower.contains("token")) ||
        (lower.contains("400") && lower.contains("context"))
    ) {
        return withCause("The model provider says this chat is too large for its context window. Start a new chat, or compact/clear old context, then retry. For simple Android actions like opening an app, ClawDroid will now try the local launcher first.")
    }
    if (lower.contains("provider") && lower.contains("error")) {
        return withCause("The model provider returned an error. If this happened during a simple Android action, start a new chat and retry; ClawDroid will use local Android control when it can. If it repeats, check Settings > Provider.")
    }
    return "Assistant run failed: $raw"
}
