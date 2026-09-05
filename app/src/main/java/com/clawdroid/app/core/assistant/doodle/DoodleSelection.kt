package com.clawdroid.app.core.assistant.doodle

import android.graphics.Rect

data class DoodleSelection(
    val boundsPx: Rect,
    val pathSvgLike: String?,
    val screenshotPath: String,
    val cropPath: String,
    val userPrompt: String?,
)
