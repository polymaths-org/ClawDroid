package com.clawdroid.app.core.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OfflineTtsInstallState(
    val modelId: String = OfflineTtsModelCatalog.DEFAULT_MODEL_ID,
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
)

class OfflineTtsDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(currentState(OfflineTtsModelCatalog.byId(selectedModelId())))
    val state: StateFlow<OfflineTtsInstallState> = _state.asStateFlow()

    fun refresh(preset: OfflineTtsModelPreset = OfflineTtsModelCatalog.byId(selectedModelId())) {
        _state.value = currentState(preset)
    }

    fun install(preset: OfflineTtsModelPreset) {
        if (_state.value.downloading) return
        scope.launch {
            val archive = File(context.cacheDir, "${preset.id}.tar.bz2.tmp")
            val extractDir = File(context.cacheDir, "${preset.id}-extract")
            val targetDir = OfflineTtsModelCatalog.modelDir(context, preset)
            runCatching {
                _state.value = currentState(preset).copy(downloading = true, progress = 0f, message = "Downloading ${preset.label}")
                archive.parentFile?.mkdirs()
                archive.delete()
                extractDir.deleteRecursively()
                download(preset.downloadUrl, archive, 0f, 0.72f)

                _state.value = _state.value.copy(progress = 0.76f, message = "Extracting model")
                extractArchive(archive, extractDir)
                val extractedRoot = File(extractDir, preset.archiveRoot).takeIf { it.isDirectory } ?: extractDir
                validateRequiredFiles(extractedRoot, preset)

                _state.value = _state.value.copy(progress = 0.92f, message = "Installing model")
                targetDir.deleteRecursively()
                targetDir.parentFile?.mkdirs()
                if (!extractedRoot.renameTo(targetDir)) {
                    copyRecursively(extractedRoot, targetDir)
                    extractedRoot.deleteRecursively()
                }
                validateRequiredFiles(targetDir, preset)
                _state.value = currentState(preset).copy(progress = 1f, message = "${preset.label} installed")
            }.onFailure { error ->
                _state.value = currentState(preset).copy(
                    downloading = false,
                    progress = 0f,
                    message = "Install failed: ${error.message ?: error::class.java.simpleName}",
                )
            }
            archive.delete()
            extractDir.deleteRecursively()
        }
    }

    fun delete(preset: OfflineTtsModelPreset) {
        OfflineTtsModelCatalog.modelDir(context, preset).deleteRecursively()
        refresh(preset)
    }

    private fun selectedModelId(): String = com.clawdroid.app.core.config.AppConfigManager.offlineTtsModelId

    private fun currentState(preset: OfflineTtsModelPreset): OfflineTtsInstallState =
        OfflineTtsInstallState(
            modelId = preset.id,
            installed = OfflineTtsModelCatalog.isInstalled(context, preset),
            downloading = false,
            progress = 0f,
            message = if (OfflineTtsModelCatalog.isInstalled(context, preset)) "${preset.label} installed" else "${preset.label} not installed",
        )

    private fun download(url: String, destination: File, progressStart: Float, progressEnd: Float) {
        val connection = openRedirectingConnection(url)
        val total = connection.contentLengthLong
        var readTotal = 0L
        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    readTotal += read
                    if (total > 0L) {
                        val progress = progressStart + (readTotal.toFloat() / total.toFloat()) * (progressEnd - progressStart)
                        _state.value = _state.value.copy(progress = progress.coerceIn(progressStart, progressEnd))
                    }
                }
            }
        }
        connection.disconnect()
        check(destination.length() > 0L) { "Downloaded file is empty" }
    }

    private fun openRedirectingConnection(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 180_000
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (!next.isNullOrBlank()) {
                    current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
                    return@repeat
                }
            }
            check(code in 200..299) { "HTTP $code" }
            return connection
        }
        error("Too many redirects")
    }

    private fun extractArchive(archive: File, outputDir: File) {
        outputDir.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val outFile = File(outputDir, entry.name).canonicalFile
                val canonicalRoot = outputDir.canonicalFile
                check(outFile.path.startsWith(canonicalRoot.path)) { "Unsafe archive path: ${entry.name}" }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { tar.copyTo(it) }
                }
            }
        }
    }

    private fun validateRequiredFiles(dir: File, preset: OfflineTtsModelPreset) {
        val missing = preset.requiredFiles.filterNot { File(dir, it).exists() }
        check(missing.isEmpty()) { "Missing ${missing.joinToString()}" }
    }

    private fun copyRecursively(from: File, to: File) {
        from.copyRecursively(to, overwrite = true)
    }
}
