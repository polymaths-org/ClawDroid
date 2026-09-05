package com.clawdroid.app.core.terminal

object InputTranslator {
    fun translate(input: String): ByteArray = input
        .replace("[ENTER]", "\r")
        .replace("[TAB]", "\t")
        .replace("[UP]", "\u001B[A")
        .replace("[DOWN]", "\u001B[B")
        .replace("[LEFT]", "\u001B[D")
        .replace("[RIGHT]", "\u001B[C")
        .replace("[CTRL+C]", "\u0003")
        .replace("[CTRL+D]", "\u0004")
        .replace("[CTRL+Z]", "\u001A")
        .replace("[BACKSPACE]", "\u007F")
        .replace("[HOME]", "\u001B[H")
        .replace("[END]", "\u001B[F")
        .toByteArray(Charsets.UTF_8)
}
