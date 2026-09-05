package com.clawdroid.app.core.localllm

import android.content.Context
import android.util.Log
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.HubSource
import com.geniex.sdk.bean.ModelPaths
import com.geniex.sdk.bean.ModelPullInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class LocalModelOption(
    val id: String,
    val label: String,
    val sizeNote: String,
    val hub: HubSource,
    val precision: String?,
    val chipset: String?,
    val runtimeId: String,
)

sealed interface LocalModelStatus {
    data object NotDownloaded : LocalModelStatus
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : LocalModelStatus
    data object Ready : LocalModelStatus
    data class Error(val message: String) : LocalModelStatus
}

/**
 * Manages on-device model weights via the GenieX ModelManager.
 * Weights are NEVER shipped in the APK — first-run/in-settings download from
 * Hugging Face / Qualcomm AI Hub, resumable, Wi-Fi recommended.
 * Mirrors the existing WhisperModelManager pattern.
 */
object LocalModelManager {
    private const val TAG = "LocalModelManager"

    val options = listOf(
        LocalModelOption(
            id = LocalLlmConfig.GGUF_MODEL_4B,
            label = "Qwen3-4B (recommended)",
            sizeNote = "~2.5 GB download · Hexagon NPU",
            hub = HubSource.HUGGINGFACE,
            precision = LocalLlmConfig.GGUF_PRECISION_Q4_0,
            chipset = null,
            runtimeId = LocalLlmConfig.RUNTIME_LLAMA_CPP,
        ),
        LocalModelOption(
            id = LocalLlmConfig.GGUF_MODEL_06B,
            label = "Qwen3-0.6B (fast, small)",
            sizeNote = "~0.4 GB download · any Snapdragon",
            hub = HubSource.HUGGINGFACE,
            precision = LocalLlmConfig.GGUF_PRECISION_Q4_0,
            chipset = null,
            runtimeId = LocalLlmConfig.RUNTIME_LLAMA_CPP,
        ),
    )

    fun optionFor(modelId: String): LocalModelOption =
        options.firstOrNull { it.id == modelId } ?: options.first()

    private val _status = MutableStateFlow<Map<String, LocalModelStatus>>(emptyMap())
    val status: StateFlow<Map<String, LocalModelStatus>> = _status.asStateFlow()

    fun statusOf(modelId: String): LocalModelStatus =
        _status.value[modelId] ?: LocalModelStatus.NotDownloaded

    @Volatile private var sdkInit = false

    suspend fun ensureInit(context: Context) = withContext(Dispatchers.IO) {
        if (!sdkInit) {
            Log.i(TAG, "GenieXSdk.init")
            GenieXSdk.getInstance().init(context.applicationContext)
            sdkInit = true
        }
    }

    suspend fun refreshStatus(context: Context, modelId: String): LocalModelStatus =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val paths = runCatching { ModelManagerWrapper.getPaths(modelId) }.getOrNull()
            val s = if (paths?.model_path.isNullOrBlank()) {
                LocalModelStatus.NotDownloaded
            } else {
                LocalModelStatus.Ready
            }
            _status.update { it + (modelId to s) }
            s
        }

    suspend fun getPaths(context: Context, modelId: String): ModelPaths? =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            runCatching { ModelManagerWrapper.getPaths(modelId) }.getOrNull()
                .takeIf { !it?.model_path.isNullOrBlank() }
        }

    suspend fun download(context: Context, modelId: String) {
        val opt = optionFor(modelId)
        withContext(Dispatchers.IO) {
            ensureInit(context)
            Log.i(TAG, "pull start model=$modelId hub=${opt.hub}")
            try {
                ModelManagerWrapper.pullFlow(
                    ModelPullInput(
                        model_name = opt.id,
                        precision = opt.precision,
                        hub = opt.hub,
                        chipset = opt.chipset,
                    ),
                ).collect { event ->
                    when (event) {
                        is ModelManagerWrapper.PullEvent.Progress -> {
                            val dl = event.files.sumOf { it.downloaded_bytes }
                            val total = event.files.sumOf { it.total_bytes }
                            _status.update { it + (modelId to LocalModelStatus.Downloading(dl, total)) }
                        }
                        is ModelManagerWrapper.PullEvent.Completed -> {
                            Log.i(TAG, "pull completed model=$modelId")
                            _status.update { it + (modelId to LocalModelStatus.Ready) }
                        }
                        is ModelManagerWrapper.PullEvent.Error -> {
                            Log.e(TAG, "pull error model=$modelId code=${event.code} msg=${event.message}")
                            _status.update {
                                it + (modelId to LocalModelStatus.Error("Download failed (${event.code}): ${event.message}"))
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "pull exception model=$modelId", t)
                _status.update { it + (modelId to LocalModelStatus.Error(t.message ?: "Download failed")) }
            }
        }
    }

    suspend fun delete(context: Context, modelId: String) = withContext(Dispatchers.IO) {
        ensureInit(context)
        runCatching { ModelManagerWrapper.remove(modelId) }
        _status.update { it + (modelId to LocalModelStatus.NotDownloaded) }
    }
}
