package com.clawdroid.app.core.voice

object ThinkingPhrases {
    private val phrases = listOf(
        "Yeah, looking into it…",
        "Got it, let me check…",
        "Let me process this…",
        "Okay, give me a sec…",
        "Let me look into that…",
        "One moment…",
        "Working on it…",
        "Let me figure this out…",
        "Checking that now…",
        "Alright, let me see what I can find…",
        "Hmm, let me think about that…",
        "On it…",
        "Let me search for that…",
        "Good question, give me a moment…",
        "Let me pull that up…",
    )

    private var lastIndex = -1

    fun random(): String {
        val idx = (phrases.indices).filter { it != lastIndex }.randomOrNull() ?: 0
        lastIndex = idx
        return phrases[idx]
    }
}
