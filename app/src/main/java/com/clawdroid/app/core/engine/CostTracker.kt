package com.clawdroid.app.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CostState(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
)

class CostTracker(
    private val inputUsdPerMillionTokens: Double = 0.0,
    private val outputUsdPerMillionTokens: Double = 0.0,
) {
    private val mutableState = MutableStateFlow(CostState())
    val state: StateFlow<CostState> = mutableState.asStateFlow()

    fun record(inputTokens: Int, outputTokens: Int) {
        val current = mutableState.value
        val nextInput = current.inputTokens + inputTokens
        val nextOutput = current.outputTokens + outputTokens
        mutableState.value = CostState(
            inputTokens = nextInput,
            outputTokens = nextOutput,
            estimatedCostUsd = estimateCost(nextInput, nextOutput),
        )
    }

    fun wouldExceedLimit(additionalInputTokens: Int, additionalOutputTokens: Int, limitUsd: Double): Boolean {
        val current = mutableState.value
        val projected = estimateCost(
            current.inputTokens + additionalInputTokens,
            current.outputTokens + additionalOutputTokens,
        )
        return projected > limitUsd
    }

    private fun estimateCost(inputTokens: Int, outputTokens: Int): Double {
        return (inputTokens / 1_000_000.0 * inputUsdPerMillionTokens) +
            (outputTokens / 1_000_000.0 * outputUsdPerMillionTokens)
    }
}
