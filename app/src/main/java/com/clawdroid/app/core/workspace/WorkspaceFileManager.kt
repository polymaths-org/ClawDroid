package com.clawdroid.app.core.workspace

import android.content.Context
import java.io.File

data class WorkspaceFile(
    val name: String,
    val title: String,
    val description: String,
    val content: String,
    val lastModified: Long,
)

object WorkspaceFileManager {
    private val specs = listOf(
        Spec(
            name = "SOUL.md",
            title = "SOUL.md",
            description = "Personality, tone, boundaries, and conversational style.",
            template = """
                # SOUL.md

                You are ClawDroid: useful, direct, transparent, and calm under pressure.

                Keep your voice concise, practical, and human. Show your work through activity steps, but do not bury the user in unnecessary detail.
            """.trimIndent(),
        ),
        Spec(
            name = "AGENTS.md",
            title = "AGENTS.md",
            description = "Operating rules, memory protocol, and task behavior.",
            template = """
                # AGENTS.md

                Use tools to act, verify results, and report concise outcomes.
                Ask before sending messages to external people or services unless the user gave exact content to send.
                Save important outputs where the user can access them.
            """.trimIndent(),
        ),
        Spec(
            name = "TOOLS.md",
            title = "TOOLS.md",
            description = "Notes for tool usage, local conventions, and preferred workflows.",
            template = """
                # TOOLS.md

                Prefer robust native tools over brittle UI automation when both are available.
                Use Android screen control tools for phone UI tasks and sandbox tools for file/code work.
            """.trimIndent(),
        ),
        Spec(
            name = "USER.md",
            title = "USER.md",
            description = "User profile, preferences, and stable context.",
            template = """
                # USER.md

                Add durable user preferences here.
            """.trimIndent(),
        ),
        Spec(
            name = "IDENTITY.md",
            title = "IDENTITY.md",
            description = "Agent display identity and role metadata.",
            template = """
                # IDENTITY.md

                Name: ClawDroid
                Role: Native Android AI agent
            """.trimIndent(),
        ),
        Spec(
            name = "HEARTBEAT.md",
            title = "HEARTBEAT.md",
            description = "Background checklist for autonomous heartbeat tasks.",
            template = """
                # HEARTBEAT.md

                - [ ] Add recurring background tasks here.
            """.trimIndent(),
        ),
    )

    fun all(context: Context): List<WorkspaceFile> {
        ensureDefaults(context)
        return specs.map { spec ->
            val file = fileFor(context, spec.name)
            WorkspaceFile(
                name = spec.name,
                title = spec.title,
                description = spec.description,
                content = file.readText(),
                lastModified = file.lastModified(),
            )
        }
    }

    fun read(context: Context, name: String): WorkspaceFile {
        ensureDefaults(context)
        val spec = specs.first { it.name == name }
        val file = fileFor(context, spec.name)
        return WorkspaceFile(spec.name, spec.title, spec.description, file.readText(), file.lastModified())
    }

    fun save(context: Context, name: String, content: String) {
        ensureDefaults(context)
        fileFor(context, name).writeText(content)
    }

    fun reset(context: Context, name: String) {
        val spec = specs.first { it.name == name }
        fileFor(context, name).writeText(spec.template)
    }

    fun promptContext(context: Context): String {
        ensureDefaults(context)
        return specs.mapNotNull { spec ->
            val text = fileFor(context, spec.name).readText().trim()
            if (text.isBlank()) null else "## ${spec.name}\n$text"
        }.joinToString("\n\n")
    }

    private fun ensureDefaults(context: Context) {
        val dir = workspaceDir(context)
        dir.mkdirs()
        specs.forEach { spec ->
            val file = File(dir, spec.name)
            if (!file.exists()) file.writeText(spec.template)
        }
    }

    private fun fileFor(context: Context, name: String): File = File(workspaceDir(context), name)

    private fun workspaceDir(context: Context): File = File(context.filesDir, "home/workspace")

    private data class Spec(
        val name: String,
        val title: String,
        val description: String,
        val template: String,
    )
}
