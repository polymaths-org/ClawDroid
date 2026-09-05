package com.clawdroid.app.core.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CostState(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
)

class CostTracker(
    private val inputUsdPerMillionTokens: Double = 0.15, // Sleek modern models default pricing
    private val outputUsdPerMillionTokens: Double = 0.60,
    private val cachedUsdPerMillionTokens: Double = 0.03, // Typically 50% to 90% cheaper or free
) {
    private val mutableState = MutableStateFlow(CostState())
    val state: StateFlow<CostState> = mutableState.asStateFlow()

    fun record(inputTokens: Int, outputTokens: Int, cachedTokens: Int = 0) {
        val current = mutableState.value
        val nextInput = current.inputTokens + inputTokens
        val nextOutput = current.outputTokens + outputTokens
        val nextCached = current.cachedTokens + cachedTokens
        mutableState.value = CostState(
            inputTokens = nextInput,
            outputTokens = nextOutput,
            cachedTokens = nextCached,
            estimatedCostUsd = estimateCost(nextInput, nextOutput, nextCached),
        )
    }

    fun wouldExceedLimit(additionalInputTokens: Int, additionalOutputTokens: Int, additionalCachedTokens: Int, limitUsd: Double): Boolean {
        val current = mutableState.value
        val projected = estimateCost(
            current.inputTokens + additionalInputTokens,
            current.outputTokens + additionalOutputTokens,
            current.cachedTokens + additionalCachedTokens,
        )
        return projected > limitUsd
    }

    private fun estimateCost(inputTokens: Int, outputTokens: Int, cachedTokens: Int): Double {
        return (inputTokens / 1_000_000.0 * inputUsdPerMillionTokens) +
                (outputTokens / 1_000_000.0 * outputUsdPerMillionTokens) +
                (cachedTokens / 1_000_000.0 * cachedUsdPerMillionTokens)
    }
}
