package com.clawdroid.app.data.api

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Seam for LLM inference backends.
 *
 * Cloud ([LlmApiClient]) and on-device (GenieX NPU, upcoming) providers both
 * implement this so [com.clawdroid.app.core.engine.AgentEngine] stays a single
 * code path: it only sees [StreamEvent]s, never HTTP vs NPU details.
 */
interface LlmProvider {
    /** Streaming chat completion. Same contract as LlmApiClient.streamChat. */
    fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray? = null,
        forcedToolName: String? = null,
        openCodeSessionId: String? = null,
    ): Flow<StreamEvent>

    /** Usable context window in tokens. Cloud default 128k; local NPU models are ~4k. */
    val contextLimit: Int

    /** True for fully on-device providers (no network for core inference). */
    val isLocal: Boolean
}

/** Fallback that surfaces configuration errors without throwing out of the agent loop. */
class LocalErrorProvider(private val message: String) : LlmProvider {
    override val contextLimit: Int = 4_096
    override val isLocal: Boolean = true

    override fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        forcedToolName: String?,
        openCodeSessionId: String?,
    ): Flow<StreamEvent> = kotlinx.coroutines.flow.flow {
        emit(StreamEvent.Error(message))
        emit(StreamEvent.Done)
    }
}
