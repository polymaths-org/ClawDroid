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
) : ChatItem

data class AgentChatItem(
    override val id: String = UUID.randomUUID().toString(),
    val text: String,
    val streaming: Boolean = false,
    val activitySteps: List<ActivityStepItem> = emptyList(),
    val activityRunning: Boolean = false,
    val filePreviews: List<FilePreview> = emptyList(),
) : ChatItem

data class ActivityStepItem(
    val id: String = UUID.randomUUID().toString(),
    val type: ActivityStepType,
    val summary: String,
    val detail: String,
    val running: Boolean = false,
)

enum class ActivityStepType(val icon: String) {
    Command("⚙️"),
    File("📂"),
    Web("🌐"),
    Edit("📝"),
    Package("📦"),
    Service("🔌"),
}

enum class AgentRuntimeState {
    Idle,
    Running,
}
