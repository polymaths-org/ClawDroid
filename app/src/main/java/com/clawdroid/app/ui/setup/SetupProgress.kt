package com.clawdroid.app.ui.setup

fun setupProgressFraction(currentStep: Int, totalSteps: Int): Float {
    if (totalSteps <= 0) return 0f
    return ((currentStep + 1).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}

fun setupProgressPercent(currentStep: Int, totalSteps: Int): Int {
    return (setupProgressFraction(currentStep, totalSteps) * 100).toInt().coerceIn(0, 100)
}

const val SETUP_TOTAL_STEPS = 6
