package com.clawdroid.app.core.channels.whatsapp

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.channels.Channel
import com.clawdroid.app.core.channels.ChannelMessage
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.tools.CommandTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * WhatsApp channel backed by a sandbox CLI bridge such as WACLI/open-wa.
 *
 * The UI starts the login/session command and renders the QR from its output.
 * Runtime send/poll behavior uses user-configured command templates:
 * - send_command supports {target} and {text}
 * - poll_command should print JSON array messages or newline text messages
 */
class WhatsAppChannel(private val context: Context) : Channel {

    override val type: String = "whatsapp"
    override var isConnected: Boolean = false
        private set

    private var allowedContacts: List<String> = emptyList()
    private var sessionCommand: String = ""
    private var sendCommand: String = ""
    private var pollCommand: String = ""
    private var lastSeenId: String = ""

    companion object {
        private const val TAG = "WhatsAppChannel"
    }

    override suspend fun connect(config: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            allowedContacts = config["allowed_contacts"]
                .orEmpty()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            sessionCommand = config["cli_command"].orEmpty()
            sendCommand = config["send_command"].orEmpty()
            pollCommand = config["poll_command"].orEmpty()
            if (AppConfigManager.whatsappAutoStartOnLaunch) {
                WacliBridge.startListener(context).getOrElse { error ->
                    Log.w(TAG, "wacli listener start failed", error)
                }
            } else {
                WacliBridge.prepare(context, config["phone"]?.takeIf { it.isNotBlank() })
            }
            isConnected = true
            Log.i(TAG, "WhatsApp CLI channel configured")
        }.map { }
    }

    override suspend fun disconnect() {
        isConnected = false
        Log.i(TAG, "WhatsApp CLI channel disconnected")
    }

    override suspend fun sendMessage(target: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val rendered = if (sendCommand.isNotBlank()) {
                renderTemplate(sendCommand, target, text)
            } else {
                WacliBridge.sendTextCommand(context, target, text)
            }
            val result = CommandTool.execute(context, rendered, null, timeoutSeconds = 60)
            check(result.exitCode == 0) {
                "WhatsApp send failed (${result.exitCode}): ${result.output}"
            }
        }
    }

    override suspend fun pollMessages(): List<ChannelMessage> {
        if (!isConnected) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val queued = readQueuedMessages()
                if (queued.isNotEmpty()) return@runCatching queued
                if (pollCommand.isBlank()) return@runCatching emptyList()
                val result = CommandTool.execute(context, pollCommand, null, timeoutSeconds = 60)
                if (result.exitCode != 0 || result.output.isBlank()) return@runCatching emptyList()
                parseMessages(result.output)
            }.getOrElse {
                Log.w(TAG, "WhatsApp CLI poll failed", it)
                emptyList()
            }
        }
    }

    private fun readQueuedMessages(): List<ChannelMessage> {
        val queueDir = File(context.filesDir, "tmp/pending_replies")
        if (!queueDir.exists()) return emptyList()
        val archiveDir = File(context.filesDir, "tmp/processed_replies").also { it.mkdirs() }
        return queueDir.listFiles { file -> file.isFile && file.extension == "msg" }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .mapNotNull { file ->
                val values = file.readLines()
                    .mapNotNull { line ->
                        val split = line.indexOf('=')
                        if (split <= 0) null else line.take(split) to line.drop(split + 1)
                    }
                    .toMap()
                val sender = values["FROM"].orEmpty()
                val chat = values["GROUP"].orEmpty()
                val text = values["TEXT"].orEmpty()
                if (sender.isBlank() || text.isBlank() || !isAllowed(sender)) return@mapNotNull null
                runCatching { file.renameTo(File(archiveDir, file.name)) }
                ChannelMessage(
                    id = file.nameWithoutExtension,
                    sender = sender,
                    text = text,
                    channelType = type,
                    timestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                )
            }
    }

    private fun parseMessages(output: String): List<ChannelMessage> {
        val trimmed = output.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val sender = item.optString("sender", item.optString("from", ""))
                val text = item.optString("text", item.optString("body", ""))
                val id = item.optString("id", "${sender}_${item.optLong("timestamp", System.currentTimeMillis())}")
                val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                if (sender.isBlank() || text.isBlank() || !isAllowed(sender) || id == lastSeenId) return@mapNotNull null
                lastSeenId = id
                ChannelMessage(id = id, sender = sender, text = text, channelType = type, timestamp = timestamp)
            }
        }

        return trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val splitAt = line.indexOf(':')
                if (splitAt <= 0) return@mapNotNull null
                val sender = line.substring(0, splitAt).trim()
                val text = line.substring(splitAt + 1).trim()
                val id = "${sender}_${line.hashCode()}"
                if (!isAllowed(sender) || id == lastSeenId) return@mapNotNull null
                lastSeenId = id
                ChannelMessage(id = id, sender = sender, text = text, channelType = type)
            }
            .toList()
    }

    private fun isAllowed(sender: String): Boolean {
        if (allowedContacts.isEmpty()) return true
        val normalized = sender.lowercase()
        return allowedContacts.any { normalized.contains(it) }
    }

    private fun renderTemplate(template: String, target: String, text: String): String {
        return template
            .replace("{target}", shellQuote(target))
            .replace("{text}", shellQuote(text))
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
