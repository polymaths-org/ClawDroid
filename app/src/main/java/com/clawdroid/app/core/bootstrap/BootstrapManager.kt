package com.clawdroid.app.core.bootstrap

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class BootstrapProgress(
    val stage: String,
    val detail: String,
)

data class BootstrapResult(
    val prefixDir: String,
    val homeDir: String,
    val bashOutput: String,
)

object BootstrapManager {
    private const val TAG = "ClawDroidBootstrap"
    private const val BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.06.07-r1%2Bapt.android-7/bootstrap-aarch64.zip"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"

    suspend fun ensureBootstrapped(
        context: Context,
        onProgress: (BootstrapProgress) -> Unit,
    ): BootstrapResult = withContext(Dispatchers.IO) {
        val env = EnvironmentSetup.build(context)
        createBaseDirectories(context, env)
        runCatching { SharedFolderManager.ensureSharedFolders() }
            .onFailure { Log.w(TAG, "Unable to create shared folders", it) }

        val bash = File(env.prefix, "bin/bash")
        if (bash.exists()) {
            return@withContext BootstrapResult(
                prefixDir = env.prefix.absolutePath,
                homeDir = env.home.absolutePath,
                bashOutput = "ALREADY_BOOTSTRAPPED"
            )
        }

        onProgress(BootstrapProgress("Downloading", "Fetching Termux bootstrap"))
        val archive = File(context.cacheDir, "bootstrap-aarch64.zip")
        downloadBootstrap(archive, onProgress)

        onProgress(BootstrapProgress("Extracting", "Unpacking Linux runtime"))
        val staging = File(context.filesDir, "usr-bootstrap")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create ${staging.absolutePath}" }
        extractBootstrap(archive, staging)

        onProgress(BootstrapProgress("Linking", "Rebuilding bootstrap symlinks"))
        restoreSymlinks(staging, env.prefix)

        onProgress(BootstrapProgress("Preparing", "Patching paths, permissions, and apt sources"))
        patchTermuxShebangs(staging, env.prefix)
        applyPermissions(staging)
        writeAptSources(staging, env.prefix)

        onProgress(BootstrapProgress("Installing", "Moving runtime into place"))
        env.prefix.deleteRecursively()
        check(staging.renameTo(env.prefix)) {
            "Unable to move ${staging.absolutePath} to ${env.prefix.absolutePath}"
        }

        onProgress(BootstrapProgress("Verifying", "Running bash probe"))
        val output = buildString {
            append(runBashProbe(env))
            append("\n\n")
            append(runAptProbe(env))
        }
        BootstrapResult(
            prefixDir = env.prefix.absolutePath,
            homeDir = env.home.absolutePath,
            bashOutput = output,
        )
    }

    private fun createBaseDirectories(context: Context, env: LinuxEnvironment) {
        listOf(
            env.prefix,
            env.home,
            File(env.home, "projects"),
            File(env.home, ".memory"),
            env.tmp,
            context.cacheDir,
        ).forEach { dir ->
            check(dir.exists() || dir.mkdirs()) {
                "Unable to create ${dir.absolutePath}"
            }
        }
    }

