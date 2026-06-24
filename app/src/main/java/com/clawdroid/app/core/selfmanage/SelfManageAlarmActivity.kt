package com.clawdroid.app.core.selfmanage

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SelfManageAlarmActivity : Activity() {
    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_TITLE = "alarm_title"
        const val EXTRA_ALARM_BODY = "alarm_body"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_ALARM_TITLE).orEmpty().ifBlank { "Alarm" }
        val body = intent.getStringExtra(EXTRA_ALARM_BODY).orEmpty().ifBlank { "Alarm due now" }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        setContentView(alarmView(title, body, alarmId, notificationId))
        startAlarmOutput()
    }

    override fun onDestroy() {
        stopAlarmOutput()
        super.onDestroy()
    }

    private fun alarmView(title: String, body: String, alarmId: String, notificationId: Int): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 48, 40, 48)
            setBackgroundColor(Color.rgb(8, 10, 16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 34f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = body
            setTextColor(Color.rgb(205, 213, 225))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 42)
        })
        root.addView(Button(this).apply {
            text = "Dismiss"
            textSize = 18f
            setOnClickListener {
                cancelNotification(notificationId)
                finish()
            }
        })
        root.addView(Button(this).apply {
            text = "Snooze 5 min"
            textSize = 16f
            setOnClickListener {
                scheduleSnooze(alarmId, title)
                cancelNotification(notificationId)
                finish()
            }
        })
        return root
    }

    private fun startAlarmOutput() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 400, 800, 400, 1200)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarmOutput() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
    }

    private fun scheduleSnooze(alarmId: String, title: String) {
        val triggerAt = System.currentTimeMillis() + 5 * 60_000L
        val intent = Intent(this, SelfManageAlarmReceiver::class.java)
            .setAction(SelfManageScheduler.ACTION_TRIGGER)
            .putExtra(SelfManageScheduler.EXTRA_ID, alarmId.ifBlank { "snooze_${System.currentTimeMillis()}" })
            .putExtra(SelfManageScheduler.EXTRA_KIND, "alarm")
            .putExtra(SelfManageScheduler.EXTRA_TITLE, title)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            (alarmId.ifBlank { title }).hashCode() + 5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, pendingIntent),
            pendingIntent,
        )
    }

    private fun cancelNotification(notificationId: Int) {
        if (notificationId != 0) {
            getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
    }
}
