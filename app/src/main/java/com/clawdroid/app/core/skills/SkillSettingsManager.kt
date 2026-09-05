package com.clawdroid.app.core.skills

import android.content.Context
import java.io.File

data class EditableSkill(
    val id: String,
    val title: String,
    val path: String,
    val content: String,
    val enabled: Boolean,
    val status: String,
    val lastModified: Long,
    val bundled: Boolean = false,
    val sourceUrl: String? = null,
)

object SkillSettingsManager {
    private const val PREFS = "clawdroid_skill_settings"
    private const val SEEDED_VERSION = "default_skills_seeded_version"
    private const val CURRENT_DEFAULT_VERSION = 1

    fun list(context: Context): List<EditableSkill> {
        seedDefaultSkills(context)
        ensureSkillsDir(context)
        val markdownSkills = skillsDir(context).listFiles()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .orEmpty()
            .map { file -> file.toEditableSkill(context, file.nameWithoutExtension, "Markdown skill") }

        val agentSkills = File(context.filesDir, "home/.agents/skills")
            .walkTopDown()
            .filter { it.isFile && it.name == "SKILL.md" }
            .map { file -> file.toEditableSkill(context, file.parentFile?.name ?: file.nameWithoutExtension, "Agent skill") }
            .toList()

        return (markdownSkills + agentSkills).sortedBy { it.title.lowercase() }
    }

    fun seedDefaultSkills(context: Context) {
        ensureSkillsDir(context)
        val prefs = prefs(context)
        DefaultSkillCatalog.skills.forEach { defaultSkill ->
            val file = File(skillsDir(context), "${defaultSkill.id}.md")
            if (!file.exists()) {
                file.writeText(defaultSkill.markdown)
                if (!prefs.contains("enabled_${defaultSkill.id}")) {
                    prefs.edit().putBoolean("enabled_${defaultSkill.id}", defaultSkill.defaultEnabled).apply()
                }
            }
        }
        prefs.edit().putInt(SEEDED_VERSION, CURRENT_DEFAULT_VERSION).apply()
    }

    fun resetBundled(context: Context, id: String): EditableSkill? {
        val defaultSkill = DefaultSkillCatalog.byId(id) ?: return null
        ensureSkillsDir(context)
        val file = File(skillsDir(context), "${defaultSkill.id}.md")
        file.writeText(defaultSkill.markdown)
        setEnabled(context, id, defaultSkill.defaultEnabled)
        return file.toEditableSkill(context, id, "Starter skill")
    }

    fun create(context: Context, name: String): EditableSkill {
        ensureSkillsDir(context)
        val safeName = name.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .ifBlank { "new-skill" }
        val file = File(skillsDir(context), "$safeName.md")
        if (!file.exists()) {
            file.writeText(
                """
                    # ${safeName.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }}

                    Describe when this skill should be used and what behavior it adds.
                """.trimIndent(),
            )
        }
        setEnabled(context, safeName, true)
        return file.toEditableSkill(context, safeName, "Markdown skill")
    }

    fun save(context: Context, id: String, path: String, content: String) {
        File(path).writeText(content)
        setEnabled(context, id, true)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled_$id", enabled).apply()
    }

    fun isEnabled(context: Context, id: String): Boolean = prefs(context).getBoolean("enabled_$id", true)

    private fun File.toEditableSkill(context: Context, id: String, defaultStatus: String): EditableSkill {
        val content = readText()
        val bundled = DefaultSkillCatalog.byId(id)
        val title = content.lineSequence()
            .firstOrNull { it.trim().startsWith("#") }
            ?.trim()
            ?.removePrefix("#")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: id
        return EditableSkill(
            id = id,
            title = title,
            path = absolutePath,
            content = content,
            enabled = prefs(context).getBoolean("enabled_$id", true),
            status = bundled?.let { "Starter • ${it.category}" } ?: defaultStatus,
            lastModified = lastModified(),
            bundled = bundled != null,
            sourceUrl = bundled?.sourceUrl,
        )
    }

    private fun ensureSkillsDir(context: Context) {
        skillsDir(context).mkdirs()
    }

    private fun skillsDir(context: Context): File = File(context.filesDir, "home/skills")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
