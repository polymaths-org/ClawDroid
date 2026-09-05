package com.clawdroid.app.core.channels

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.BackgroundAgentRunner
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import kotlinx.coroutines.*

class ClawNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "ClawNotification"
        private const val APPROVAL_CHANNEL = "whatsapp_approval"
        private var pendingApprovals = mutableMapOf<String, PendingApproval>()
        private var replyActionCache: Notification.Action? = null
        private var pendingSender = ""
        private var pendingText = ""

        data class PendingApproval(
            val sender: String,
            val message: String,
            val draft: String,
            val replyAction: Notification.Action,
            val timestamp: Long
        )

        fun getPendingApproval(key: String): PendingApproval? = pendingApprovals[key]
        fun removePendingApproval(key: String) {
            pendingApprovals.remove(key)
            replyActionCache = null
        }

        fun doSendReply(context: Context, action: Notification.Action, responseText: String) {
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in action.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, responseText)
            }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            try {
                action.actionIntent.send(context, 0, intent)
                Log.d(TAG, "Sent WhatsApp reply: $responseText")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createApprovalChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (!AppConfigManager.whatsappEnabled) return

        val packageName = sbn.packageName
        if (packageName != "com.whatsapp") return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() || text.isBlank()) return

        if (title.lowercase().contains("whatsapp") || title.lowercase().contains("you") || title.lowercase().contains("transferring")) return

        val allowedContacts = AppConfigManager.whatsappAllowedContacts
        if (allowedContacts.isNotBlank()) {
            val list = allowedContacts.split(",").map { it.trim().lowercase() }
            if (list.none { it == title.lowercase() || title.lowercase().contains(it) }) {
                Log.d(TAG, "Message from '$title' ignored by contact filter")
                return
            }
        }

        Log.d(TAG, "Intercepted WhatsApp message from $title: $text")

        val actions = notification.actions ?: return
        var replyAction: Notification.Action? = null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            if (remoteInputs.isNotEmpty()) {
                replyAction = action
                break
            }
        }

        if (replyAction == null) {
            Log.d(TAG, "No RemoteInput reply action found")
            return
        }

        // Cache for later use when user approves
        replyActionCache = replyAction
        pendingSender = title
        pendingText = text

        val prompt = "You received a WhatsApp message from $title: \"$text\". Draft a response."
        val projectId = AppConfigManager.activeProjectId

        serviceScope.launch {
            try {
                val db = ClawDroidDatabase.get(applicationContext)

                val existing = db.conversations().getById("whatsapp_chat_$title")
                val conversationId = if (existing != null) {
                    existing.id
                } else {
                    val newId = "whatsapp_chat_$title"
                    db.conversations().upsert(
                        ConversationEntity(
                            id = newId,
                            projectId = projectId,
                            title = "WhatsApp Chat: $title",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            status = "idle",
                            costUsd = 0.0
                        )
                    )
                    newId
                }

                val draftedReply = BackgroundAgentRunner.runAgentInBackground(
                    context = applicationContext,
                    projectId = projectId,
                    conversationId = conversationId,
                    prompt = prompt
                )

                if (draftedReply.isNotBlank()) {
                    val key = "wa_${title}_${System.currentTimeMillis()}"
                    pendingApprovals[key] = PendingApproval(
                        sender = title,
                        message = text,
                        draft = draftedReply,
                        replyAction = replyAction!!,
                        timestamp = System.currentTimeMillis()
                    )
                    showApprovalNotification(draftedReply, title, key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drafting response", e)
            }
        }
    }

    private fun showApprovalNotification(draft: String, sender: String, approvalKey: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val approveIntent = Intent(this, ApproveReplyReceiver::class.java).apply {
            putExtra("approval_key", approvalKey)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            this, approvalKey.hashCode(), approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, APPROVAL_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reply to $sender?")
            .setContentText(draft)
            .setStyle(NotificationCompat.BigTextStyle().bigText(draft))
            .addAction(android.R.drawable.ic_menu_send, "Send Reply", approvePendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify("whatsapp_approval_$approvalKey", 0, notification)
    }

    private fun createApprovalChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                APPROVAL_CHANNEL,
                "WhatsApp Approval",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ask for approval before sending WhatsApp replies"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    class ApproveReplyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val key = intent.getStringExtra("approval_key") ?: return
            val approval = getPendingApproval(key) ?: return
            removePendingApproval(key)
            doSendReply(context, approval.replyAction, approval.draft)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
