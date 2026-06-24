package com.clawdroid.app.core.interpole

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class FileTransferClient(context: Context) {
    private val configRepository = InterpoleConfigRepository(context.applicationContext)

    suspend fun pushFile(localPath: String, desktopPath: String): Boolean = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig()
        val source = File(localPath)
        if (!source.isFile) return@withContext false
        val url = URL("${baseFileUrl(config)}/upload?path=${desktopPath.urlEncode()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 10_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Content-Length", source.length().toString())
        }
        source.inputStream().use { input ->
            connection.outputStream.use { output -> input.copyTo(output) }
        }
        connection.responseCode in 200..299
    }

    suspend fun pullFile(desktopPath: String, localDest: String): Boolean = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig()
        val target = File(localDest)
        target.parentFile?.mkdirs()
        val url = URL("${baseFileUrl(config)}/download?path=${desktopPath.urlEncode()}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 60_000
        }
        if (connection.responseCode !in 200..299) return@withContext false
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }

    private fun baseFileUrl(config: InterpoleConfig): String {
        val rpcUrl = URL(config.rpcBaseUrl)
        val host = config.tailscaleIp.takeIf { it.isNotBlank() } ?: rpcUrl.host
        return "${rpcUrl.protocol}://$host:${config.fileTransferPort}"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
