package com.clawdroid.app.core.bootstrap

import android.os.Environment
import java.io.File

object SharedFolderManager {
    fun ensureSharedFolders(): File {
        val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ClawDroid")
        listOf(
            root,
            File(root, "Inbox"),
            File(root, "Output"),
            File(root, "Projects"),
            File(root, "Exports"),
        ).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
        return root
    }
}
