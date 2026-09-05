package com.clawdroid.app.core.heartbeat

/**
 * A periodic task that runs on a schedule (cron-like).
 * The agent executes the prompt and optionally sends results to a channel.
 */
interface HeartbeatTask {
    val id: String
    val cronExpression: String
    val prompt: String
    val channelTarget: String?
}

data class SimpleHeartbeatTask(
    override val id: String,
    override val cronExpression: String,
    override val prompt: String,
    override val channelTarget: String? = null,
) : HeartbeatTask
