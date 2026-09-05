package com.clawdroid.app.core.engine

import com.clawdroid.app.core.control.AndroidControlTools
import com.clawdroid.app.data.api.CompletedToolCall

sealed interface LoopCheckResult {
    data object Ok : LoopCheckResult
    data class Warn(val message: String) : LoopCheckResult
    data class Stop(val message: String) : LoopCheckResult
}

class LoopDetector(
    private val warnAfterSimilarCalls: Int = 2,
    private val hardStopAfterIdenticalCalls: Int = 10,
) {
    private val recentCalls = ArrayDeque<String>()

    fun record(call: CompletedToolCall): LoopCheckResult {
        val signature = call.signature()
        // Screen taps with guessed coords looped 50x ("open browser" -> tap 800,500).
        // Cap identical screen calls at 5 so the run stops instead of tapping forever.
        val hardCap = if (AndroidControlTools.isScreenControlTool(call.name)) 5 else hardStopAfterIdenticalCalls
        recentCalls.addLast(signature)
        while (recentCalls.size > hardCap) {
            recentCalls.removeFirst()
        }

        val identicalCount = recentCalls.count { it == signature }
        return when {
            identicalCount >= hardCap -> LoopCheckResult.Stop(
                "The agent attempted the same tool call $identicalCount times. Stopping to avoid an infinite loop."
            )

            identicalCount >= warnAfterSimilarCalls -> LoopCheckResult.Warn(
                "You already called this exact tool with the same arguments and received the result. Reply in plain text using that result. Do NOT repeat the same tool call."
            )

            else -> LoopCheckResult.Ok
        }
    }

    private fun CompletedToolCall.signature(): String = "$name:$arguments"
}