    private fun getRemoteContentLength(urlString: String): Long {
        var currentUrl = urlString
        var redirectCount = 0
        while (redirectCount < 5) {
            try {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                }
                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                        redirectCount++
                        connection.disconnect()
                        continue
                    }
                }
                val length = connection.contentLengthLong
                connection.disconnect()
                return length
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get remote content length for $currentUrl", e)
                return -1L
            }
        }
        return -1L
    }

    private fun downloadBootstrap(
        destination: File,
        onProgress: (BootstrapProgress) -> Unit,
    ) {
        if (destination.exists()) {
            val expectedLength = getRemoteContentLength(BOOTSTRAP_URL)
            if (expectedLength > 0 && destination.length() == expectedLength) {
                Log.i(TAG, "Bootstrap archive already downloaded and matches expected size ($expectedLength bytes). Skipping download.")
                return
            } else {
                Log.w(TAG, "Bootstrap archive size mismatch or content length unavailable. Expected: $expectedLength, Actual: ${destination.length()}. Deleting and re-downloading.")
                destination.delete()
            }
        }

        val tempFile = File(destination.absolutePath + ".tmp")
        tempFile.delete() // clean up any previous failed temp download

        try {
            var currentUrl = BOOTSTRAP_URL
            var redirectCount = 0
            var connection: HttpURLConnection? = null
            while (redirectCount < 5) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                }
                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val location = conn.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                        redirectCount++
                        conn.disconnect()
                        continue
                    }
                }
                connection = conn
                break
            }

            if (connection == null) {
                error("Failed to connect after redirects")
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                error("Server returned status $status")
            }

            val total = connection.contentLengthLong.takeIf { it > 0L }
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var nextReportAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded >= nextReportAt) {
                            val detail = if (total != null) {
                                "${downloaded / 1_000_000} MB / ${total / 1_000_000} MB"
                            } else {
                                "${downloaded / 1_000_000} MB"
                            }
                            onProgress(BootstrapProgress("Downloading", detail))
                            nextReportAt = downloaded + 2_000_000L
                        }
                    }
                }
            }
            connection.disconnect()

            // Verify size if total was known
            if (total != null && tempFile.length() != total) {
                error("Downloaded file size (${tempFile.length()}) does not match content length ($total)")
            }

            // Rename temp to final destination
            if (!tempFile.renameTo(destination)) {
                error("Failed to rename temp file to ${destination.absolutePath}")
            }
        } catch (e: Exception) {
            tempFile.delete() // clean up partial file
            throw e
        }
    }

    private fun extractBootstrap(archive: File, destination: File) {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outFile = File(destination, entry.name)
                val destinationPath = destination.canonicalPath
                val outPath = outFile.canonicalPath
                check(outPath == destinationPath || outPath.startsWith("$destinationPath/")) {
                    "Refusing to extract outside prefix: ${entry.name}"
                }

                if (entry.isDirectory) {
                    check(outFile.exists() || outFile.mkdirs()) {
                        "Unable to create ${outFile.absolutePath}"
                    }
                } else {
                    outFile.parentFile?.let { parent ->
                        check(parent.exists() || parent.mkdirs()) {
                            "Unable to create ${parent.absolutePath}"
                        }
                    }
                    outFile.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun restoreSymlinks(stagingPrefix: File, finalPrefix: File) {
        val symlinkFile = File(stagingPrefix, "SYMLINKS.txt")
        if (!symlinkFile.exists()) return

        symlinkFile.forEachLine { line ->
            val splitAt = line.indexOf('\u2190')
            if (splitAt <= 0 || splitAt >= line.lastIndex) return@forEachLine

            var target = line.substring(0, splitAt)
            val linkPath = line.substring(splitAt + 1).removePrefix("./")
            if (target.startsWith("$TERMUX_PREFIX/")) {
                target = finalPrefix.absolutePath + target.removePrefix(TERMUX_PREFIX)
            }

            val link = File(stagingPrefix, linkPath)
            link.parentFile?.mkdirs()
            if (link.exists()) link.delete()

            runCatching {
                Os.symlink(target, link.absolutePath)
            }.onFailure {
                Log.w(TAG, "Unable to create symlink ${link.absolutePath} -> $target", it)
            }
        }
    }

    private fun patchTermuxShebangs(stagingPrefix: File, finalPrefix: File) {
        sequenceOf(
            File(stagingPrefix, "bin"),
            File(stagingPrefix, "lib/apt/methods"),
        ).filter { it.exists() }.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val bytes = runCatching { file.inputStream().use { it.readNBytes(128) } }
                        .getOrNull()
                        ?: return@forEach
                    val header = bytes.toString(Charsets.UTF_8)
                    if (!header.startsWith("#!$TERMUX_PREFIX")) return@forEach

                    val patched = file.readText()
                        .replaceFirst("#!$TERMUX_PREFIX", "#!${finalPrefix.absolutePath}")
                    file.writeText(patched)
                }
        }
    }

    private fun applyPermissions(prefix: File) {
        prefix.walkTopDown().forEach { file ->
            runCatching {
                if (file.isDirectory) {
                    Os.chmod(file.absolutePath, 0b111_000_000)
                } else {
                    Os.chmod(file.absolutePath, 0b111_000_000)
                }
            }.onFailure {
                Log.w(TAG, "Unable to chmod ${file.absolutePath}", it)
            }
        }
    }

    private fun writeAptSources(prefix: File, finalPrefix: File) {
        val sources = File(prefix, "etc/apt/sources.list")
        sources.parentFile?.mkdirs()
        sources.writeText("deb https://packages.termux.dev/apt/termux-main stable main\n")

        File(prefix, "etc/apt/apt.conf.d").mkdirs()
        File(prefix, "var/cache/apt/archives/partial").mkdirs()
        File(prefix, "var/lib/apt/lists/partial").mkdirs()
        File(prefix, "tmp").mkdirs()

        File(prefix, "etc/apt/apt-clawdroid.conf").writeText(
            """
            Dir "${finalPrefix.absolutePath}";
            Dir::State "var/lib/apt";
            Dir::State::status "${finalPrefix.absolutePath}/var/lib/dpkg/status";
            Dir::Cache "var/cache/apt";
            Dir::Etc "etc/apt";
            Dir::Etc::sourcelist "sources.list";
            Dir::Etc::sourceparts "sources.list.d";
            Dir::Etc::main "apt.conf";
            Dir::Etc::parts "apt.conf.d";
            Dir::Bin::methods "${finalPrefix.absolutePath}/lib/apt/methods";
            Dir::Bin::apt-key "${finalPrefix.absolutePath}/bin/apt-key";
            Apt::Key::gpgvcommand "${finalPrefix.absolutePath}/bin/gpgv";
            Acquire::https::CaInfo "${finalPrefix.absolutePath}/etc/tls/cert.pem";
            APT::Architecture "aarch64";
            """.trimIndent() + "\n"
        )
    }

    private fun runBashProbe(env: LinuxEnvironment): String {
        val bash = File(env.prefix, "bin/bash")
        check(bash.exists()) { "Missing bash at ${bash.absolutePath}" }

        val command = listOf(
            bash.absolutePath,
            "--noprofile",
            "--norc",
            "-c",
            "echo CLAWDROID_BASH_OK; id; echo PREFIX=\$PREFIX; command -v script || true",
        )
        val process = ProcessBuilder(command)
            .directory(env.home)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(env.values)
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val completed = process.waitFor(15, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            error("Bash probe timed out")
        }

        val exitCode = process.exitValue()
        check(exitCode == 0) {
            "Bash probe exited $exitCode:\n$output"
        }
        return output
    }

    private fun runAptProbe(env: LinuxEnvironment): String {
        val apt = File(env.prefix, "bin/apt")
        check(apt.exists()) { "Missing apt at ${apt.absolutePath}" }

        val command = listOf(
            apt.absolutePath,
            "update",
        )
        val process = ProcessBuilder(command)
            .directory(env.home)
            .redirectErrorStream(true)
            .apply {
                environment().clear()
                environment().putAll(env.values)
            }
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val completed = process.waitFor(90, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            error("Apt probe timed out")
        }

        val exitCode = process.exitValue()
        check(exitCode == 0) {
            "Apt probe exited $exitCode:\n$output"
        }
        return "APT_UPDATE_OK\n$output"
    }
}
