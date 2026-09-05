package com.clawdroid.app.core.channels.whatsapp

import android.content.Context
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.core.bootstrap.EnvironmentSetup
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.tools.CommandTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WhatsAppListenerStatus(
    val syncRunning: Boolean,
    val daemonRunning: Boolean,
    val daemonPid: String,
    val lastHeartbeatSecondsAgo: Long?,
    val logTail: String,
) {
    val isRunning: Boolean get() = syncRunning && daemonRunning
}

object WacliBridge {
    private const val SETTINGS_DIR = ".native-skill"
    private const val LISTENER_SCRIPT = "listener_daemon.sh"
    private const val STARTUP_SCRIPT = "startup.sh"

    suspend fun prepare(context: Context, phone: String? = null) = withContext(Dispatchers.IO) {
        BootstrapManager.ensureBootstrapped(context) { }
        val env = EnvironmentSetup.build(context)
        env.home.mkdirs()
        env.tmp.mkdirs()
        File(env.home, SETTINGS_DIR).mkdirs()
        File(context.filesDir, "wacli-data").mkdirs()
        writeBashrc(env.home)
        writeSettings(context, phone)
        writeListenerScripts(context)
        writeAgentSkillHints(context)
    }

    fun authCommand(context: Context, phone: String): String {
        val base = context.filesDir.absolutePath
        val log = "$base/tmp/wacli_auth.log"
        return buildString {
            append(installCommand(context))
            append("; ")
            append("rm -f ${shellQuote(log)}; ")
            append(wacliPrefix(context))
            append(" auth --phone ")
            append(shellQuote(phone))
            append(" 2>&1 | tee ")
            append(shellQuote(log))
        }
    }

    fun extractPairingCode(output: String): String? {
        return Regex("""[A-Z0-9]{4}-[A-Z0-9]{4}""")
            .find(output)
            ?.value
    }

    fun sendTextCommand(context: Context, target: String, text: String): String {
        return "${wacliPrefix(context)} send text --to ${shellQuote(target)} --message ${shellQuote(text)}"
    }

    suspend fun startListener(context: Context): Result<String> = runCatching {
        prepare(context, AppConfigManager.whatsappPhone.takeIf { it.isNotBlank() })
        val startup = File(File(EnvironmentSetup.build(context).home, SETTINGS_DIR), STARTUP_SCRIPT)
        val result = CommandTool.execute(
            context = context,
            command = "sh ${shellQuote(startup.absolutePath)}",
            cwd = context.filesDir.absolutePath,
            timeoutSeconds = 45,
        )
        check(result.exitCode == 0) { result.output.ifBlank { "Listener startup failed." } }
        result.output
    }

    suspend fun stopListener(context: Context): Result<String> = runCatching {
        val base = context.filesDir.absolutePath
        val command = """
            if [ -f ${shellQuote("$base/tmp/listener.pid")} ]; then kill -TERM "${'$'}(cat ${shellQuote("$base/tmp/listener.pid")})" 2>/dev/null || true; fi
            ps aux | grep "[w]acli sync" | awk '{print ${'$'}2}' | xargs -r kill -TERM 2>/dev/null || true
            ps aux | grep "[l]istener_daemon.sh" | awk '{print ${'$'}2}' | xargs -r kill -TERM 2>/dev/null || true
            rm -f ${shellQuote("$base/tmp/listener.pid")}
            echo "WhatsApp listener stopped"
        """.trimIndent()
        val result = CommandTool.execute(context, command, context.filesDir.absolutePath, timeoutSeconds = 15)
        check(result.exitCode == 0) { result.output.ifBlank { "Unable to stop listener." } }
        result.output
    }

    suspend fun status(context: Context): WhatsAppListenerStatus = withContext(Dispatchers.IO) {
        val base = context.filesDir
        val tmp = File(base, "tmp")
        val pid = File(tmp, "listener.pid").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val heartbeat = File(tmp, "listener_heartbeat.txt").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
        val now = System.currentTimeMillis() / 1000
        val ps = runCatching {
            CommandTool.execute(context, "ps aux", base.absolutePath, timeoutSeconds = 8).output
        }.getOrDefault("")
        val logTail = File(tmp, "native-skill-events.log")
            .takeIf { it.exists() }
            ?.readLines()
            ?.takeLast(12)
            ?.joinToString("\n")
            .orEmpty()
        WhatsAppListenerStatus(
            syncRunning = ps.contains("wacli sync"),
            daemonRunning = ps.contains("listener_daemon.sh") || pid.isNotBlank(),
            daemonPid = pid,
            lastHeartbeatSecondsAgo = heartbeat?.let { (now - it).coerceAtLeast(0) },
            logTail = logTail,
        )
    }

