package com.clawdroid.app.core.agent

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.channels.ChannelManager
import com.clawdroid.app.core.channels.ChannelMessage
import com.clawdroid.app.core.engine.AgentEngine
import com.clawdroid.app.core.engine.AgentRunEvent
import com.clawdroid.app.core.heartbeat.HeartbeatManager
import com.clawdroid.app.core.notifications.NotificationHelper
import com.clawdroid.app.core.skills.SkillManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background agent that runs 24/7, processes channel messages,
 * executes heartbeat tasks, and dispatches results.
 *
 * This is the core of the "always-on" agent experience.
 */
class BackgroundAgent(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channelManager = ChannelManager(context)
    private var processingJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    companion object {
        private const val TAG = "BackgroundAgent"
    }

    /**
     * Start background agent: connect channels, schedule heartbeats,
     * and start processing incoming messages.
     */
    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        _status.value = "Starting…"

        scope.launch {
            // Load config and load skills
            val config = AgentConfigLoader.load(context)
            SkillManager.loadConfigured(context)
            Log.i(TAG, "Skills loaded: ${SkillManager.getAll().map { it.name }}")

            // Schedule heartbeat tasks
            HeartbeatManager.scheduleAll(context)
            Log.i(TAG, "Heartbeats scheduled")

            // Connect channels
            _status.value = "Connecting channels…"
            val results = channelManager.connectAll()
            val connected = results.filter { it.value.isSuccess }.keys
            val failed = results.filter { it.value.isFailure }.keys
            if (failed.isNotEmpty()) {
                Log.w(TAG, "Channel connection failures: $failed")
            }
            _status.value = if (connected.isNotEmpty()) "Connected: ${connected.joinToString(", ")}" else "No channels connected"

            // Listen for incoming messages from channels
            launch {
                channelManager.incomingMessages.collect { messages ->
                    messages.forEach { msg ->
                        processChannelMessage(msg)
                    }
                }
            }

            NotificationHelper.sendAgentNotification(
                context,
                "ClawDroid Agent Running",
                "Channels: ${connected.joinToString(", ").ifEmpty { "none" }}",
            )
        }
    }

    /**
     * Stop the background agent: disconnect channels, cancel heartbeats.
     */
    fun stop() {
        _isRunning.value = false
        _status.value = "Stopped"
        processingJob?.cancel()
        channelManager.disconnectAll()
        HeartbeatManager.cancelAll(context)
        NotificationHelper.sendAgentNotification(context, "ClawDroid", "Background agent stopped")
    }

    private suspend fun processChannelMessage(msg: ChannelMessage) {
        if (processingJob?.isActive == true) {
            Log.i(TAG, "Already processing, queueing message from ${msg.sender}")
            return
        }

        _status.value = "Processing message from ${msg.sender} via ${msg.channelType}"
        NotificationHelper.sendAgentNotification(
            context,
            "💬 ${msg.channelType}: ${msg.sender}",
            msg.text.take(200),
        )

        processingJob = scope.launch {
            val engine = AgentEngine(context)
            var responseText = ""

            engine.run(msg.text).collect { event ->
                when (event) {
                    is AgentRunEvent.TextDelta -> responseText += event.text
                    is AgentRunEvent.Completed -> {
                        responseText = event.finalText
                        // Don't auto-send — show draft to user for approval
                        NotificationHelper.sendAgentNotification(
                            context,
                            "📝 Draft reply for ${msg.sender} (${msg.channelType})",
                            "Agent suggests: ${responseText.take(300)}\nOpen app to approve and send.",
                        )
                        _status.value = "Idle"
                    }
                    is AgentRunEvent.Stopped -> {
                        _status.value = "Idle"
                    }
                    else -> {}
                }
            }
        }
    }
}
