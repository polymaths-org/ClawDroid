package com.clawdroid.app.data.api

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.localllm.GenieXLocalProvider
import com.clawdroid.app.core.localllm.LocalLlmConfig

/**
 * Selects the active [LlmProvider]. Cloud ([LlmApiClient]) by default;
 * returns the on-device GenieX NPU provider when prefs select it.
 */
object LlmProviderFactory {
    fun isLocalSelected(provider: String = AppConfigManager.provider): Boolean =
        provider == LocalLlmConfig.PROVIDER_ID

    fun createDefault(): LlmProvider = LlmApiClient()

    fun create(
        context: Context? = null,
        provider: String = AppConfigManager.provider,
    ): LlmProvider =
        when (provider) {
            LocalLlmConfig.PROVIDER_ID -> {
                val ctx = context?.applicationContext
                if (ctx == null) {
                    LocalErrorProvider("On-device provider needs an Android Context; open the app once, then retry.")
                } else {
                    GenieXLocalProvider(ctx)
                }
            }
            else -> LlmApiClient()
        }
}
