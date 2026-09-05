package com.clawdroid.app.data.api

object MessageBuilder {
    fun forUserPrompt(prompt: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """
                You are ClawDroid, a transparent Android agent with access to a Linux sandbox.
                Use tools when useful, keep the user informed, and prefer concrete action over vague advice.
            """.trimIndent(),
        ),
        ChatMessage(role = "user", content = prompt),
    )
}
