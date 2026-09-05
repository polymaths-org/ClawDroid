package com.clawdroid.app.core.engine

import android.content.Context
import android.util.Log
import com.clawdroid.app.core.bootstrap.BootstrapManager
import com.clawdroid.app.core.memory.MemoryManager
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.permissions.PermissionAwareToolExecutor
import com.clawdroid.app.data.api.ChatMessage
import com.clawdroid.app.data.api.ContextBuilder
import com.clawdroid.app.data.api.CompletedToolCall
import com.clawdroid.app.data.api.LlmApiClient
import com.clawdroid.app.data.api.MessageBuilder
import com.clawdroid.app.data.api.StreamEvent
import com.clawdroid.app.data.api.TokenUsage
import com.clawdroid.app.data.api.ToolSchemaRegistry
import com.clawdroid.app.data.db.ClawDroidDatabase
import com.clawdroid.app.data.db.ConversationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.channelFlow
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AgentRunEvent {
    data class TextDelta(val text: String) : AgentRunEvent
    data class ToolCallRequested(val call: CompletedToolCall) : AgentRunEvent
    data class ToolCallStreaming(val callId: String, val name: String, val arguments: String) : AgentRunEvent
    data class ToolOutputUpdated(val callId: String, val output: String) : AgentRunEvent
    data class ToolResultReceived(val result: ToolExecutionResult) : AgentRunEvent
    data class SteeringApplied(val message: String) : AgentRunEvent
    data class LoopWarning(val message: String) : AgentRunEvent
    data class Completed(val finalText: String) : AgentRunEvent
    data class Stopped(val reason: String) : AgentRunEvent
    data class RunError(val message: String) : AgentRunEvent
}

