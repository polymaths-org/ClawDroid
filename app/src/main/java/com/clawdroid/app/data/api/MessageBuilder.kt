package com.clawdroid.app.data.api

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.control.ScreenReaderService
import com.clawdroid.app.core.skills.SkillManager
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object MessageBuilder {

    private var memoryContext: String? = null

    fun setMemoryContext(memory: String) {
        memoryContext = memory
    }

    fun clearMemoryContext() {
        memoryContext = null
    }

    fun buildSystemPrompt(context: Context, projectId: String?): String {
        SkillManager.loadConfigured(context)
        val agentName = AppConfigManager.agentName
        val personality = AppConfigManager.agentPersonality
        val purpose = AppConfigManager.agentPurpose
        val responseMode = AppConfigManager.agentResponseMode
        val memory = memoryContext
        val skillPrompt = SkillManager.buildSkillPrompt().takeIf { it.isNotBlank() }
        val globalAgentsMd = AppConfigManager.agentsMd.takeIf { it.isNotBlank() }
        val globalToolsMd = AppConfigManager.toolsMd.takeIf { it.isNotBlank() }
        val globalSkillMd = AppConfigManager.skillMd.takeIf { it.isNotBlank() }
        val globalSystemMd = AppConfigManager.systemMd.takeIf { it.isNotBlank() }

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
        val now = ZonedDateTime.now()

        val systemContent = buildString {
            appendLine("You are $agentName, a transparent Android agent with access to a Linux sandbox.")
            appendLine("Your personality: $personality.")
            appendLine("Your primary purpose: $purpose.")
            appendLine("Current local time: ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} (${now.zone}).")
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
            when (responseMode) {
                "fast" -> {
                    appendLine("- Response style: FAST. Keep replies short, direct, and action-first. Prefer one or two concise sentences unless details are necessary.")
                    appendLine("- Avoid long explanations and avoid narrating reasoning unless the user asks.")
                }
                "thinking" -> {
                    appendLine("- Response style: THINKING. Show brief reasoning, assumptions, and next-step thinking before important actions, while still staying concise.")
                    appendLine("- Explain tradeoffs when decisions affect reliability, permissions, data, or external services.")
                }
                else -> {
                    appendLine("- Response style: BALANCED. Be clear and useful with enough context to understand decisions, but avoid unnecessary length.")
                }
            }
            appendLine("- You have full filesystem access inside the sandbox at ${File.separator}data${File.separator}data${File.separator}com.clawdroid.app${File.separator}files.")
            appendLine("- Save important files to the shared folder /storage/emulated/0/Documents/ClawDroid/Output/ so the user can access them.")
            appendLine("- For downloaded models, packages, and agent artifacts, use the sandbox internal storage.")
            appendLine("- Never ask for confirmation before writing files in the sandbox or shared Documents/ClawDroid folders.")
            appendLine("- CRITICAL: Before sending ANY message to an external service (WhatsApp, SMS, email, Slack, Telegram, etc.), you MUST ask the user what to say first. Never auto-reply.")
            appendLine("- If the user tells you to send a specific message, you may send it without further confirmation.")
            appendLine("- Do NOT use web.whatsapp.com or any browser-based messaging interface to send messages without explicit user approval.")
            appendLine("- For reminders, todos, alarms, schedules, or future autonomous work, use set_reminder. Use absolute ISO-8601 trigger times with timezone when a time is known.")
            appendLine("- If the user schedules background work, set run_agent=true only when they asked the agent to actually execute something at that time; otherwise create a normal reminder.")
            appendLine("- If the user teaches you durable preferences, workflows, identity facts, or operating rules, update the relevant markdown memory/config files such as AGENTS.md, SOUL.md, TOOLS.md, SKILL.md, SYSTEM.md, or files under home/.memory/.")

            if (globalSystemMd != null || globalAgentsMd != null || globalToolsMd != null || globalSkillMd != null) {
                appendLine()
                appendLine("## Global Agent Markdown")
                if (globalSystemMd != null) {
                    appendLine("### SYSTEM.md")
                    appendLine(globalSystemMd)
                }
                if (globalAgentsMd != null) {
                    appendLine("### AGENTS.md")
                    appendLine(globalAgentsMd)
                }
                if (globalToolsMd != null) {
                    appendLine("### TOOLS.md")
                    appendLine(globalToolsMd)
                }
                if (globalSkillMd != null) {
                    appendLine("### SKILL.md")
                    appendLine(globalSkillMd)
                }
            }

            if (skillPrompt != null) {
                appendLine()
                appendLine("## Enabled Agent Skills")
                appendLine(skillPrompt)
            }

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

            if (ScreenReaderService.instance != null) {
                appendLine()
                appendLine("## Android Screen Control")
                appendLine("You have access to Android screen control tools. Use them to autonomously complete tasks on the user's phone.")
                appendLine("Standard workflow for any app task:")
                appendLine("1. Call get_screen to see the current UI state")
                appendLine("1a. To open an app, call launch_app with either the package name or visible app name (for example WhatsApp, Chrome, Settings).")
                appendLine("2. Reason about which element to interact with based on the tree")
                appendLine("3. Prefer perform_android_actions when multiple taps, waits, or typing actions can be done together")
                appendLine("4. Verify after important state changes, failures, and task completion")
                appendLine("5. Repeat only when the screen actually changed or more information is needed")
                appendLine("6. Prefer the screen tree for text, buttons, forms, and navigation. Use screenshot only for visual context the tree cannot expose, such as gallery images, drawings, photos, colors, maps, or canvas content.")
                appendLine("7. A dedicated screenshot tool is available when screen capture permission is active. Use it when visual details are required and get_screen only returns a tree.")
                appendLine("8. If get_screen or screenshot returns an image, analyze image coordinates and use tap with absolute x/y")
                appendLine("If an action fails, try an alternative approach before giving up.")
            }

            appendLine()
            appendLine("When a task is complete, save a brief summary of what was done so it is remembered for next time.")
        }

        return systemContent.trimEnd()
    }

    fun forUserPrompt(context: Context, projectId: String?, prompt: String): List<ChatMessage> {
        val systemContent = buildSystemPrompt(context, projectId)
        return listOf(
            ChatMessage(role = "system", content = systemContent),
            ChatMessage(role = "user", content = prompt),
        )
    }
}
