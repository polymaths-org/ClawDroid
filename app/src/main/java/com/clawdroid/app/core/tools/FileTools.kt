package com.clawdroid.app.core.tools

import android.content.Context
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.core.bootstrap.SharedFolderManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ReadFileTool {
    fun execute(context: Context, path: String, startLine: Int?, endLine: Int?): JSONObject {
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
    fun execute(context: Context, path: String, content: String): JSONObject {
        val file = resolveAgentPath(context, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return JSONObject()
            .put("path", file.absolutePath)
            .put("bytes", file.length())
    }
}

object EditFileTool {
    fun execute(context: Context, path: String, search: String, replace: String): JSONObject {
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
    fun execute(context: Context, path: String): JSONObject {
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

private fun resolveAgentPath(context: Context, rawPath: String): File {
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
