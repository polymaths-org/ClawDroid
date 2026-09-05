package com.clawdroid.app.core.voice

import android.content.Context
import java.io.File

enum class OfflineTtsEngineType { VITS }

data class OfflineTtsModelPreset(
    val id: String,
    val label: String,
    val engineType: OfflineTtsEngineType,
    val downloadUrl: String,
    val archiveRoot: String,
    val modelFile: String,
    val requiredFiles: List<String>,
    val defaultSpeakerId: Int,
    val speakerLabels: List<Pair<Int, String>>,
)

object OfflineTtsModelCatalog {
    const val DEFAULT_MODEL_ID = "vits-piper-en_US-glados"

    val presets = listOf(
        OfflineTtsModelPreset(
            id = DEFAULT_MODEL_ID,
            label = "GLaDOS English",
            engineType = OfflineTtsEngineType.VITS,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-glados.tar.bz2",
            archiveRoot = "vits-piper-en_US-glados",
            modelFile = "en_US-glados.onnx",
            requiredFiles = listOf("en_US-glados.onnx", "tokens.txt", "espeak-ng-data"),
            defaultSpeakerId = 0,
            speakerLabels = listOf(
                0 to "GLaDOS",
            ),
        ),
    )

    fun byId(id: String): OfflineTtsModelPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()

    fun modelDir(context: Context, preset: OfflineTtsModelPreset): File =
        File(context.filesDir, "tts/sherpa/${preset.id}")

    fun isInstalled(context: Context, preset: OfflineTtsModelPreset): Boolean {
        val dir = modelDir(context, preset)
        return dir.isDirectory && preset.requiredFiles.all { File(dir, it).exists() }
    }
}
