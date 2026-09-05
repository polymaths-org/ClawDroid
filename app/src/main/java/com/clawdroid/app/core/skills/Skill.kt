package com.clawdroid.app.core.skills

/**
 * A pluggable skill module that adds capabilities to the agent.
 * Skills are loaded from AgentConfig and can provide tools, context,
 * or modify agent behavior.
 */
interface Skill {
    val name: String
    val description: String
    val version: String get() = "1.0"
}

/**
 * A skill that injects a system prompt fragment into the agent's context.
 */
data class PromptSkill(
    override val name: String,
    override val description: String,
    val systemPrompt: String,
) : Skill

/**
 * A skill that provides a script or tool the agent can invoke.
 */
data class ScriptSkill(
    override val name: String,
    override val description: String,
    val scriptPath: String,
    val runOnInit: Boolean = false,
) : Skill
