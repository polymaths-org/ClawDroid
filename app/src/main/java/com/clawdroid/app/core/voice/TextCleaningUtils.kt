package com.clawdroid.app.core.voice

import android.util.Log

/**
 * Utility for cleaning text before TTS processing
 * - Removes emojis (which would be read incorrectly)
 * - Filters out thinking/analyzing expressions
 * - Removes tone markers like (excited), (serious), etc
 * - Preserves readability and natural speech flow
 */
object TextCleaningUtils {

    /**
     * Emoji and symbol patterns that should be removed before TTS
     */
    private val EMOJI_PATTERN = Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]")
    private val CODE_FENCE_PATTERN = Regex("```[\\s\\S]*?```")
    private val MARKDOWN_LINK_PATTERN = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val STRUCTURAL_LINE_PATTERN = Regex("(?m)^\\s*(?:[-*_]{3,}|={3,}|#{1,6}\\s*)\\s*$")
    private val BULLET_PREFIX_PATTERN = Regex("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|>\\s*)")
    private val FILLER_LINE_PATTERN = Regex(
        "(?im)^\\s*(h+m+|u+h+|u+m+|ok(?:ay)?|right|sure|thinking|processing|one moment|let me think)\\s*[.!?]*\\s*$"
    )

    /**
     * Patterns for thinking/analyzing expressions that should be filtered
     * Examples: [thinking], [analyzing], [processing], etc
     */
    private val THINKING_EXPRESSION_PATTERN = Regex(
        "\\[(thinking|analyzing|processing|considering|evaluating|assessing|reviewing|checking|pondering|reflecting|examining)\\]",
        RegexOption.IGNORE_CASE
    )

    /**
     * Patterns for tone markers and emotional indicators
     * Examples: (excited), (serious), (sarcastic), etc
     */
    private val TONE_MARKER_PATTERN = Regex(
        "\\((excited|happy|sad|angry|sarcastic|serious|joking|frustrated|confused|concerned|thoughtful|pleased|disappointed|surprised|embarrassed|curious|skeptical|confident|uncertain|calm|intense|gentle|witty)\\)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Remove emojis and special symbols from text for TTS
     * Returns cleaner text that won't be misread by TTS engines
     */
    fun cleanForTts(text: String): String {
        if (text.isBlank()) return text

        var cleaned = text
            .replace(CODE_FENCE_PATTERN, " ")
            .replace(MARKDOWN_LINK_PATTERN, "$1")
            .replace(STRUCTURAL_LINE_PATTERN, " ")
            .replace(FILLER_LINE_PATTERN, " ")
            .replace(BULLET_PREFIX_PATTERN, "")
            .replace(EMOJI_PATTERN, " ")
            .replace("(?:^\\s+|\\s+$)".toRegex(), "")
            .replace("\\s+".toRegex(), " ")

        // Remove markdown-style symbols that add no value in speech
        cleaned = cleaned
            .replace("**", "")  // Bold
            .replace("__", "")  // Underscore
            .replace("`", "")  // Code tick
            .replace("_", " ")  // Italic
            .replace("|", " ")

        // Remove URLs (they're unreadable)
        cleaned = cleaned.replace("http[s]?://[^\\s]+".toRegex(), "link")

        // Clean up multiple punctuation
        cleaned = cleaned
            .replace("([.!?])\\1{2,}".toRegex(), "$1")  // Remove multiple punctuation
            .replace("([,;])\\1{2,}".toRegex(), "$1")

        return cleaned.trim()
    }

    /**
     * Filter out thinking/analyzing expressions like [thinking], [analyzing], etc
     */
    fun filterExpressions(text: String): String {
        if (text.isBlank()) return text
        return text.replace(THINKING_EXPRESSION_PATTERN, "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Filter out tone markers like (excited), (serious), (sarcastic), etc
     */
    fun filterToneMarkers(text: String): String {
        if (text.isBlank()) return text
        return text.replace(TONE_MARKER_PATTERN, "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /**
     * Complete filtering: remove expressions, tone markers, emojis, and clean markdown
     * This is the most aggressive cleaning for TTS
     */
    fun fullyCleanForTts(text: String): String {
        if (text.isBlank()) return text
        
        var cleaned = text
        // First, filter expressions and tone markers
        cleaned = filterExpressions(cleaned)
        cleaned = filterToneMarkers(cleaned)
        // Then apply standard TTS cleaning
        cleaned = cleanForTts(cleaned)
        return cleaned
    }

    /**
     * Detect if text contains emojis
     */
    fun hasEmojis(text: String): Boolean {
        return EMOJI_PATTERN.containsMatchIn(text)
    }

    /**
     * Get emoji count in text
     */
    fun countEmojis(text: String): Int {
        return EMOJI_PATTERN.findAll(text).count()
    }

    /**
     * Detect if text contains thinking expressions
     */
    fun hasThinkingExpressions(text: String): Boolean {
        return THINKING_EXPRESSION_PATTERN.containsMatchIn(text)
    }

    /**
     * Detect if text contains tone markers
     */
    fun hasToneMarkers(text: String): Boolean {
        return TONE_MARKER_PATTERN.containsMatchIn(text)
    }

    /**
     * Log cleaning info for debugging
     */
    fun debugClean(text: String): String {
        val cleaned = fullyCleanForTts(text)
        val emojiCount = countEmojis(text)
        val hasThinking = hasThinkingExpressions(text)
        val hasTone = hasToneMarkers(text)
        
        if (emojiCount > 0 || hasThinking || hasTone || text != cleaned) {
            Log.d(
                "TextCleaningUtils",
                "Cleaned TTS input: emojis=$emojiCount, expressions=$hasThinking, tones=$hasTone, " +
                    "original=${text.length} -> cleaned=${cleaned.length}"
            )
        }
        return cleaned
    }
}
