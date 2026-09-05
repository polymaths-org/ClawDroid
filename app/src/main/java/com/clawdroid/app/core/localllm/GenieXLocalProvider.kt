package com.clawdroid.app.core.localllm

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmProvider
import com.clawdroid.app.data.api.StreamEvent
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.bean.ChatMessage as GenieChatMessage
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/**
 * On-device [LlmProvider] backed by GenieX (llama_cpp Q4_0), pinned to CPU.
 *
 * CPU is deliberate: the Hexagon NPU path aborts the process (SIGABRT in
 * ggml-hexagon dspqueue) on some Snapdragon chips, killing the app
 * mid-generation with no catchable exception.
 *
 * The NPU runtimes have no native function-calling, so tools are passed as
 * prompt text and the model replies with a single ```tool_json block, parsed
 * with [DefensiveJsonParser] (small models garble JSON often).
 */
class GenieXLocalProvider(
    private val appContext: Context,
    private val modelId: String = AppConfigManager.model
        .takeIf { it.isNotBlank() } ?: LocalLlmConfig.GGUF_MODEL_4B,
) : LlmProvider {
    override val contextLimit: Int get() = LocalLlmConfig.nCtxFor(modelId)
    override val isLocal: Boolean = true

    private val loadLock = Mutex()
    @Volatile private var llm: LlmWrapper? = null

    /** Core tools only — a 4k-context 4B model cannot hold all 30+ cloud tools. */
    private val toolAllowlist = setOf(
        "get_screen", "tap", "tap_text", "swipe", "scroll", "type_text",
        "press_back", "press_home", "launch_app", "execute_command",
        "read_file", "write_file", "list_directory", "wait",
    )

    private suspend fun ensureLoaded(): LlmWrapper {
        llm?.let { return it }
        return loadLock.withLock {
            llm?.let { return it }
            Log.i(TAG, "loading model=$modelId")
            val paths = LocalModelManager.getPaths(appContext, modelId)
                ?: error("Local model '$modelId' is not downloaded. Download it in Provider settings first.")
            val opt = LocalModelManager.optionFor(modelId)
            val handle = LlmWrapper.builder()
                .llmCreateInput(
                    LlmCreateInput(
                        model_path = paths.model_path,
                        config = ModelConfig(nCtx = LocalLlmConfig.nCtxFor(modelId)).apply {
                            nGpuLayers = 0
                        },
                        runtime_id = paths.runtime_id?.takeIf { r -> r.isNotBlank() } ?: opt.runtimeId,
                        // CPU avoids the Hexagon NPU SIGABRT in ggml-hexagon dspqueue.
                        compute_unit = ComputeUnitValue.CPU.value,
                    ),
                )
                .build()
                .getOrThrow()
            llm = handle
            Log.i(TAG, "model loaded model=$modelId")
            handle
        }
    }

    override fun streamChat(
        messages: List<ChatMessage>,
        tools: JSONArray?,
        forcedToolName: String?,
        openCodeSessionId: String?,
    ): Flow<StreamEvent> = flow {
        val handle = try {
            ensureLoaded()
        } catch (t: Throwable) {
            Log.e(TAG, "load failed", t)
            emit(StreamEvent.Error(LocalPromptTools.loadErrorMessage(t)))
            emit(StreamEvent.Done)
            return@flow
        }
        val prompt = buildPrompt(messages, tools)
        val templated = try {
            handle.applyChatTemplate(arrayOf(GenieChatMessage("user", prompt)), null, false)
                .getOrThrow()
                .formattedText
        } catch (t: Throwable) {
            Log.e(TAG, "chat template failed", t)
            emit(StreamEvent.Error("Local model prompt failed: ${t.message}"))
            emit(StreamEvent.Done)
            return@flow
        }
        val fullText = StringBuilder()
        try {
            handle.generateStreamFlow(
                templated,
                GenerationConfig(maxTokens = LocalLlmConfig.LOCAL_MAX_TOKENS),
            ).collect { result ->
                when (result) {
                    // Buffer only: tool_json blocks must not appear as chat text.
                    // Visible text is emitted once, stripped, after generation.
                    is LlmStreamResult.Token -> fullText.append(result.text)
                    is LlmStreamResult.Completed -> Unit
                    is LlmStreamResult.Error -> {
                        Log.e(TAG, "generate error", result.throwable)
                        emit(StreamEvent.Error(result.throwable.message ?: "Local generation failed"))
                    }
                }
            }
        } finally {
            runCatching { handle.stopStream() }
        }
        val raw = fullText.toString()
        val toolCall = parseToolCall(raw)
        val visible = LocalPromptTools.stripToolBlock(raw).trim()
        // If the turn is only a tool call, show no chat bubble; the tool step covers it.
        // If text accompanies the call, show only that text.
        if (toolCall == null) {
            if (visible.isNotEmpty()) emit(StreamEvent.TextDelta(visible))
        } else if (visible.isNotEmpty()) {
            emit(StreamEvent.TextDelta(visible))
        }
        toolCall?.let { emit(StreamEvent.ToolCallComplete(it)) }
        emit(StreamEvent.Done)
    }.flowOn(Dispatchers.IO)

    private fun buildPrompt(messages: List<ChatMessage>, tools: JSONArray?): String {
        val home = runCatching { EnvironmentSetup.build(appContext).home.absolutePath }.getOrNull()
        return LocalPromptTools.buildPrompt(messages, tools, toolAllowlist, home)
    }

    private fun parseToolCall(text: String): CompletedToolCall? =
        LocalPromptTools.parseToolCall(text)

    companion object {
        private const val TAG = "GenieXLocalProvider"
    }
}
