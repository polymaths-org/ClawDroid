package com.clawdroid.app.core.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.clawdroid.app.data.db.ClawDroidDatabase

import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.core.engine.BackgroundAgentRunner
import com.clawdroid.app.data.db.ConversationEntity
import com.clawdroid.app.core.service.EnhancedForegroundService
import android.content.Intent
import androidx.core.content.ContextCompat
import com.clawdroid.app.core.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.io.File

class AutomationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Self-heal EnhancedForegroundService if Ultra Agent is enabled
        if (AppConfigManager.ultraAgentEnabled) {
            runCatching {
                val serviceIntent = Intent(applicationContext, EnhancedForegroundService::class.java)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)
            }
        }

        if (AppConfigManager.heartbeatEnabled) {
            runCatching {
                val db = ClawDroidDatabase.get(applicationContext)
                val projects = db.projects().observeProjects().first()
                val homeDir = File(applicationContext.filesDir, "home")

                for (project in projects) {
                    val projectDir = File(homeDir, "projects/${project.id}")
                    val heartbeatFile = File(projectDir, "heartbeat.md").takeIf { it.exists() }
                        ?: File(projectDir, "HEARTBEAT.md").takeIf { it.exists() }

                    if (heartbeatFile != null) {
                        val heartbeatContent = heartbeatFile.readText().trim()
                        if (heartbeatContent.isNotBlank()) {
                            val existingList = db.conversations().observeForProject(project.id).first()
                            val existing = existingList.firstOrNull { it.title == "Autonomous Heartbeat" }
                            val conversationId = if (existing != null) {
                                existing.id
                            } else {
                                val newId = "heartbeat_chat_${project.id}"
                                db.conversations().upsert(
                                    ConversationEntity(
                                        id = newId,
                                        projectId = project.id,
                                        title = "Autonomous Heartbeat",
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        status = "idle",
                                        costUsd = 0.0
                                    )
                                )
                                newId
                            }

                            val prompt = "Autonomous Heartbeat Check. Scan your heartbeat.md tasks and execute necessary checks:\n\n$heartbeatContent\n\nIf you perform actions, use send_notification to inform the user."
                            BackgroundAgentRunner.runAgentInBackground(
                                context = applicationContext,
                                projectId = project.id,
                                conversationId = conversationId,
                                prompt = prompt
                            )
                        }
                    }
                }
            }
        }

        if (AppConfigManager.proactiveAssistantEnabled) {
            runCatching {
                val now = System.currentTimeMillis()
                val minGapMs = 6 * 60 * 60 * 1000L
                if (now - AppConfigManager.proactiveLastPromptAt > minGapMs) {
                    AppConfigManager.proactiveLastPromptAt = now
                    val title = when (AppConfigManager.proactiveAssistantMode) {
                        "email_triage" -> "Email triage"
                        "work_prompt" -> "Need anything handled?"
                        else -> "Daily brief"
                    }
                    val body = when (AppConfigManager.proactiveAssistantMode) {
                        "email_triage" -> "Want me to check recent mail and ask before drafting replies?"
                        "work_prompt" -> "Tell me what to work on, or tap to start voice chat."
                        else -> "Want a quick plan from your calendar, reminders, and messages?"
                    }
                    if (AppConfigManager.proactiveDeliveryMode != "silent") {
                        NotificationHelper.sendReminderNotification(
                            context = applicationContext,
                            reminderId = "proactive_$now",
                            title = title,
                            body = body,
                            voiceMode = AppConfigManager.proactiveDeliveryMode == "voice",
                        )
                    }
                }
            }
        }

        return Result.success()
    }
}
