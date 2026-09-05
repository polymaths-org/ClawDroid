package com.clawdroid.app.core.agent

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import org.json.JSONObject
import java.io.File

/**
 * Loads and saves agent configuration from a JSON file in the sandbox.
 * Corresponds to an "AGENTS.md" equivalent — structured config the agent
 * and app use to determine behavior, skills, channels, and heartbeats.
 */
object AgentConfigLoader {

    private const val CONFIG_FILENAME = "agent_config.json"

    fun getConfigFile(context: Context): File {
        val env = EnvironmentSetup.build(context)
        return File(env.home, CONFIG_FILENAME)
    }

    fun load(context: Context): AgentConfig {
        val file = getConfigFile(context)
        if (!file.exists()) return AgentConfig()
        return try {
            val text = file.readText()
            AgentConfig.fromJson(JSONObject(text))
        } catch (_: Exception) {
            AgentConfig()
        }
    }

    fun save(context: Context, config: AgentConfig) {
        val file = getConfigFile(context)
        file.parentFile?.mkdirs()
        file.writeText(config.toJson().toString(2))
    }

    /**
     * Exports config as Markdown (AGENTS.md style) for the agent to read.
     */
    fun toMarkdown(config: AgentConfig): String = buildString {
        appendLine("# Agent Configuration")
        appendLine()
        appendLine("## Identity")
        appendLine("- Name: ${config.name}")
        appendLine("- Personality: ${config.personality}")
        appendLine("- Purpose: ${config.purpose}")
        appendLine()
        appendLine("## Model")
        appendLine("- Provider: ${config.providerBaseUrl}")
        appendLine("- Model: ${config.model}")
        appendLine("- Voice: ${config.voice}")
        appendLine()
        if (config.skills.isNotEmpty()) {
            appendLine("## Skills")
            config.skills.filter { it.enabled }.forEach { skill ->
                appendLine("- ${skill.name}")
                skill.config.forEach { (k, v) -> appendLine("  - $k: $v") }
            }
            appendLine()
        }
        if (config.channels.isNotEmpty()) {
            appendLine("## Channels")
            config.channels.filter { it.enabled }.forEach { ch ->
                appendLine("- ${ch.type}")
                ch.config.forEach { (k, v) -> appendLine("  - $k: $v") }
            }
            appendLine()
        }
        if (config.heartbeats.isNotEmpty()) {
            appendLine("## Heartbeats")
            config.heartbeats.filter { it.enabled }.forEach { hb ->
                appendLine("- [${hb.id}] cron: `${hb.cron}`")
                appendLine("  prompt: ${hb.prompt}")
            }
            appendLine()
        }
    }
}
