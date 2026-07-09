package com.clawdroid.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.clawdroid.app.MainActivity
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.assistant.overlay.OverlayWindowService
import com.clawdroid.app.core.config.AppConfigManager

class TaskAskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(NotificationHelper.TASK_ASK_NOTIFICATION_ID)
        val summary = intent.getStringExtra(NotificationHelper.EXTRA_TASK_SUMMARY).orEmpty()
        val basePrompt = intent.getStringExtra(NotificationHelper.EXTRA_TASK_PROMPT)
            ?: "This task looks done. Should I mark it complete?"

        when (intent.action) {
            NotificationHelper.ACTION_TASK_MARK_DONE -> {
                NotificationHelper.sendAgentNotification(
                    context = context,
                    title = "Marked complete",
                    body = summary.ifBlank { "ClawDroid marked the task complete." },
                )
            }

            NotificationHelper.ACTION_TASK_NEEDS_WORK -> {
                openVoiceAsk(
                    context = context,
                    prompt = "$basePrompt Tell me what is still left.",
                )
            }

            NotificationHelper.ACTION_TASK_VOICE_REPLY -> {
                openVoiceAsk(context = context, prompt = basePrompt)
            }
        }
    }

    private fun openVoiceAsk(context: Context, prompt: String) {
        val appContext = context.applicationContext
        val canDrawOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)
        if (canDrawOverlay) {
            runCatching {
                OverlayWindowService.startVoice(
                    context = appContext,
                    greeting = prompt,
                    listenTimeoutSeconds = AppConfigManager.notificationAskListenSeconds,
                    source = AssistantInvocationSource.NOTIFICATION_ACTION,
                )
            }.onSuccess {
                return
            }
        }

        appContext.startActivity(
            Intent(appContext, MainActivity::class.java)
                .putExtra("START_VOICE_SESSION", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }
}
