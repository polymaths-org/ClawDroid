package com.clawdroid.app.core.voice

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.clawdroid.app.MainActivity
import com.clawdroid.app.core.assistant.overlay.AssistantOverlayCoordinator
import com.clawdroid.app.core.assistant.overlay.OverlayWindowService
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wake voice has two modes:
 *
 * - system: one-shot handoff opened by Android assistant / notifications / UI.
 * - background: ClawDroid owns the microphone in a foreground service and uses
 *   SpeechRecognizer to detect the configured phrase.
 *
 * Android cannot provide arbitrary third-party wake words without either a
 * privileged hotword service or a microphone listener. The background mode makes
 * that tradeoff explicit and avoids the previous broken "toggle saved, nothing
 * actually listens" behavior.
 */
class WakeVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizerClient? = null
    private var listening = false
    private var wakeTriggered = false
    private var restartJob: Job? = null

    companion object {
        private const val TAG = "WakeVoiceService"
        private const val NOTIFICATION_ID = 44

        fun start(context: Context) {
            val appContext = context.applicationContext
            if (AppConfigManager.wakeDetectionMode == "background") {
                ContextCompat.startForegroundService(appContext, Intent(appContext, WakeVoiceService::class.java))
            } else {
                runCatching {
                    appContext.startService(Intent(appContext, WakeVoiceService::class.java).putExtra("one_shot", true))
                }.onFailure { error ->
                    Log.w(TAG, "Unable to start one-shot voice handoff", error)
                }
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context, WakeVoiceService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (shouldRunBackgroundListener()) {
            startMicForeground()
            recognizer = createWakeRecognizer()
            scheduleListening(0L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("one_shot", false) == true || !shouldRunBackgroundListener()) {
            launchVoiceUi()
            if (!shouldRunBackgroundListener()) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            return START_STICKY
        }

        if (recognizer == null) {
            recognizer = createWakeRecognizer()
        }
        scheduleListening(0L)
        return START_STICKY
    }

    private fun startMicForeground() {
        val notification = NotificationHelper.foregroundNotification(
            this,
            "Wake listener active for \"${configuredWakePhrase()}\"",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createWakeRecognizer(): SpeechRecognizerClient =
        SpeechRecognizerClient(
            context = applicationContext,
            forceSystemRecognizer = true,
            fastMode = false,
            wakeMode = true,
        )

    private fun listenLoop() {
        val client = recognizer ?: return
        if (listening || wakeTriggered) return
        if (!shouldRunBackgroundListener()) {
            stopSelf()
            return
        }
        if (shouldPauseListening()) {
            scheduleListening(1_000L)
            return
        }

        listening = true
        client.startListening(
            onResult = { text ->
                listening = false
                Log.d(TAG, "wake final=${text.take(80)}")
                if (wakeTriggered || shouldPauseListening()) {
                    scheduleListening(1_000L)
                    return@startListening
                }
                if (matchesWakePhrase(text)) {
                    triggerWake()
                } else {
                    scheduleListening(350L)
                }
            },
            onPartialResult = { text ->
                if (!wakeTriggered && matchesWakePhrase(text)) {
                    Log.i(TAG, "Wake phrase detected from partial")
                    triggerWake()
                }
            },
            onError = { error ->
                listening = false
                Log.d(TAG, "wake recognizer ended: $error")
                scheduleListening(if (error.contains("busy", ignoreCase = true)) 1_500L else 450L)
            },
        )
    }

    private fun triggerWake() {
        if (wakeTriggered) return
        wakeTriggered = true
        listening = false
        restartJob?.cancel()
        recognizer?.cancelListening()
        launchVoiceUi()
        scope.launch {
            delay(8_000L)
            wakeTriggered = false
            scheduleListening(1_000L)
        }
    }

    private fun launchVoiceUi() {
        val owner = AppConfigManager.ownerName.takeIf { it.isNotBlank() } ?: "there"
        val greeting = "$owner, I am right here. Tell me how I can help."
        val canDrawOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if (canDrawOverlay) {
            runCatching {
                OverlayWindowService.startVoice(this, greeting = greeting)
            }.onSuccess {
                return
            }.onFailure { error ->
                Log.e(TAG, "Failed to start voice overlay", error)
            }
        }

        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("START_VOICE_SESSION", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to open MainActivity voice session", error)
            NotificationHelper.sendReminderNotification(
                context = this,
                reminderId = "wake_voice_${System.currentTimeMillis()}",
                title = "Wake phrase heard",
                body = "Tap to open ClawDroid voice chat. Overlay permission may be missing.",
                voiceMode = true,
            )
        }
    }

    private fun scheduleListening(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            runCatching { listenLoop() }
        }
    }

    private fun shouldPauseListening(): Boolean {
        return VoiceActivityGate.shouldSuppressRecognition() ||
            AssistantOverlayCoordinator.voiceInputActive.value
    }

    private fun shouldRunBackgroundListener(): Boolean {
        return AppConfigManager.wakeOnVoiceEnabled &&
            AppConfigManager.wakeDetectionMode == "background" &&
            hasMicPermission()
    }

    private fun configuredWakePhrase(): String =
        AppConfigManager.wakePhrase.ifBlank { "Hey ${AppConfigManager.agentName}" }

    private fun matchesWakePhrase(text: String): Boolean {
        val heard = normalize(text)
        if (heard.isBlank()) return false
        val phrases = listOf(
            configuredWakePhrase(),
            "Hey ${AppConfigManager.agentName}",
            AppConfigManager.agentName,
            "Hey ClawDroid",
            "ClawDroid",
            "Claw Droid",
        )
            .map { normalize(it) }
            .filter { it.length >= 4 }
            .distinct()
        val compactHeard = heard.replace(" ", "")
        return phrases.any { phrase ->
            val compactPhrase = phrase.replace(" ", "")
            heard.contains(phrase) ||
                (compactPhrase.length >= 4 && compactHeard.contains(compactPhrase))
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        listening = false
        wakeTriggered = false
        restartJob?.cancel()
        restartJob = null
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
