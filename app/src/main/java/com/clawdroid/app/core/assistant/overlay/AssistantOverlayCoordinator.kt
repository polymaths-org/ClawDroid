package com.clawdroid.app.core.assistant.overlay

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantInvocationRouter
import com.clawdroid.app.core.assistant.AssistantMode
import kotlinx.coroutines.flow.MutableStateFlow

object AssistantOverlayCoordinator {
    private const val TAG = "AssistantOverlayCoordinator"

    val visible = MutableStateFlow(false)
    val textDelta = MutableStateFlow("")
    val status = MutableStateFlow("")
    val shortLine = MutableStateFlow("")
    val answer = MutableStateFlow("")
    val error = MutableStateFlow("")
    val actionLog = MutableStateFlow<List<String>>(emptyList())
    val currentInvocation = MutableStateFlow<AssistantInvocation?>(null)
    private var sessionUiController: ((Boolean) -> Unit)? = null

    fun setSessionUiController(controller: ((Boolean) -> Unit)?) {
        Log.i(TAG, "setSessionUiController registered=${controller != null}")
        sessionUiController = controller
    }

    fun showOverlay(context: Context, invocation: AssistantInvocation) {
        Log.i(TAG, "showOverlay id=${invocation.id} mode=${invocation.mode} package=${invocation.contextSnapshot?.sourcePackage} screenshot=${invocation.contextSnapshot?.screenshotPath}")
        currentInvocation.value = invocation
        textDelta.value = ""
        status.value = "Ready"
        shortLine.value = "Ask about this screen or choose an action."
        answer.value = ""
        error.value = ""
        actionLog.value = emptyList()
        visible.value = true
    }

    fun showRunning(context: Context, invocation: AssistantInvocation) {
        Log.i(TAG, "showRunning id=${invocation.id} mode=${invocation.mode} textLen=${invocation.userText?.length ?: 0}")
        currentInvocation.value = invocation
        textDelta.value = ""
        status.value = "Thinking..."
        shortLine.value = "Thinking about your request..."
        answer.value = ""
        error.value = ""
        actionLog.value = listOf("Started task")
        visible.value = true
    }

    fun updateText(text: String) {
        Log.d(TAG, "updateText deltaLen=${text.length}")
        textDelta.value = textDelta.value + text
        val compact = textDelta.value
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (compact.isNotBlank()) {
            shortLine.value = compact.take(140)
        }
    }

    fun updateStatus(message: String) {
        Log.i(TAG, "updateStatus message=$message")
        status.value = message
        shortLine.value = message
    }

    fun recordAction(message: String) {
        val clean = message.trim()
        if (clean.isBlank()) return
        val existing = actionLog.value
        actionLog.value = (existing + clean)
            .fold(emptyList<String>()) { acc, item ->
                if (acc.lastOrNull() == item) acc else acc + item
            }
            .takeLast(5)
    }

    fun updateProgress(message: String) {
        val clean = message
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120)
        if (clean.isNotBlank()) {
            shortLine.value = clean
        }
    }

    fun showAnswer(finalText: String) {
        Log.i(TAG, "showAnswer len=${finalText.length}")
        answer.value = finalText
        status.value = "Done"
        shortLine.value = finalText.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(140)
    }

    fun showError(message: String) {
        Log.e(TAG, "showError message=$message")
        error.value = message
        status.value = "Error"
        shortLine.value = message.take(140)
    }

    fun hideOverlay() {
        Log.i(TAG, "hideOverlay currentId=${currentInvocation.value?.id}")
        visible.value = false
    }

    suspend fun <T> withOverlayHiddenForExternalUi(
        reason: String,
        settleMs: Long,
        block: suspend () -> T,
    ): T {
        Log.i(TAG, "withOverlayHidden bypassed reason=$reason keepVisible=${visible.value} settleMs=$settleMs currentId=${currentInvocation.value?.id}")
        if (currentInvocation.value != null) {
            visible.value = true
        }
        return block()
    }

    @Composable
    fun ContentOverlay(
        onWindowDrag: (Float, Float) -> Unit = { _, _ -> },
    ) {
        val isVisible by visible.collectAsState()
        if (isVisible) {
            val context = LocalContext.current
            val invocation by currentInvocation.collectAsState()
            val delta by textDelta.collectAsState()
            val stat by status.collectAsState()
            val line by shortLine.collectAsState()
            val ans by answer.collectAsState()
            val err by error.collectAsState()
            val actions by actionLog.collectAsState()

            AssistantOverlayView(
                invocation = invocation,
                status = stat,
                shortLine = line,
                textDelta = delta,
                answer = ans,
                error = err,
                actionLog = actions,
                onWindowDrag = onWindowDrag,
                onSubmit = { text ->
                    val activeInvocation = invocation ?: return@AssistantOverlayView
                    AssistantInvocationRouter.submit(context, activeInvocation, text)
                },
                onTranslate = {
                    val activeInvocation = invocation ?: return@AssistantOverlayView
                    AssistantInvocationRouter.submit(
                        context = context,
                        invocation = activeInvocation,
                        userText = "Translate the visible text on this screen. If there are multiple languages, identify them and translate to English.",
                        mode = AssistantMode.SUMMARIZE,
                    )
                },
                onStop = { AssistantInvocationRouter.stopActive() },
                onDismiss = {
                    hideOverlay()
                    runCatching {
                        context.stopService(Intent(context, OverlayWindowService::class.java))
                    }
                }
            )
        }
    }
}
