package com.clawdroid.app.ui.components

data class ThinkingQuote(
    val text: String,
    val author: String,
)

object ThinkingQuoteCatalog {
    val quotes = listOf(
        ThinkingQuote("Simplicity is prerequisite for reliability.", "Edsger W. Dijkstra"),
        ThinkingQuote("Programs must be written for people to read.", "Harold Abelson"),
        ThinkingQuote("Talk is cheap. Show me the code.", "Linus Torvalds"),
        ThinkingQuote("Make it work, make it right, make it fast.", "Kent Beck"),
        ThinkingQuote("The best way to predict the future is to invent it.", "Alan Kay"),
        ThinkingQuote("Stay hungry, stay foolish.", "Steve Jobs"),
        ThinkingQuote("Premature optimization is the root of all evil.", "Donald Knuth"),
        ThinkingQuote("Any sufficiently advanced technology is indistinguishable from magic.", "Arthur C. Clarke"),
        ThinkingQuote("What I cannot create, I do not understand.", "Richard Feynman"),
        ThinkingQuote("The details are not the details. They make the design.", "Charles Eames"),
        ThinkingQuote("First, solve the problem. Then, write the code.", "John Johnson"),
        ThinkingQuote("The only way to learn a new programming language is by writing programs in it.", "Dennis Ritchie"),
    )
}
