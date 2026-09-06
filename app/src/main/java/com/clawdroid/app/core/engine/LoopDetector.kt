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
                if (call.name == "launch_app" || call.name == "open_app") {
                    "The app is already open (launch succeeded above). Do NOT call launch_app again. " +
                        "Your NEXT call must send the message: send_message_in_current_chat with the text, " +
                        "or tap_text the Editable input label then type_text."
                } else {
                    "You already called this exact tool with the same arguments and received the result. Reply in plain text using that result. Do NOT repeat the same tool call."
                }
            )

            else -> LoopCheckResult.Ok
        }
    }

    private fun CompletedToolCall.signature(): String = "$name:$arguments"
}

/**
 * Decides whether an empty local-model turn (no text, no tool call) gets one
 * more chance with an explicit nudge instead of ending the run with nothing.
 * Small on-device models stall this way right after get_screen: they have the
 * screen but emit neither text nor a tool block. A plain-text nudge has proven
 * to steer them (same mechanism as the repeat-launch warning).
 */
object EmptyTurnPolicy {
    const val MAX_NUDGES = 2

    fun shouldNudge(
        finalLen: Int,
        turnBlank: Boolean,
        hadTools: Boolean,
        nudgesUsed: Int,
    ): Boolean {
        if (hadTools || !turnBlank || finalLen > 0) return false
        return nudgesUsed < MAX_NUDGES
    }

    const val NUDGE = "Continue the task: the current screen is in the Tool result above and the " +
        "original request is the first User message in the history. " +
        "Take the NEXT step now with a tool call, not empty text. To send a message, call " +
        "send_message_in_current_chat with the text, or tap_text the Editable input label then type_text."
}
