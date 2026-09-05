package com.clawdroid.app.core.terminal

object AnsiStripper {
    private val ansiRegex = Regex("\\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])")

    fun strip(text: String): String = text.replace(ansiRegex, "")
}