    internal fun wacliPrefix(context: Context): String {
        val base = context.filesDir.absolutePath
        val glibcLib = "$base/usr/glibc/lib"
        val ld = "$base/usr/glibc/bin/ld.so"
        val wacli = "$base/wacli"
        val store = "$base/wacli-data"
        return "env LD_LIBRARY_PATH=${shellQuote(glibcLib)} WACLI_STORE_DIR=${shellQuote(store)} ${shellQuote(ld)} ${shellQuote(wacli)}"
    }

    internal fun installCommand(context: Context): String {
        val base = context.filesDir.absolutePath
        val wacli = "$base/wacli"
        return """
            mkdir -p ${shellQuote("$base/tmp")} ${shellQuote("$base/wacli-data")}
            if [ ! -f ${shellQuote("$base/usr/glibc/bin/ld.so")} ]; then pkg install -y glibc >/dev/null 2>&1 || true; fi
            if [ ! -f ${shellQuote(wacli)} ]; then
              WACLI_URL=${'$'}(curl -s https://api.github.com/repos/itsToggle/wacli/releases/latest | grep "browser_download_url" | grep "linux_arm64" | head -1 | cut -d'"' -f4)
              [ -n "${'$'}WACLI_URL" ] && curl -L -o ${shellQuote(wacli)} "${'$'}WACLI_URL"
              chmod +x ${shellQuote(wacli)} 2>/dev/null || true
            fi
        """.trimIndent().replace('\n', ';')
    }

    private fun writeBashrc(home: File) {
        val bashrc = File(home, ".bashrc")
        val block = """

            # ClawDroid terminal fixes
            alias clear='printf "\033[2J\033[H"'
            cls() { printf "\033c"; }
            export PS1='\[\033[01;32m\]\u@clawdroid\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\${'$'} '
            export TERM=xterm-256color
            alias ll='ls -la'
            alias la='ls -A'
            alias l='ls -CF'
            alias psall='ps aux | grep -v grep'
            kname() { ps aux | grep -v grep | grep "${'$'}1" | awk '{print ${'$'}2}' | xargs -r kill -9; }
            pidof() { ps aux | grep -v grep | grep "${'$'}1" | awk '{print ${'$'}2}'; }
            alias bgjobs='jobs -l'
        """.trimIndent()
        val current = if (bashrc.exists()) bashrc.readText() else ""
        if (!current.contains("# ClawDroid terminal fixes")) {
            bashrc.writeText(current.trimEnd() + "\n" + block + "\n")
        }
    }

    private fun writeSettings(context: Context, phone: String?) {
        val env = EnvironmentSetup.build(context)
        val file = File(File(env.home, SETTINGS_DIR), "settings.json")
        val monitoredChats = AppConfigManager.whatsappMonitoredChats
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val settings = JSONObject()
            .put(
                "whatsapp",
                JSONObject()
                    .put("enabled", AppConfigManager.whatsappEnabled)
                    .put("phone", phone ?: AppConfigManager.whatsappPhone)
                    .put("auto_reply", AppConfigManager.whatsappAutoReply)
                    .put("notify_on_message", AppConfigManager.whatsappNotifyOnMessage)
                    .put("auto_download_media", AppConfigManager.whatsappAutoDownloadMedia)
                    .put("send_read_receipts", AppConfigManager.whatsappSendReadReceipts)
                    .put("show_typing_indicator", AppConfigManager.whatsappShowTypingIndicator)
                    .put("reply_delay_seconds", AppConfigManager.whatsappReplyDelaySeconds)
                    .put("reply_mode", AppConfigManager.whatsappReplyMode)
                    .put("reply_language", AppConfigManager.whatsappReplyLanguage)
                    .put("custom_reply_prompt", AppConfigManager.whatsappCustomReplyPrompt)
                    .put(
                        "monitored_chats",
                        JSONArray(monitoredChats.map { jid ->
                            JSONObject()
                                .put("jid", jid)
                                .put("name", jid)
                                .put("active", true)
                                .put("auto_reply", AppConfigManager.whatsappAutoReply)
                                .put("notify", AppConfigManager.whatsappNotifyOnMessage)
                        }),
                    )
                    .put("max_stored_messages", AppConfigManager.whatsappMaxStoredMessages)
                    .put("auto_sync", true)
                    .put("sync_interval_seconds", 60)
                    .put("listener_port", AppConfigManager.whatsappListenerPort)
                    .put("webhook_enabled", false)
                    .put("auto_start_on_launch", AppConfigManager.whatsappAutoStartOnLaunch),
            )
        file.parentFile?.mkdirs()
        file.writeText(settings.toString(2))
    }

