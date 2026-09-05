package com.clawdroid.app.core.bootstrap

import android.content.Context
import java.io.File

data class LinuxEnvironment(
    val prefix: File,
    val home: File,
    val tmp: File,
    val values: Map<String, String>,
)

object EnvironmentSetup {
    fun build(context: Context): LinuxEnvironment {
        val filesDir = context.filesDir
        val prefix = File(filesDir, "usr")
        val home = File(filesDir, "home")
        val tmp = File(filesDir, "tmp")

        val values = mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to home.absolutePath,
            "PATH" to "${prefix.absolutePath}/bin:${prefix.absolutePath}/bin/applets:/system/bin",
            "LD_LIBRARY_PATH" to "${prefix.absolutePath}/lib",
            "TMPDIR" to tmp.absolutePath,
            "APT_CONFIG" to "${prefix.absolutePath}/etc/apt/apt-clawdroid.conf",
            "SSL_CERT_FILE" to "${prefix.absolutePath}/etc/tls/cert.pem",
            "CURL_CA_BUNDLE" to "${prefix.absolutePath}/etc/tls/cert.pem",
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "SHELL" to "${prefix.absolutePath}/bin/bash",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system",
        )

        return LinuxEnvironment(prefix = prefix, home = home, tmp = tmp, values = values)
    }
}
