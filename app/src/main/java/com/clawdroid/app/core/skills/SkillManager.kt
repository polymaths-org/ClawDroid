package com.clawdroid.app.core.skills

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.agent.AgentConfigLoader
import com.clawdroid.app.core.agent.SkillConfig
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.core.bootstrap.LinuxEnvironment
import java.io.File

/**
 * Manages loading, registration, and execution of skills.
 * Skills are configured in AGENTS.md / agent_config.json.
 */
object SkillManager {
    private const val TAG = "SkillManager"
    private val registered = mutableMapOf<String, Skill>()

    fun register(skill: Skill) {
        registered[skill.name] = skill
        Log.i(TAG, "Registered skill: ${skill.name} v${skill.version}")
    }

    fun get(name: String): Skill? = registered[name]

    fun getAll(): List<Skill> = registered.values.toList()

    /**
     * Load configured skills from agent config and register built-in skills.
     */
    fun loadConfigured(context: Context) {
        val config = AgentConfigLoader.load(context)
        val env = EnvironmentSetup.build(context)

        // Register built-in skills
        registerBuiltins(env)

        // Register user-configured prompt skills
        config.skills.filter { it.enabled }.forEach { cfg ->
            when (cfg.name) {
                "web_search" -> register(PromptSkill("web_search", "Web search capability",
                    "You can search the web for current information using the web_search tool."))
                "code_execution" -> register(PromptSkill("code_execution", "Execute code in sandbox",
                    "You can write and run code (Python, Node.js, shell) in the sandbox environment."))
                "file_operations" -> register(PromptSkill("file_operations", "Read/write files",
                    "You can create, read, edit, and delete files in the workspace."))
                "browser" -> register(PromptSkill("browser", "Web browsing",
                    "You can browse websites using the built-in WebView browser."))
                else -> {
                    // Custom script-based skill
                    val scriptFile = File(env.home, "skills/${cfg.name}.sh")
                    if (scriptFile.exists()) {
                        register(ScriptSkill(cfg.name, cfg.config["description"] ?: "Custom skill", scriptFile.absolutePath))
                    }
                }
            }
        }
    }

    /**
     * Builds a system prompt fragment from all registered prompt skills.
     */
    fun buildSkillPrompt(): String {
        val prompts = registered.values.filterIsInstance<PromptSkill>()
        if (prompts.isEmpty()) return ""
        return prompts.joinToString("\n\n") { "=== ${it.name} ===\n${it.systemPrompt}" }
    }

    private fun registerBuiltins(env: LinuxEnvironment) {
        // Check for user-created skill scripts in ~/skills/
        val skillsDir = File(env.home, "skills")
        if (!skillsDir.isDirectory) return

        skillsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".md") }?.forEach { file ->
            val skillName = file.nameWithoutExtension
            try {
                val content = file.readText()
                val title = content.lines().firstOrNull()?.removePrefix("#")?.trim() ?: skillName
                register(PromptSkill(skillName, title, content))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load skill from ${file.name}", e)
            }
        }
    }
}
