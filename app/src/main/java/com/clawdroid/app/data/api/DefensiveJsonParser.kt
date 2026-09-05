package com.clawdroid.app.data.api

import org.json.JSONObject

object DefensiveJsonParser {
    fun parseObjectOrError(raw: String): Result<JSONObject> {
        val trimmed = raw.trim()
        return standardParse(trimmed)
            .recoverCatching { standardParse(removeTrailingCommas(trimmed)).getOrThrow() }
            .recoverCatching { standardParse(extractObject(trimmed)).getOrThrow() }
    }

    fun errorForModel(raw: String, schemaHint: String): String = buildString {
        appendLine("Tool arguments were not valid JSON.")
        appendLine("Received:")
        appendLine(raw)
        appendLine("Expected schema:")
        appendLine(schemaHint)
        append("Return only a valid JSON object for the tool arguments.")
    }

    private fun standardParse(raw: String): Result<JSONObject> = runCatching { JSONObject(raw) }

    private fun removeTrailingCommas(raw: String): String = raw
        .replace(Regex(",\\s*}"), "}")
        .replace(Regex(",\\s*]"), "]")

    private fun extractObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        check(start >= 0 && end > start) { "No JSON object found" }
        return raw.substring(start, end + 1)
    }
}
