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
        .takeIf { it.isNotBlank() } ?: LocalLlmConfig.DEFAULT_MODEL,
) : LlmProvider {
    override val contextLimit: Int get() = LocalLlmConfig.nCtxFor(modelId)
    override val isLocal: Boolean = true

    /**
     * Crash guard: the 10:57 tombstone showed 209% CPU, 352K major faults,
     * and mem-pressure restarts after 4 concurrent loads of the same 1.7B
     * weights. Per-instance locks allowed duplicate native residents that
     * together exceed phone RAM and take down system services. One resident
     * model and one inference at a time, process-wide.
     */
    companion object {
        private const val TAG = "GenieXLocalProvider"
        private val globalLoadMutex = Mutex()
        private val globalGenMutex = Mutex()
        @Volatile private var sharedHandle: LlmWrapper? = null
        @Volatile private var sharedModelId: String = ""
        private const val MIN_AVAIL_MB = 900
    }

    /**
     * PocketPal-style: tiny catalog per model. 0.6B gets 4 basic tools only,
     * unless the message asks for mail/events — then every local model gets
     * the Gmail/Calendar intent-routed set (same 6-tool budget).
     * Order matters: renderTools emits in allowlist order and caps at MAX_TOOLS,
     * so launch_app leads — "open browser" must never degrade to blind taps.
     * Gmail/Calendar tools are intent-routed: they replace the file/screen set
     * only when the last user message asks for mail or events, keeping the
     * 6-tool prompt budget intact.
     */
    private fun allowlistFor(model: String, lastUserText: String = ""): Set<String> =
        LocalPromptTools.localAllowlist(model, lastUserText)

    private suspend fun ensureLoaded(): LlmWrapper {
        sharedHandle?.takeIf { sharedModelId == modelId }?.let { return it }
        return globalLoadMutex.withLock {
            sharedHandle?.takeIf { sharedModelId == modelId }?.let { return it }
            // Only one resident: drop a different model before loading, so
            // two weights never sit in RAM together on a 12GB phone.
            if (sharedHandle != null && sharedModelId != modelId) {
                Log.i(TAG, "unloading model=$sharedModelId for model=$modelId")
                runCatching { sharedHandle?.stopStream() }
                runCatching { sharedHandle?.reset() }
                sharedHandle = null
                sharedModelId = ""
                System.gc()
            }
            checkFreeMemory()
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
                            // 4 threads caps peak CPU buffers vs default 8; small batch caps compute RAM.
                            nThreads = 4
                            nThreadsBatch = 4
                            nBatch = 128
                            nUBatch = 64
                        }.copy(enable_thinking = true),
                        runtime_id = paths.runtime_id?.takeIf { r -> r.isNotBlank() } ?: opt.runtimeId,
                        // CPU avoids the Hexagon NPU SIGABRT in ggml-hexagon dspqueue.
                        compute_unit = ComputeUnitValue.CPU.value,
                    ),
                )
                .build()
                .getOrThrow()
            sharedHandle = handle
            sharedModelId = modelId
            Log.i(TAG, "model loaded model=$modelId")
            handle
        }
    }

    private fun checkFreeMemory() {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val availMb = info.availMem / (1024 * 1024)
        Log.i(TAG, "availMem=${availMb}MB lowMemory=${info.lowMemory}")
        if (info.lowMemory || availMb < MIN_AVAIL_MB) {
            error("Phone is low on memory (${availMb}MB free). Close other apps and retry — loading now would risk killing the app.")
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
        // The native handle is reused across turns; its KV cache accumulates
        // unless cleared, surfacing as "context length exceeded" mid-thread.
        // Reset per turn so each prompt starts from a clean context.
        runCatching { handle.reset() }.onFailure { Log.w(TAG, "context reset failed", it) }
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
        var genFailed: Throwable? = null
        try {
            // One inference at a time: concurrent decodes double KV RAM and
            // pin CPU past 200%, which is what forced mem-pressure restarts.
            globalGenMutex.withLock {
                handle.generateStreamFlow(
                    templated,
                    GenerationConfig(maxTokens = LocalLlmConfig.maxTokensFor(modelId)),
                ).collect { result ->
                    when (result) {
                        // Buffer only: tool_json blocks must not appear as chat text.
                        // Visible text is emitted once, stripped, after generation.
                        is LlmStreamResult.Token -> fullText.append(result.text)
                        is LlmStreamResult.Completed -> Unit
                        is LlmStreamResult.Error -> {
                            Log.e(TAG, "generate error", result.throwable)
                            genFailed = result.throwable
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Includes OutOfMemoryError: surface as a chat error so the run
            // stops cleanly instead of taking the process (and phone) down.
            Log.e(TAG, "generate failed", t)
            genFailed = t
        } finally {
            runCatching { handle.stopStream() }
        }
        if (genFailed != null) {
            // Drop the resident on OOM so the next turn reloads cleanly
            // instead of reusing a poisoned native context.
            if (genFailed is OutOfMemoryError) {
                sharedHandle = null
                sharedModelId = ""
                System.gc()
            }
            emit(StreamEvent.Error(genFailed?.message ?: "Local generation failed"))
            emit(StreamEvent.Done)
            return@flow
        }
        val raw = fullText.toString()
        val toolCall = parseToolCall(raw)
        val thinking = LocalPromptTools.extractThinking(raw)
        if (thinking.isNotEmpty()) Log.d(TAG, "thinking len=${thinking.length}")
        val visible = LocalPromptTools.firstTurnOnly(
            LocalPromptTools.stripRolePrefix(
                LocalPromptTools.TOOL_CALL_ECHO_REGEX.replace(
                    LocalPromptTools.stripThinking(LocalPromptTools.stripToolBlock(raw)),
                    "",
                ),
            ),
        ).trim().take(500)
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
        // Route on oldest plus newest user text: mid-thread nudges and loop
        // warnings are stored as user messages and would otherwise flip the
        // tool catalog away from the original task.
        val firstUser = messages.firstOrNull { it.role == "user" }?.content.orEmpty()
        val lastUser = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val routeText = "$firstUser $lastUser"
        val mini = runCatching {
            if (AppConfigManager.miniContextEnabled) {
                com.clawdroid.app.core.memory.MiniContextManager(appContext)
                    .readForPrompt(AppConfigManager.miniContextMaxLines)
            } else ""
        }.getOrDefault("")
        return LocalPromptTools.buildPromptForModel(
            messages,
            tools,
            allowlistFor(modelId, routeText),
            modelId,
            home,
            miniContext = mini.ifBlank { null },
        )
    }

    private fun parseToolCall(text: String): CompletedToolCall? =
        LocalPromptTools.parseToolCall(text)
}
