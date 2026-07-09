package com.clawdroid.app.core.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.reminders.ReminderManager
import com.clawdroid.app.core.selfmanage.SelfManageRepository
import com.clawdroid.app.core.voice.WakeVoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AutomationScheduler.schedule(context)
            ReminderManager.rescheduleAll(context)
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    SelfManageRepository(context.applicationContext).rescheduleActive()
                } finally {
                    pending.finish()
                }
            }
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
