package com.clawdroid.app.core.interpole

import android.content.Context
import com.clawdroid.app.core.config.AppConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InterpoleMemorySync(context: Context) {
    private val appContext = context.applicationContext
    private val memoryDir: File get() = File(appContext.filesDir, "home/.memory")

    suspend fun sync(direction: String = "bidirectional", force: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            if (!AppConfigManager.memorySyncEnabled) {
                return@withContext JSONObject()
                    .put("ok", false)
                    .put("error", "memory_sync_disabled")
                    .put("advice", "Enable memory sync in INTERPOLE settings before syncing.")
            }
            if (!force && AppConfigManager.memoryAutoSyncEnabled) {
                val elapsedMinutes = (System.currentTimeMillis() - AppConfigManager.memoryLastSyncedAt) / 60_000L
                if (elapsedMinutes < AppConfigManager.memorySyncIntervalMinutes) {
                    return@withContext JSONObject()
                        .put("ok", true)
                        .put("skipped", true)
                        .put("reason", "interval_not_elapsed")
                        .put("next_sync_in_minutes", AppConfigManager.memorySyncIntervalMinutes - elapsedMinutes)
                }
            }

            val cleanDirection = when (direction.lowercase().trim()) {
                "push", "pull", "bidirectional" -> direction.lowercase().trim()
                else -> "bidirectional"
            }
            val response = InterpoleClient(appContext).rpc(
                action = "memory_sync",
                params = JSONObject()
                    .put("protocol", "clawdroid.memory.v1")
                    .put("direction", cleanDirection)
                    .put("source", "android")
                    .put("files", JSONArray(readLocalMemoryFiles())),
            )

            applyPulledFiles(response)
            AppConfigManager.memoryLastSyncedAt = System.currentTimeMillis()
            response
                .put("ok", true)
                .put("protocol", "clawdroid.memory.v1")
                .put("direction", cleanDirection)
        }

    private fun readLocalMemoryFiles(): List<JSONObject> {
        memoryDir.mkdirs()
        return memoryDir
            .walkTopDown()
            .maxDepth(1)
            .filter { it.isFile && it.extension.lowercase() in setOf("md", "txt", "json") }
            .map { file ->
                JSONObject()
                    .put("path", file.name)
                    .put("updated_at", file.lastModified())
                    .put("content", file.readText())
            }
            .toList()
    }

    private fun applyPulledFiles(response: JSONObject) {
        val files = response.optJSONArray("files") ?: response.optJSONArray("pulled_files") ?: return
        memoryDir.mkdirs()
        for (index in 0 until files.length()) {
            val item = files.optJSONObject(index) ?: continue
            val path = item.optString("path").substringAfterLast('/').substringAfterLast('\\')
            if (path.isBlank() || path.startsWith(".")) continue
            if (path.substringAfterLast('.', "").lowercase() !in setOf("md", "txt", "json")) continue
            File(memoryDir, path).writeText(item.optString("content"))
        }
    }
}
