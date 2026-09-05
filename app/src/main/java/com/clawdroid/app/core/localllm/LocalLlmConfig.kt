package com.clawdroid.app.core.localllm

/**
 * Single source of truth for on-device (GenieX llama_cpp, CPU) LLM versions.
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
    const val GGUF_MODEL_17B = "unsloth/Qwen3-1.7B-GGUF"
    /**
     * Purpose-built function caller for the 12 GB iQOO 15: xLAM fine-tune of
     * Llama-3.2-3B-Instruct for structured calls, Q4_K_M (~1.9 GB, ~4.5 GB RAM).
     * Benchmarks put the Qwen3-1.7B family on top only with native Hermes tools;
     * xLAM restrains itself better through our prompt-fence path.
     */
    const val GGUF_MODEL_XLAM_3B = "ermiaazarkhalili/Llama-3.2-3B-Instruct_Function_Calling_xLAM-GGUF"
    /**
     * Google mobile-first options. Gemma 3n E2B runs with ~2 GB effective RAM
     * via per-layer embeddings (2.8 GB Q4_K_M file); Gemma 3 1B is the fastest
     * fallback (~0.7 GB). Both need correct chat templates via applyChatTemplate.
     */
    const val GGUF_MODEL_GEMMA_3N_E2B = "unsloth/gemma-3n-E2B-it-GGUF"
    const val GGUF_MODEL_GEMMA_3_1B = "unsloth/gemma-3-1b-it-GGUF"
    /** Default is xLAM 3B: trained to call tools and to not call them. */
    const val DEFAULT_MODEL = GGUF_MODEL_XLAM_3B
    const val GGUF_PRECISION_Q4_0 = "Q4_0"
    /** Q4_K_M preserves ~95% quality at quarter size; prefer over Q4_0. */
    const val GGUF_PRECISION_Q4_K_M = "Q4_K_M"
    const val RUNTIME_LLAMA_CPP = "llama_cpp"

    /** Perf model: pre-compiled NPU-only bundle (Instruct variant — base can't do tools). */
    const val QAIRT_MODEL = "ai-hub-models/Qwen3-4B-Instruct-2507"
    const val RUNTIME_QAIRT = "qairt"

    /** Cap per generation — tool calls need <200 tokens; 1024 caps KV growth on low-RAM phones. */
    const val LOCAL_MAX_TOKENS = 1_024

    /**
     * Per-model context windows. GenieX llama_cpp uses use_mmap=false, so the
     * full weights sit in RAM (4B file 2.2 GB -> 2.8 GB RSS -> LMK OOM kill).
     * 4B gets 1K, 1.7B and 0.6B get 2K like PocketPal default.
     */
    const val N_CTX_4B = 1_024
    const val N_CTX_17B = 2_048
    const val N_CTX_06B = 2_048
    const val N_CTX_XLAM_3B = 2_048
    const val N_CTX_GEMMA = 2_048

    fun nCtxFor(modelId: String): Int = when (modelId) {
        GGUF_MODEL_4B -> N_CTX_4B
        GGUF_MODEL_17B -> N_CTX_17B
        GGUF_MODEL_XLAM_3B -> N_CTX_XLAM_3B
        GGUF_MODEL_GEMMA_3N_E2B, GGUF_MODEL_GEMMA_3_1B -> N_CTX_GEMMA
        else -> N_CTX_06B
    }
}
