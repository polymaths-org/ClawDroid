package com.clawdroid.app.data.api

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager
import java.io.File

object MessageBuilder {

    private var memoryContext: String? = null

    fun setMemoryContext(memory: String) {
        memoryContext = memory
    }

    fun clearMemoryContext() {
        memoryContext = null
    }

    fun forUserPrompt(context: Context, projectId: String?, prompt: String): List<ChatMessage> {
        val agentName = AppConfigManager.agentName
        val personality = AppConfigManager.agentPersonality
        val purpose = AppConfigManager.agentPurpose
        val memory = memoryContext

        var customInstructions: String? = null
        if (projectId != null) {
            val projectDir = File(context.filesDir, "home/projects/$projectId")
            val agentMdFile = File(projectDir, "agent.md").takeIf { it.exists() }
                ?: File(projectDir, "AGENT.md").takeIf { it.exists() }
            if (agentMdFile != null) {
                runCatching {
                    customInstructions = agentMdFile.readText().trim()
                }
            }
        }

        val ownerName = AppConfigManager.ownerName.takeIf { it.isNotBlank() }
        val ownerInfo = AppConfigManager.ownerInfo.takeIf { it.isNotBlank() }

        val systemContent = buildString {
            appendLine("You are $agentName, a transparent Android agent with access to a Linux sandbox.")
            appendLine("Your personality: $personality.")
            appendLine("Your primary purpose: $purpose.")
            if (ownerName != null) {
                appendLine("Your owner's name: $ownerName.")
            }
            if (ownerInfo != null) {
                appendLine("About your owner: $ownerInfo")
            }
            appendLine()
            appendLine("Rules:")
            appendLine("- Use tools when useful. Keep the user informed.")
            appendLine("- Prefer concrete action over vague advice.")
            appendLine("- You have full filesystem access inside the sandbox at ${File.separator}data${File.separator}data${File.separator}com.clawdroid.app${File.separator}files.")
            appendLine("- Save important files to the shared folder /storage/emulated/0/Documents/ClawDroid/Output/ so the user can access them.")
            appendLine("- For downloaded models, packages, and agent artifacts, use the sandbox internal storage.")
            appendLine("- Never ask for confirmation before writing files in the sandbox or shared Documents/ClawDroid folders.")
            appendLine("- CRITICAL: Before sending ANY message to an external service (WhatsApp, SMS, email, Slack, Telegram, etc.), you MUST ask the user what to say first. Never auto-reply.")
            appendLine("- If the user tells you to send a specific message, you may send it without further confirmation.")
            appendLine("- Do NOT use web.whatsapp.com or any browser-based messaging interface to send messages without explicit user approval.")
            
            if (!customInstructions.isNullOrBlank()) {
                appendLine()
                appendLine("## Custom Project Instructions (agent.md)")
                appendLine("The following instructions are specific to this project sandbox. Adhere to them strictly:")
                appendLine(customInstructions)
            }

            if (memory != null && memory.isNotBlank()) {
                appendLine()
                appendLine("## Persistent Memory")
                appendLine("The following is what you remember from previous sessions. Read it carefully:")
                appendLine(memory)
            }
            appendLine()
            appendLine("When a task is complete, save a brief summary of what was done so it is remembered for next time.")
        }

        return listOf(
            ChatMessage(role = "system", content = systemContent.trimEnd()),
            ChatMessage(role = "user", content = prompt),
        )
    }
}
