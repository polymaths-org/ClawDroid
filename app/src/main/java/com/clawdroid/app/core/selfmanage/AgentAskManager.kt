package com.clawdroid.app.core.selfmanage

import android.content.Context
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.data.db.AgentQuestionEntity
import com.clawdroid.app.data.db.ClawDroidDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class AgentQuestion(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val context: String,
    val suggestedActions: List<String> = emptyList(),
    val priority: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val answered: Boolean = false,
    val answer: String? = null,
)

class AgentAskManager(private val context: Context) {
    private val dao = ClawDroidDatabase.get(context.applicationContext).agentQuestions()

    fun observePending(): Flow<List<AgentQuestionEntity>> = dao.observePending(System.currentTimeMillis())

    suspend fun ask(question: AgentQuestion): AgentQuestion {
        val now = System.currentTimeMillis()
        val existing = dao.getById(question.id)
        if (existing != null && !existing.answered && (existing.expiresAt == null || existing.expiresAt > now)) {
            return question
        }
        dao.upsert(question.toEntity())
        NotificationHelper.sendAgentQuestion(
            context = context,
            questionId = question.id,
            question = question.question,
            triggerAction = "answer_question:${question.id}",
        )
        return question
    }

    suspend fun answer(questionId: String, answer: String) {
        dao.answer(questionId, answer)
        NotificationHelper.sendAgentNotification(
            context = context,
            title = "Question answered",
            body = answer,
            triggerAction = "question_answered:$questionId",
        )
    }
}

private fun AgentQuestion.toEntity(): AgentQuestionEntity = AgentQuestionEntity(
    id = id,
    question = question,
    context = context,
    suggestedActionsCsv = suggestedActions.joinToString(","),
    priority = priority.coerceIn(1, 10),
    createdAt = createdAt,
    expiresAt = expiresAt,
    answered = answered,
    answer = answer,
)
