package com.clawdroid.app.ui.chat

import java.util.UUID

sealed interface ChatItem {
    val id: String
}

data class FilePreview(
    val path: String,
    val content: String = "",
    val previewType: FilePreviewType = FilePreviewType.Text,
)

enum class FilePreviewType {
    Html,
    Image,
    Svg,
    Text,
}

data class UserChatItem(
    override val id: String = UUID.randomUUID().toString(),
    val text: String,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) : ChatItem

data class AgentChatItem(
    override val id: String = UUID.randomUUID().toString(),
    val text: String,
    val streaming: Boolean = false,
    val filePreviews: List<FilePreview> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) : ChatItem

data class ActivityChatItem(
    override val id: String = UUID.randomUUID().toString(),
    val steps: List<ActivityStepItem> = emptyList(),
    val running: Boolean = false,
) : ChatItem

data class ActivityStepItem(
    val id: String = UUID.randomUUID().toString(),
    val callId: String = "",
    val type: ActivityStepType,
    val summary: String,
    val detail: String,
    val arguments: String = "",
    val result: String? = null,
    val running: Boolean = false,
    val isError: Boolean = false,
)

enum class ActivityStepType(val icon: String) {
    Command("⚙️"),
    File("📂"),
    Web("🌐"),
    Edit("📝"),
    Package("📦"),
    Service("🔌"),
    Desktop("🖥️"),
}

enum class AgentRuntimeState {
    Idle,
    Running,
}

data class AgentErrorUi(
    val shortMessage: String,
    val details: String?,
    val isProviderError: Boolean,
)

fun formatAgentError(raw: String): AgentErrorUi {
    val lower = raw.lowercase()
    val isProviderError = "provider" in lower || "http " in lower ||
        "context window" in lower || "api key" in lower ||
        "timeout" in lower || "network" in lower || "connect" in lower
    val short = when {
        Regex("http\\s+5\\d\\d").containsMatchIn(raw) || "internal server error" in lower ->
            "The model service had a problem. Try again in a moment."
        Regex("http\\s+401|http\\s+403").containsMatchIn(raw) || "api key" in lower || "unauthorized" in lower ->
            "Your API key was rejected. Check it in Settings > Provider."
        "context window" in lower || Regex("http\\s+400").containsMatchIn(raw) ->
            "This conversation is too long for the model. Continue in a new chat."
        "timeout" in lower || "network" in lower || "connect" in lower || "unreachable" in lower ->
            "Could not reach the model service. Check your connection and retry."
        else -> raw.lineSequence().firstOrNull().orEmpty().trim().take(140)
            .ifBlank { "Something went wrong. Try again." }
    }
    val details = raw.takeIf { it.trim() != short.trim() && it.isNotBlank() }
    return AgentErrorUi(shortMessage = short, details = details, isProviderError = isProviderError)
}