    private fun writeListenerScripts(context: Context) {
        val env = EnvironmentSetup.build(context)
        val dir = File(env.home, SETTINGS_DIR).also { it.mkdirs() }
        val listener = File(dir, LISTENER_SCRIPT)
        val startup = File(dir, STARTUP_SCRIPT)
        listener.writeText(listenerScript(context.filesDir.absolutePath))
        startup.writeText(startupScript(context.filesDir.absolutePath))
        listener.setExecutable(true)
        startup.setExecutable(true)
    }

    private fun writeAgentSkillHints(context: Context) {
        val env = EnvironmentSetup.build(context)
        val skill = File(env.home, "WHATSAPP_CHANNEL.md")
        skill.writeText(
            """
            # WhatsApp Channel

            Pending messages are queued in `${context.filesDir.absolutePath}/tmp/pending_replies`.
            Read `.msg` files, generate a reply, send it with:

            `${wacliPrefix(context)} send text --to <chat-jid> --message <reply>`

            After handling a queue file, move it into `${context.filesDir.absolutePath}/tmp/processed_replies`.
            Never send a WhatsApp message unless the user asked for that exact external action or the configured approval mode permits it.
            """.trimIndent(),
        )
    }

    private fun listenerScript(base: String): String = """
        #!/system/bin/sh
        BASE="__BASE__"
        W="env LD_LIBRARY_PATH=@BASE/usr/glibc/lib WACLI_STORE_DIR=@BASE/wacli-data @BASE/usr/glibc/bin/ld.so @BASE/wacli"
        PID_FILE="@BASE/tmp/listener.pid"
        EVENTS_LOG="@BASE/tmp/native-skill-events.log"
        CONFIG="@BASE/home/.native-skill/settings.json"
        QUEUE_DIR="@BASE/tmp/pending_replies"
        PROCESSED_FILE="@BASE/tmp/dae_processed_ids.txt"
        HEARTBEAT_FILE="@BASE/tmp/listener_heartbeat.txt"

        mkdir -p "@BASE/tmp" "@QUEUE_DIR" "@BASE/tmp/processed_replies"
        echo "@@" > "@PID_FILE"

        load_settings() {
            AUTO_REPLY="true"
            NOTIFY="true"
            DELAY="2"
            CHATS=""
            if [ -f "@CONFIG" ]; then
                AUTO_REPLY=@(grep -o '"auto_reply":[^,}]*' "@CONFIG" | head -1 | cut -d':' -f2 | tr -d ' ')
                NOTIFY=@(grep -o '"notify_on_message":[^,}]*' "@CONFIG" | head -1 | cut -d':' -f2 | tr -d ' ')
                DELAY=@(grep -o '"reply_delay_seconds":[^,}]*' "@CONFIG" | head -1 | cut -d':' -f2 | tr -d ' ')
                CHATS=@(grep -o '"jid":"[^"]*"' "@CONFIG" | cut -d'"' -f4 | tr '\n' ' ')
            fi
            [ -z "@AUTO_REPLY" ] && AUTO_REPLY="true"
            [ -z "@NOTIFY" ] && NOTIFY="true"
            [ -z "@DELAY" ] && DELAY="2"
        }

        notify_agent() {
            SENDER="@1"; TEXT="@2"; CHAT="@3"
            am broadcast -a com.clawdroid.NOTIFY --es title "@SENDER" --es body "@TEXT" --es chat "@CHAT" --ez alert true 2>/dev/null || true
            printf '{"sender":"%s","text":"%s","chat":"%s","time":"%s"}\n' "@SENDER" "@TEXT" "@CHAT" "@(date -Iseconds)" >> "@BASE/tmp/new_msg_alerts.json"
            tail -10 "@BASE/tmp/new_msg_alerts.json" > "@BASE/tmp/new_msg_alerts.tmp" 2>/dev/null && mv "@BASE/tmp/new_msg_alerts.tmp" "@BASE/tmp/new_msg_alerts.json"
        }

        queue_message() {
            SENDER="@1"; TEXT="@2"; CHAT="@3"
            SAFE=@(echo "@SENDER" | tr '/: ' '___' | tr -cd '[:alnum:]_.-')
            MSG_FILE="@QUEUE_DIR/@(date +%s)_@SAFE.msg"
            {
              echo "FROM=@SENDER"
              echo "GROUP=@CHAT"
              echo "TEXT=@TEXT"
              echo "TIME=@(date '+%Y-%m-%d %H:%M:%S')"
            } > "@MSG_FILE"
            echo "[@(date)] Queued from @SENDER in @CHAT: @TEXT" >> "@EVENTS_LOG"
        }

        load_settings
        echo "[@(date)] === WhatsApp Listener Daemon Started (PID: @@) ===" >> "@EVENTS_LOG"
        touch "@PROCESSED_FILE"

        while true; do
            date +%s > "@HEARTBEAT_FILE"
            load_settings
            TARGETS="@CHATS"
            [ -z "@TARGETS" ] && TARGETS="default"
            for CHAT in @TARGETS; do
                if [ "@CHAT" = "default" ]; then
                    @W messages list --limit 5 --json --read-only > "@BASE/tmp/daemon_raw.json" 2>/dev/null
                else
                    @W messages list --chat "@CHAT" --limit 5 --json --read-only > "@BASE/tmp/daemon_raw.json" 2>/dev/null
                fi
                [ ! -s "@BASE/tmp/daemon_raw.json" ] && continue
                awk '{printf "%s", @0}' "@BASE/tmp/daemon_raw.json" | tr '}' '\n' | grep -E "MsgID|id" | while read -r block; do
                    ID=@(echo "@block" | grep -o '"MsgID":"[^"]*"\|"id":"[^"]*"' | head -1 | cut -d'"' -f4)
                    SN=@(echo "@block" | grep -o '"SenderName":"[^"]*"\|"sender":"[^"]*"\|"from":"[^"]*"' | head -1 | cut -d'"' -f4)
                    FM=@(echo "@block" | grep -o '"FromMe":[^,}]*\|"fromMe":[^,}]*' | head -1 | cut -d':' -f2 | tr -d ' ,}]')
                    TX=@(echo "@block" | grep -o '"Text":"[^"]*"\|"text":"[^"]*"\|"body":"[^"]*"' | head -1 | cut -d'"' -f4)
                    CJ=@(echo "@block" | grep -o '"ChatJID":"[^"]*"\|"chat":"[^"]*"' | head -1 | cut -d'"' -f4)
                    [ -z "@ID" ] && continue
                    grep -q "^@ID@" "@PROCESSED_FILE" 2>/dev/null && continue
                    echo "@ID" >> "@PROCESSED_FILE"
                    if [ "@FM" = "false" ] || [ -z "@FM" ]; then
                        [ -n "@SN" ] || SN="@CJ"
                        [ "@NOTIFY" = "true" ] && notify_agent "@SN" "@TX" "@{CJ:-@CHAT}"
                        [ "@AUTO_REPLY" = "true" ] && queue_message "@SN" "@TX" "@{CJ:-@CHAT}"
                    fi
                done
            done
            sleep 3
        done
    """.trimIndent().replace("__BASE__", base).replace('@', '$')

