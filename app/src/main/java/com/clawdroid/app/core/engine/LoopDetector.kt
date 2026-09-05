package com.clawdroid.app.core.engine

import com.clawdroid.app.data.api.CompletedToolCall

sealed interface LoopCheckResult {
    data object Ok : LoopCheckResult
    data class Warn(val message: String) : LoopCheckResult
    data class Stop(val message: String) : LoopCheckResult
}

class LoopDetector(
    private val warnAfterSimilarCalls: Int = 3,
    private val hardStopAfterIdenticalCalls: Int = 10,
) {
    private val recentCalls = ArrayDeque<String>()

    fun record(call: CompletedToolCall): LoopCheckResult {
        val signature = call.signature()
        recentCalls.addLast(signature)
        while (recentCalls.size > hardStopAfterIdenticalCalls) {
            recentCalls.removeFirst()
        }

        val identicalCount = recentCalls.count { it == signature }
        return when {
            identicalCount >= hardStopAfterIdenticalCalls -> LoopCheckResult.Stop(
                "The agent attempted the same tool call $identicalCount times. Stopping to avoid an infinite loop."
            )

            identicalCount >= warnAfterSimilarCalls -> LoopCheckResult.Warn(
                "You've attempted this tool call multiple times. Try a fundamentally different approach or explain the blocker."
            )

            else -> LoopCheckResult.Ok
        }
    }

    private fun CompletedToolCall.signature(): String = "$name:$arguments"
}
