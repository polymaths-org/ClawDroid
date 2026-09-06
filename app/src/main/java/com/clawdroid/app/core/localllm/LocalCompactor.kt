package com.clawdroid.app.core.localllm

import com.clawdroid.app.core.engine.TokenEstimator
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.db.MessageDao

/**
 * Rule-based compaction for on-device models. No LLM call, so it cannot
 * hallucinate: it drops middle duplicates (repeat warnings, nudges,
 * identical tool errors) and caps length to the per-model harness budget.
 */
object LocalCompactor {
    fun shouldCompact(messages: List<ChatMessage>, modelId: String): Boolean {
        val h = LocalLlmConfig.harnessFor(modelId)
        if (messages.size > h.compactAfterMessages) return true
        return TokenEstimator.estimateMessages(messages) >= (h.nCtx * 0.8).toInt()
    }

    fun planDeletions(
        idsInOrder: List<String>,
        contentsInOrder: List<String>,
        keepLast: Int,
    ): List<String> {
        if (idsInOrder.size <= keepLast + 1) return emptyList()
        val firstId = idsInOrder.firstOrNull() ?: return emptyList()
        val middleEnd = idsInOrder.size - keepLast
        val seen = mutableMapOf<String, Int>()
        val deletions = mutableListOf<String>()
        for (i in 1 until middleEnd) {
            val sig = signature(contentsInOrder[i])
            val count = (seen[sig] ?: 0) + 1
            seen[sig] = count
            // Keep first occurrence of each signature plus up to 2 repeats;
            // drop older pile-up beyond that. Non-repeat content in the middle
            // is dropped too once over budget, since first+last are kept.
            if (count > 3 || !isSticky(contentsInOrder[i])) {
                if (idsInOrder[i] != firstId) deletions.add(idsInOrder[i])
            }
        }
        return deletions
    }

    private fun signature(content: String): String {
        val c = content.trim().take(200)
        return when {
            c.contains("already called this exact tool", ignoreCase = true) -> "loop-warning"
            c.contains("Continue the task", ignoreCase = true) -> "empty-nudge"
            c.contains("No value for path") -> "err-missing-path"
            c.contains("empty_ui_tree") -> "err-empty-ui"
            c.contains("accessibility_service_not_running") -> "err-a11y"
            c.contains("screen_unavailable") -> "err-screen"
            else -> "other:" + c.take(80)
        }
    }

    private fun isSticky(content: String): Boolean {
        // Loop warnings and nudges carry steering value; keep a few recent.
        // Plain assistant chatter ("I'm Nova, ready") in the middle is droppable.
        return content.contains("already called this exact tool", ignoreCase = true) ||
            content.contains("Continue the task", ignoreCase = true)
    }

    suspend fun compact(conversationId: String, messageDao: MessageDao, modelId: String): Int {
        val h = LocalLlmConfig.harnessFor(modelId)
        val all = messageDao.getAll(conversationId)
        if (all.size <= h.compactAfterMessages) return 0
        val keepLast = (h.maxHistory + 6).coerceAtMost(all.size - 1)
        val deletions = planDeletions(all.map { it.id }, all.map { it.content }, keepLast)
        for (id in deletions) runCatching { messageDao.deleteById(id) }
        return deletions.size
    }
}
