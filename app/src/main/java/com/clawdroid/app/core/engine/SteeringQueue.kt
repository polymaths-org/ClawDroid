package com.clawdroid.app.core.engine

import java.util.concurrent.ConcurrentLinkedQueue

class SteeringQueue {
    private val messages = ConcurrentLinkedQueue<String>()

    fun offer(message: String) {
        val trimmed = message.trim()
        if (trimmed.isNotEmpty()) {
            messages.offer(trimmed)
        }
    }

    fun drain(): List<String> {
        val drained = mutableListOf<String>()
        while (true) {
            drained += messages.poll() ?: break
        }
        return drained
    }
}
