package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.core.bootstrap.SharedFolderManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Environment
import android.content.pm.PackageManager
import android.Manifest
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay

object ReadFileTool {
    suspend fun execute(context: Context, path: String, startLine: Int?, endLine: Int?): JSONObject {
        val file = resolveAgentPath(context, path)
        check(file.exists() && file.isFile) { "File not found: ${file.absolutePath}" }
        val lines = file.readLines()
        val from = ((startLine ?: 1) - 1).coerceAtLeast(0)
        val toExclusive = (endLine ?: lines.size).coerceIn(0, lines.size)
        val selected = if (from >= toExclusive) emptyList() else lines.subList(from, toExclusive)
        return JSONObject()
            .put("path", file.absolutePath)
            .put("start_line", from + 1)
            .put("end_line", from + selected.size)
            .put("content", selected.joinToString("\n"))
    }
}

object WriteFileTool {
    suspend fun execute(context: Context, path: String, content: String): JSONObject {
        val file = resolveAgentPath(context, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return JSONObject()
            .put("path", file.absolutePath)
            .put("bytes", file.length())
    }
}

object EditFileTool {
    suspend fun execute(context: Context, path: String, search: String, replace: String): JSONObject {
        val file = resolveAgentPath(context, path)
        check(file.exists() && file.isFile) { "File not found: ${file.absolutePath}" }
        val original = file.readText()
        check(search in original) { "Search text was not found in ${file.absolutePath}" }
        val updated = original.replace(search, replace, ignoreCase = false)
        file.writeText(updated)
        return JSONObject()
            .put("path", file.absolutePath)
            .put("replacements", original.windowed(search.length).count { it == search })
    }
}

object ListDirectoryTool {
    suspend fun execute(context: Context, path: String): JSONObject {
        val dir = resolveAgentPath(context, path)
        check(dir.exists() && dir.isDirectory) { "Directory not found: ${dir.absolutePath}" }
        val entries = dir.listFiles().orEmpty().sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name })
        return JSONObject()
            .put("path", dir.absolutePath)
            .put(
                "entries",
                JSONArray().apply {
                    entries.forEach { file ->
                        put(
                            JSONObject()
                                .put("name", file.name)
                                .put("path", file.absolutePath)
                                .put("type", if (file.isDirectory) "directory" else "file")
                                .put("bytes", if (file.isFile) file.length() else JSONObject.NULL)
                        )
                    }
                },
            )
    }
}

private fun isStoragePermissionGranted(context: Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

suspend fun checkAndRequestStoragePermission(context: Context, path: String): Boolean {
    val isExternal = path.startsWith("/storage") || path.startsWith("/sdcard") || path.contains("/storage/emulated")
    if (!isExternal) return true

    if (isStoragePermissionGranted(context)) {
        return true
    }

    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, "ClawDroid: Storage Permission Required", Toast.LENGTH_LONG).show()
    }
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {}
        }
    } else {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
    }

    // Poll for 15 seconds (30 checks of 500ms)
    repeat(30) {
        delay(500)
        if (isStoragePermissionGranted(context)) {
            return true
        }
    }
    return false
}

private suspend fun resolveAgentPath(context: Context, rawPath: String): File {
    if (!checkAndRequestStoragePermission(context, rawPath)) {
        throw SecurityException("The user did not grant storage permission.")
    }
    val env = EnvironmentSetup.build(context)
    val sharedRoot = runCatching { SharedFolderManager.ensureSharedFolders() }.getOrNull()
    val file = if (rawPath.startsWith("/")) File(rawPath) else File(env.home, rawPath)
    val canonical = file.canonicalFile
    val allowedRoots = listOfNotNull(env.home, env.prefix, env.tmp, sharedRoot).map { it.canonicalFile }
    check(allowedRoots.any { root -> canonical == root || canonical.path.startsWith(root.path + File.separator) }) {
        "Refusing to access path outside sandbox/shared folders: ${canonical.absolutePath}"
    }
    return canonical
}