    private fun startupScript(base: String): String = """
        #!/system/bin/sh
        BASE="__BASE__"
        W="env LD_LIBRARY_PATH=@BASE/usr/glibc/lib WACLI_STORE_DIR=@BASE/wacli-data @BASE/usr/glibc/bin/ld.so @BASE/wacli"
        E="@BASE/tmp/native-skill-events.log"
        mkdir -p "@BASE/tmp" "@BASE/wacli-data"
        echo "[@(date)] === Starting WhatsApp Listener Stack ===" >> "@E"
        ${installCommandForScript(base)}
        if ! ps aux | grep -q "[w]acli sync"; then
            nohup @W sync --follow > "@BASE/tmp/wacli-sync.log" 2>&1 &
            echo "[@(date)] wacli sync started (PID: @!)" >> "@E"
            sleep 5
        else
            echo "[@(date)] wacli sync already running" >> "@E"
        fi
        if ! ps aux | grep -q "[l]istener_daemon.sh"; then
            nohup sh "@BASE/home/.native-skill/listener_daemon.sh" > "@BASE/tmp/listener-daemon.log" 2>&1 &
            echo "[@(date)] listener daemon started (PID: @!)" >> "@E"
        else
            echo "[@(date)] listener daemon already running" >> "@E"
        fi
        sleep 2
        echo "[@(date)] === Stack ready ===" >> "@E"
    """.trimIndent().replace("__BASE__", base).replace('@', '$')

    private fun installCommandForScript(base: String): String = """
        if [ ! -f "@BASE/usr/glibc/bin/ld.so" ]; then pkg install -y glibc >/dev/null 2>&1 || true; fi
        if [ ! -f "@BASE/wacli" ]; then
            WACLI_URL=@(curl -s https://api.github.com/repos/itsToggle/wacli/releases/latest | grep "browser_download_url" | grep "linux_arm64" | head -1 | cut -d'"' -f4)
            [ -n "@WACLI_URL" ] && curl -L -o "@BASE/wacli" "@WACLI_URL"
            chmod +x "@BASE/wacli" 2>/dev/null || true
        fi
    """.trimIndent()

    internal fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
