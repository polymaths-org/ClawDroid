package com.clawdroid.app.core.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.reminders.ReminderManager
import com.clawdroid.app.core.voice.WakeVoiceService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutomationScheduler.schedule(context)
            ReminderManager.rescheduleAll(context)
            if (
                AppConfigManager.wakeOnVoiceEnabled &&
                AppConfigManager.wakeDetectionMode == "background" &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                WakeVoiceService.start(context)
            }
        }
    }
}
