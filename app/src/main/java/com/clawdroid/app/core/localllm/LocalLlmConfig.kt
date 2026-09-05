package com.clawdroid.app.core.localllm

/**
 * Single source of truth for on-device (Hexagon NPU) LLM versions.
 *
 * QAIRT/SDK/bundle mismatch is the most common failure mode — when upgrading
 * one, upgrade all three together and re-test on the iQOO 15 (SM8850).
 */
object LocalLlmConfig {
    /** Gradle dep com.qualcomm.qti:geniex-android — keep in sync with app/build.gradle.kts. */
    const val GENIEX_VERSION = "0.3.12"

    /** QAIRT SDK version the pre-compiled bundles below were built against. */
    const val QAIRT_VERSION = "2.45"

    /** Provider id stored in AppConfigManager when local mode is selected. */
    const val PROVIDER_ID = "geniex-local"

    /** iQOO 15 = Snapdragon 8 Elite Gen 5. SM8750 (8 Elite non-Gen-5) is WRONG here. */
    const val CHIPSET_SM8850 = "SM8850"

    /** Bring-up models: portable GGUF, no chipset pinning, fastest iteration. */
    const val GGUF_MODEL_4B = "unsloth/Qwen3-4B-GGUF"
    const val GGUF_MODEL_06B = "unsloth/Qwen3-0.6B-GGUF"
    const val GGUF_PRECISION_Q4_0 = "Q4_0"
    const val RUNTIME_LLAMA_CPP = "llama_cpp"

    /** Perf model: pre-compiled NPU-only bundle (Instruct variant — base can't do tools). */
    const val QAIRT_MODEL = "ai-hub-models/Qwen3-4B-Instruct-2507"
    const val RUNTIME_QAIRT = "qairt"

    /** Local context window — NPU memory bandwidth is the bottleneck, keep prompts lean. */
    const val LOCAL_CONTEXT_LIMIT = 4_096
    const val LOCAL_N_CTX = 4_096
    const val LOCAL_MAX_TOKENS = 2_048
}