class AgentEngine(
    private val context: Context,
    private val projectId: String? = null,
    private val client: LlmApiClient = LlmApiClient(),
    private val steeringQueue: SteeringQueue = SteeringQueue(),
    private val toolExecutor: ToolExecutor = ToolExecutor,
    private val loopDetector: LoopDetector = LoopDetector(),
    private val memoryManager: MemoryManager = MemoryManager(context),
) {
    private val stopRequested = AtomicBoolean(false)

    init {
        // Load persistent memory into the message builder on engine creation
        val memory = memoryManager.readMemory()
        MessageBuilder.setMemoryContext(memory)
    }

    fun steer(message: String) {
        steeringQueue.offer(message)
    }

    fun stop() {
        stopRequested.set(true)
    }

    fun runInvocation(
        invocation: AssistantInvocation,
        maxTurns: Int = 200
    ): Flow<AgentRunEvent> {
        Log.i(
            "AgentEngine",
            "runInvocation build start id=${invocation.id} mode=${invocation.mode} source=${invocation.source} " +
                "userTextLen=${invocation.userText?.length ?: 0} package=${invocation.contextSnapshot?.sourcePackage} " +
                "screenshot=${invocation.contextSnapshot?.screenshotPath ?: invocation.mediaPath}"
        )
        val prompt = buildString {
            val snapshot = invocation.contextSnapshot
            appendLine("Assistant invocation context:")
            appendLine("- This request came from the user's current active Android screen at the moment ClawDroid was invoked.")
            appendLine("- If an image is attached, it is the current screen. Treat it as live device context, not as a random image upload.")
            appendLine("- For speed, screenshots are attached only when visual details are likely needed. Otherwise use get_screen first.")
            appendLine("- If the user asks to do something in the current app, start from the current screen. Do not open the app again unless the screen has changed or you need a different app.")
            appendLine("- If the user asks to send a specific message in the current Telegram/WhatsApp/chat screen, call send_message_in_current_chat immediately. Do not call get_screen or screenshot first.")
            appendLine("- Use Android screen-control tools when useful. For app actions, call get_screen first, then prefer perform_android_actions to batch multiple taps/typing/waits in one tool call.")
            appendLine("- Do not verify after every tiny action. Verify after important state changes, after failures, and at task completion.")
            appendLine("- The ClawDroid overlay is temporarily hidden automatically before get_screen, screenshot, taps, swipes, and typing so your vision/actions target the real app underneath.")
            appendLine("- For screenshots and get_screen, the overlay is hidden for about 3 seconds before capture. Do not compensate for the overlay in coordinates.")
            if (snapshot != null) {
                appendLine("- Source package: ${snapshot.sourcePackage ?: "unknown"}")
                appendLine("- Source activity: ${snapshot.sourceActivity ?: "unknown"}")
                appendLine("- Capture method: ${snapshot.captureMethod}")
                appendLine("- Focused field/text: ${snapshot.focusedText?.take(160) ?: "none detected"}")
                appendLine("- Web URI: ${snapshot.webUri ?: "none detected"}")
                appendLine("- Visible text available: ${snapshot.visibleText.isNotBlank()}")
                appendLine("- Content descriptions available: ${snapshot.contentDescriptionText.isNotBlank()}")
            }
            appendLine()
            if (!invocation.userText.isNullOrBlank()) {
                append(invocation.userText)
            } else {
                append("Wait for the user's instruction about this screen.")
            }
        }

        val conversationId = invocation.conversationId ?: "assistant_${invocation.id}"
        val shouldAttachImage = invocation.shouldAttachImageForFirstTurn()
        val mediaPath = if (shouldAttachImage) {
            invocation.contextSnapshot?.selectedRegionPath
                ?: invocation.contextSnapshot?.screenshotPath
                ?: invocation.mediaPath
        } else {
            null
        }
        val mediaMimeType = if (mediaPath != null) {
            invocation.mediaMimeType ?: "image/jpeg"
        } else {
            null
        }
        Log.i(
            "AgentEngine",
            "runInvocation built id=${invocation.id} conversationId=$conversationId promptLen=${prompt.length} " +
                "mediaPath=$mediaPath mediaMimeType=$mediaMimeType attachImage=$shouldAttachImage promptPreview=${prompt.take(220).replace('\n', ' ')}"
        )

        return runInternal(
            prompt = prompt,
            targetConversationId = conversationId,
            maxTurns = maxTurns,
            mediaPath = mediaPath,
            mediaMimeType = mediaMimeType,
            isAssistantMode = true,
        )
    }

    fun run(
        prompt: String,
        targetConversationId: String? = null,
        maxTurns: Int = 200,
        mediaPath: String? = null,
        mediaMimeType: String? = null,
        toolsOverride: JSONArray? = null,
    ): Flow<AgentRunEvent> = runInternal(prompt, targetConversationId, maxTurns, mediaPath, mediaMimeType, false, toolsOverride)

    private fun runInternal(
        prompt: String,
        targetConversationId: String?,
        maxTurns: Int,
        mediaPath: String?,
        mediaMimeType: String?,
        isAssistantMode: Boolean,
        toolsOverride: JSONArray? = null,
    ): Flow<AgentRunEvent> = channelFlow {
        stopRequested.set(false)
        Log.i("AgentEngine", "runInternal started assistant=$isAssistantMode promptLen=${prompt.length} targetConversationId=$targetConversationId mediaPath=$mediaPath mediaMimeType=$mediaMimeType")
        val result = BootstrapManager.ensureBootstrapped(context) { }
        Log.i("AgentEngine", "ensureBootstrapped completed. Result: $result")
        var mcpStarted = false
        if (!isAssistantMode) {
            McpServerLauncher.startAll(context)
            mcpStarted = true
            Log.i("AgentEngine", "MCP startAll completed assistant=$isAssistantMode")
        } else {
            Log.i("AgentEngine", "MCP startAll skipped for fast assistant mode")
        }

        try {
            val db = ClawDroidDatabase.get(context)
        val conversationDao = db.conversations()
        val messageDao = db.messages()
        val toolCallDao = db.toolCalls()

        // 1. Fetch or create active conversation associated with this project/id
        var conversation = if (targetConversationId != null) {
            conversationDao.getById(targetConversationId)
        } else {
            conversationDao.getMostRecent()
        }

        if (conversation == null || (targetConversationId == null && conversation.projectId != projectId)) {
            val newId = targetConversationId ?: UUID.randomUUID().toString()
            conversation = ConversationEntity(
                id = newId,
                projectId = projectId,
                title = if (targetConversationId != null && targetConversationId.startsWith("whatsapp_chat_")) "WhatsApp Chat" else if (targetConversationId != null && targetConversationId.startsWith("sms_chat_")) "SMS Chat" else "New Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = "active",
                costUsd = 0.0,
            )
            conversationDao.upsert(conversation)
        }
        val conversationId = conversation.id
        Log.i("AgentEngine", "conversation ready id=$conversationId projectId=${conversation.projectId} status=${conversation.status}")

        val contextBuilder = ContextBuilder(context, projectId, conversationDao, messageDao, toolCallDao)
        val compactionManager = CompactionManager(conversationDao, messageDao, client)
        val costTracker = CostTracker()
        var emptyScreenCount = 0
        var screenCaptureBlocked = false

        // 2. Save current user prompt to DB if not already present as the last message
        val existingMessages = messageDao.getAll(conversationId)
        val lastMsg = existingMessages.lastOrNull()
        if (lastMsg == null || lastMsg.role != "user" || lastMsg.content != prompt) {
            contextBuilder.saveUserMessage(conversationId, prompt, mediaPath, mediaMimeType)
        } else if (lastMsg.mediaPath != mediaPath || lastMsg.mediaMimeType != mediaMimeType) {
            messageDao.update(lastMsg.copy(mediaPath = mediaPath, mediaMimeType = mediaMimeType))
        }

        val finalText = StringBuilder()

        repeat(maxTurns) {
            if (stopRequested.get()) {
                send(AgentRunEvent.Stopped("Stop requested"))
                saveSummary(finalText.toString())
                return@channelFlow
            }

            // 3. Build current conversation context from DB
            var messages = contextBuilder.buildContext(conversationId)
            Log.i("AgentEngine", "turn buildContext conversationId=$conversationId messages=${messages.size} assistant=$isAssistantMode")

            // 4. Handle steering messages before calling LLM
            val steering = steeringQueue.drain()
            if (steering.isNotEmpty()) {
                for (msg in steering) {
                    contextBuilder.saveUserMessage(conversationId, msg)
                    send(AgentRunEvent.SteeringApplied(msg))
                }
                messages = contextBuilder.buildContext(conversationId)
            }

            val turnText = StringBuilder()
            val toolCalls = mutableListOf<CompletedToolCall>()
            var tokenUsage: TokenUsage? = null
            var streamError: String? = null

            // 5. Query the LLM
            val toolsArray = toolsOverride ?: if (isAssistantMode) {
                ToolSchemaRegistry.assistantTools()
            } else {
                ToolSchemaRegistry.allTools().also { tools ->
                    McpServerLauncher.getMcpTools().forEach { tools.put(it) }
                }
            }
            Log.i("AgentEngine", "streamChat start conversationId=$conversationId messages=${messages.size} tools=${toolsArray.length()} assistant=$isAssistantMode")

            try {
                client.streamChat(
                    messages = messages,
                    tools = toolsArray,
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            turnText.append(event.text)
                            finalText.append(event.text)
                            send(AgentRunEvent.TextDelta(event.text))
                        }
                        is StreamEvent.ToolCallDeltaReceived -> {
                            if (event.id.isNotEmpty() && event.name.isNotEmpty()) {
                                send(AgentRunEvent.ToolCallStreaming(
                                    callId = event.id,
                                    name = event.name,
                                    arguments = event.arguments
                                ))
                            }
                        }
                        is StreamEvent.ToolCallComplete -> {
                            Log.i("AgentEngine", "toolCall complete conversationId=$conversationId name=${event.call.name} id=${event.call.id} argsLen=${event.call.arguments.length}")
                            toolCalls += event.call
                        }
                        is StreamEvent.Usage -> {
                            Log.i("AgentEngine", "usage conversationId=$conversationId prompt=${event.usage.promptTokens} completion=${event.usage.completionTokens} cached=${event.usage.cachedTokens}")
                            tokenUsage = event.usage
                        }
                        is StreamEvent.Error -> {
                            streamError = event.message.toUserFacingStreamError(mediaPath = mediaPath)
                            Log.e("AgentEngine", "stream error conversationId=$conversationId message=${event.message}")
                        }
                        StreamEvent.Done -> Log.i("AgentEngine", "stream done conversationId=$conversationId turnTextLen=${turnText.length} toolCalls=${toolCalls.size}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                streamError = (t.message ?: t::class.java.simpleName).toUserFacingStreamError(mediaPath = mediaPath)
                Log.e("AgentEngine", "stream exception conversationId=$conversationId", t)
            }

            if (streamError != null) {
                val message = streamError ?: "The assistant run failed."
                send(AgentRunEvent.RunError(message))
                saveSummary(finalText.toString())
                return@channelFlow
            }

            // 6. Save assistant message and track usage/costs
            contextBuilder.saveAssistantMessage(conversationId, turnText.toString(), toolCalls)

            val usage = tokenUsage ?: TokenUsage(
                promptTokens = TokenEstimator.estimateMessages(messages),
                completionTokens = TokenEstimator.estimate(turnText.toString())
            )
            costTracker.record(usage.promptTokens, usage.completionTokens, usage.cachedTokens)

            // Persist cost update in database
            val costDelta = (usage.promptTokens / 1_000_000.0 * 0.15) +
                    (usage.completionTokens / 1_000_000.0 * 0.60) +
                    (usage.cachedTokens / 1_000_000.0 * 0.03)

            conversationDao.recordUsage(
                id = conversationId,
                lastPromptTokens = usage.promptTokens,
                promptTokens = usage.promptTokens.toLong(),
                completionTokens = usage.completionTokens.toLong(),
                cachedTokens = usage.cachedTokens.toLong(),
                costDelta = costDelta
            )

            // 7. Check if compaction is needed
            val postTurnMessages = contextBuilder.buildContext(conversationId)
            val decision = compactionManager.shouldCompact(postTurnMessages)
            if (decision.shouldCompact) {
                compactionManager.compact(conversationId)
            }

            // 8. Exit loop if no tool calls were generated
            if (toolCalls.isEmpty()) {
                val finalAnswer = finalText.toString().trim()
                Log.i("AgentEngine", "completed no tools conversationId=$conversationId finalLen=${finalAnswer.length}")
                send(AgentRunEvent.Completed(finalAnswer))
                saveSummary(finalAnswer)
                return@channelFlow
            }

            // 9. Execute tools
            for (call in toolCalls) {
                Log.i("AgentEngine", "execute tool start conversationId=$conversationId name=${call.name} id=${call.id} assistant=$isAssistantMode")
                send(AgentRunEvent.ToolCallRequested(call))
                when (val loopCheck = loopDetector.record(call)) {
                    LoopCheckResult.Ok -> Unit
                    is LoopCheckResult.Warn -> send(AgentRunEvent.LoopWarning(loopCheck.message))
                    is LoopCheckResult.Stop -> {
                        send(AgentRunEvent.Stopped(loopCheck.message))
                        saveSummary(finalText.toString())
                        return@channelFlow
                    }
                }

                if (stopRequested.get()) {
                    send(AgentRunEvent.Stopped("Stop requested"))
                    saveSummary(finalText.toString())
                    return@channelFlow
                }

                val result = if (isAssistantMode) {
                    PermissionAwareToolExecutor.execute(context, call) { progress ->
                        send(AgentRunEvent.ToolOutputUpdated(call.id, progress))
                    }
                } else {
                    toolExecutor.execute(context, call) { progress ->
                        send(AgentRunEvent.ToolOutputUpdated(call.id, progress))
                    }
                }
                Log.i("AgentEngine", "execute tool done conversationId=$conversationId name=${call.name} id=${call.id} isError=${result.isError} contentLen=${result.content.length}")
                send(AgentRunEvent.ToolResultReceived(result))

                // Save tool result to DB
                contextBuilder.saveToolResultMessage(
                    conversationId = conversationId,
                    toolCallId = result.callId,
                    content = result.content,
                    isError = result.isError
                )

                val blocker = classifyAssistantBlocker(call.name, result.content)
                if (blocker == AssistantBlocker.EmptyScreen) {
                    emptyScreenCount += 1
                } else if (call.name == "get_screen" && !result.isError) {
                    emptyScreenCount = 0
                }
                if (blocker == AssistantBlocker.ScreenCapturePermission) {
                    screenCaptureBlocked = true
                }
                if (isAssistantMode && emptyScreenCount >= 3 && screenCaptureBlocked) {
                    val message = "I could not read the current app UI tree, and screenshot permission is off. Enable screen capture or reopen the app screen, then try again."
                    Log.w("AgentEngine", "assistant blocked conversationId=$conversationId emptyScreenCount=$emptyScreenCount screenCaptureBlocked=$screenCaptureBlocked")
                    send(AgentRunEvent.RunError(message))
                    saveSummary(finalText.toString())
                    return@channelFlow
                }
            }

            // 10. Process steering messages received during tool runs
            val postToolSteering = steeringQueue.drain()
            if (postToolSteering.isNotEmpty()) {
                for (msg in postToolSteering) {
                    contextBuilder.saveUserMessage(conversationId, msg)
                    send(AgentRunEvent.SteeringApplied(msg))
                }
            }
        }

        val final = finalText.toString().trim()
        send(AgentRunEvent.Stopped("Reached max agent turns ($maxTurns)"))
        saveSummary(final)
        } finally {
            Log.i("AgentEngine", "runInternal finally stopping MCP assistant=$isAssistantMode targetConversationId=$targetConversationId")
            if (mcpStarted) {
                McpServerLauncher.stopAll()
            }
        }
    }

    private fun saveSummary(text: String) {
        if (text.isBlank()) return
        val preview = text.take(500).replace("\n", " ").trim()
        val summary = "Completed task. Summary: $preview"
        memoryManager.appendSessionSummary(summary)
        // Reload memory context for next run
        MessageBuilder.setMemoryContext(memoryManager.readMemory())
    }

    private fun String.toUserFacingStreamError(mediaPath: String?): String {
        val lower = lowercase()
        if (mediaPath != null && (lower.contains("image_url") || lower.contains("vision") || lower.contains("image input"))) {
            return "This model rejected the screenshot image. Pick a vision-capable model, or retry with screen-control context only."
        }
        if (lower.contains("401") || lower.contains("api key")) {
            return "The model provider rejected the API key. Check Settings and try again."
        }
        if (lower.contains("429") || lower.contains("rate limit")) {
            return "The model provider is rate limiting requests. Wait a moment and try again."
        }
        return "Assistant run failed: $this"
    }

    private fun AssistantInvocation.shouldAttachImageForFirstTurn(): Boolean {
        if (contextSnapshot?.selectedRegionPath != null) return true
        if (mode == com.clawdroid.app.core.assistant.AssistantMode.DOODLE_SEARCH) return true
        if (mode == com.clawdroid.app.core.assistant.AssistantMode.VOICE_CHAT) return false
        val text = userText.orEmpty().lowercase()
        if (text.isBlank()) return false
        val visualKeywords = listOf(
            "screenshot", "image", "picture", "photo", "see", "look at", "describe",
            "what is this", "what's this", "on my screen", "visual", "doodle"
        )
        return visualKeywords.any { it in text }
    }

    private enum class AssistantBlocker {
        EmptyScreen,
        ScreenCapturePermission,
    }

    private fun classifyAssistantBlocker(toolName: String, content: String): AssistantBlocker? {
        val parsed = runCatching { org.json.JSONObject(content) }.getOrNull() ?: return null
        val error = parsed.optString("error")
        if (toolName == "get_screen" && error == "empty_ui_tree") {
            return AssistantBlocker.EmptyScreen
        }
        if (toolName == "screenshot" && error == "permission_required") {
            return AssistantBlocker.ScreenCapturePermission
        }
        val verification = parsed.optJSONObject("verification")
        if (verification?.optString("error") == "empty_ui_tree") {
            return AssistantBlocker.EmptyScreen
        }
        return null
    }
}
