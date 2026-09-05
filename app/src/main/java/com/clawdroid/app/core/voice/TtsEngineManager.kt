package com.clawdroid.app.core.voice

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val engineName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
) {
    val percentComplete: Float get() = if (totalBytes > 0) {
        (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f
    } else 0f
}

data class TtsEngineInfo(
    val id: String,
    val name: String,
    val description: String,
    val isAvailable: Boolean,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val requiresApiKey: Boolean = false,
    val hasApiKey: Boolean = false
)

/**
 * Manages available TTS engines - detection, downloading, and fallback
 * Supports: Piper, Kokoro, OpenAI, ElevenLabs, Deepgram, Android (device)
 */
class TtsEngineManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _engines = MutableStateFlow<List<TtsEngineInfo>>(emptyList())
    val engines: StateFlow<List<TtsEngineInfo>> = _engines.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    companion object {
        private const val TAG = "TtsEngineManager"
    }

    init {
        scope.launch {
            detectAvailableEngines()
        }
    }

    /**
     * Detect which TTS engines are available on the device
     */
    private suspend fun detectAvailableEngines() {
        val engineList = mutableListOf<TtsEngineInfo>()

        // Check Android (device) TTS - always available
        engineList.add(TtsEngineInfo(
            id = "device",
            name = "Android TTS",
            description = "Built-in system TTS (offline)",
            isAvailable = true,
            requiresApiKey = false,
            hasApiKey = true
        ))

        // Check Piper - check if installed
        val piperEngine = PiperEngine(context, scope)
        engineList.add(TtsEngineInfo(
            id = "piper",
            name = "Piper",
            description = "Local, offline, high-quality TTS",
            isAvailable = piperEngine.isInstalled,
            isDownloading = piperEngine.isDownloading,
            downloadProgress = piperEngine.downloadProgress.value,
            requiresApiKey = false,
            hasApiKey = true
        ))

        // Check Kokoro - check if installed
        val kokoroAvailable = isKokoroInstalled()
        engineList.add(TtsEngineInfo(
            id = "kokoro",
            name = "Kokoro",
            description = "Fast, local multilingual TTS",
            isAvailable = kokoroAvailable,
            requiresApiKey = false,
            hasApiKey = true
        ))

        // OpenAI TTS - available if API key is configured
        val openaiKey = AppConfigManager.openaiTtsApiKey
        engineList.add(TtsEngineInfo(
            id = "openai",
            name = "OpenAI TTS",
            description = "Premium voices (alloy, echo, fable, onyx, nova, shimmer)",
            isAvailable = openaiKey.isNotBlank(),
            requiresApiKey = true,
            hasApiKey = openaiKey.isNotBlank()
        ))

        // ElevenLabs - available if API key is configured
        val elevenlabsKey = AppConfigManager.elevenlabsApiKey
        engineList.add(TtsEngineInfo(
            id = "elevenlabs",
            name = "ElevenLabs",
            description = "Premium neural voices (Rachel, Domi, Josh...)",
            isAvailable = elevenlabsKey.isNotBlank(),
            requiresApiKey = true,
            hasApiKey = elevenlabsKey.isNotBlank()
        ))

        // Deepgram - available if API key is configured
        val deepgramKey = AppConfigManager.deepgramApiKey
        engineList.add(TtsEngineInfo(
            id = "deepgram",
            name = "Deepgram",
            description = "12 voices: Asteria, Luna, Orion, Zeus...",
            isAvailable = deepgramKey.isNotBlank(),
            requiresApiKey = true,
            hasApiKey = deepgramKey.isNotBlank()
        ))

        // Free Cloud TTS - always available (uses Google Translate API, no key needed)
        engineList.add(TtsEngineInfo(
            id = "freecloud",
            name = "Free Cloud TTS",
            description = "Free, cloud-based, natural voice (no API key)",
            isAvailable = true,
            requiresApiKey = false,
            hasApiKey = true
        ))

        _engines.value = engineList
        Log.i(TAG, "Detected ${engineList.size} TTS engines, ${engineList.count { it.isAvailable }} available")
    }

    /**
     * Get list of available TTS engines
     */
    fun getAvailableEngines(): List<TtsEngineInfo> {
        return _engines.value.filter { it.isAvailable }
    }

    /**
     * Get list of all TTS engines (available and unavailable)
     */
    fun getAllEngines(): List<TtsEngineInfo> {
        return _engines.value
    }

    /**
     * Check if a specific engine is available
     */
    fun isEngineAvailable(engineId: String): Boolean {
        return _engines.value.find { it.id == engineId }?.isAvailable ?: false
    }

    /**
     * Get engine info by ID
     */
    fun getEngineInfo(engineId: String): TtsEngineInfo? {
        return _engines.value.find { it.id == engineId }
    }

    /**
     * Download/install a TTS engine (mock implementation for demo)
     */
    fun downloadEngine(engineId: String): Flow<DownloadProgress> = kotlinx.coroutines.flow.flow {
        val engine = _engines.value.find { it.id == engineId }
        if (engine == null) {
            emit(DownloadProgress(
                engineName = engineId,
                bytesDownloaded = 0,
                totalBytes = 0,
                isComplete = false,
                errorMessage = "Engine not found"
            ))
            return@flow
        }

        try {
            // Emit start of download
            emit(DownloadProgress(
                engineName = engine.name,
                bytesDownloaded = 0,
                totalBytes = 100,
                isComplete = false
            ))

            // Simulate download progress (in real implementation, would download files)
            for (i in 1..100 step 10) {
                kotlinx.coroutines.delay(100)
                emit(DownloadProgress(
                    engineName = engine.name,
                    bytesDownloaded = i.toLong(),
                    totalBytes = 100,
                    isComplete = false
                ))
            }

            // Emit completion
            emit(DownloadProgress(
                engineName = engine.name,
                bytesDownloaded = 100,
                totalBytes = 100,
                isComplete = true
            ))

            // Update engine availability
            detectAvailableEngines()

            Log.i(TAG, "Download of $engineId completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $engineId", e)
            emit(DownloadProgress(
                engineName = engine.name,
                bytesDownloaded = 0,
                totalBytes = 100,
                isComplete = false,
                errorMessage = e.message
            ))
        }
    }

    /**
     * Find a fallback engine if the desired one is unavailable
     */
    fun findFallbackEngine(preferredEngineId: String): TtsEngineInfo? {
        // If preferred is available, use it
        getEngineInfo(preferredEngineId)?.let { if (it.isAvailable) return it }

        // Otherwise, try to find the best alternative
        val available = getAvailableEngines()
        return available.firstOrNull { it.id == "device" }  // Device TTS as last resort
            ?: available.firstOrNull()
            ?: TtsEngineInfo(
                id = "device",
                name = "Android TTS",
                description = "Built-in system TTS (offline)",
                isAvailable = true,
                requiresApiKey = false,
                hasApiKey = true
            )
    }

    /**
     * Get the currently selected engine with fallback
     */
    fun getCurrentEngine(): TtsEngineInfo {
        val current = AppConfigManager.ttsEngine
        return getEngineInfo(current)?.takeIf { it.isAvailable }
            ?: findFallbackEngine(current)
            ?: TtsEngineInfo(
                id = "device",
                name = "Android TTS",
                description = "Built-in system TTS (offline)",
                isAvailable = true,
                requiresApiKey = false,
                hasApiKey = true
            )
    }

    /**
     * Check if Kokoro is installed
     */
    private fun isKokoroInstalled(): Boolean {
        val kokoroDir = File(context.filesDir, "kokoro")
        return kokoroDir.exists() && File(kokoroDir, "kokoro.bin").exists()
    }

    /**
     * Refresh engine list (useful when API keys change)
     */
    fun refresh() {
        scope.launch {
            detectAvailableEngines()
        }
    }

    /**
     * Cleanup when manager is destroyed
     */
    fun destroy() {
        // Cleanup coroutine scope if needed
    }
}
