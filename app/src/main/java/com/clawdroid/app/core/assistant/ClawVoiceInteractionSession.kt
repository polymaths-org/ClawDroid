package com.clawdroid.app.core.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.clawdroid.app.core.assistant.context.ScreenContextManager
import com.clawdroid.app.core.assistant.overlay.AssistantOverlayCoordinator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class ClawVoiceInteractionSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "ClawVoiceInteractionSession"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Assistant session coroutine crashed", throwable)
        AssistantOverlayCoordinator.showError("Assistant crashed while preparing the screen: ${throwable.message ?: throwable::class.java.simpleName}")
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

    private var pendingStructure: AssistStructure? = null
    private var pendingScreenshot: Bitmap? = null
    private var currentInvocationId: String = UUID.randomUUID().toString()

    init {
        Log.i(TAG, "init session owner=${context.packageName}")
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        AssistantOverlayCoordinator.setSessionUiController { enabled ->
            Log.i(TAG, "setUiEnabled($enabled) invocationId=$currentInvocationId")
            setUiEnabled(enabled)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        pendingStructure = null
        pendingScreenshot = null
        currentInvocationId = UUID.randomUUID().toString()
        Log.i(TAG, "onShow invocationId=$currentInvocationId showFlags=$showFlags argsKeys=${args?.keySet()?.joinToString() ?: "none"}")
    }

    override fun onHide() {
        Log.i(TAG, "onHide invocationId=$currentInvocationId")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onHide()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy invocationId=$currentInvocationId")
        AssistantOverlayCoordinator.setSessionUiController(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        mViewModelStore.clear()
        super.onDestroy()
    }

    override fun onCreateContentView(): View {
        Log.i(TAG, "onCreateContentView invocationId=$currentInvocationId")
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ClawVoiceInteractionSession)
            setViewTreeViewModelStoreOwner(this@ClawVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@ClawVoiceInteractionSession)
            setContent {
                AssistantOverlayCoordinator.ContentOverlay()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        Log.i(
            TAG,
            "onHandleAssist invocationId=$currentInvocationId structure=${structure != null} " +
                "windows=${runCatching { structure?.windowNodeCount }.getOrNull() ?: "unknown"} " +
                "contentUri=${content?.webUri} dataKeys=${data?.keySet()?.joinToString() ?: "none"}"
        )
        pendingStructure = structure
        processSnapshotIfReady()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        Log.i(
            TAG,
            "onHandleScreenshot invocationId=$currentInvocationId bitmap=${screenshot != null} " +
                "size=${screenshot?.width ?: 0}x${screenshot?.height ?: 0}"
        )
        pendingScreenshot = screenshot
        processSnapshotIfReady()
    }

    private fun processSnapshotIfReady() {
        Log.i(
            TAG,
            "processSnapshotIfReady start invocationId=$currentInvocationId " +
                "hasStructure=${pendingStructure != null} hasScreenshot=${pendingScreenshot != null}"
        )
        scope.launch {
            val snapshot = runCatching {
                ScreenContextManager.getScreenSnapshot(
                    context = context,
                    structure = pendingStructure,
                    screenshotBitmap = pendingScreenshot
                )
            }.onFailure { error ->
                Log.e(TAG, "getScreenSnapshot failed invocationId=$currentInvocationId", error)
            }.getOrThrow()

            Log.i(
                TAG,
                "snapshot ready invocationId=$currentInvocationId package=${snapshot.sourcePackage} " +
                    "activity=${snapshot.sourceActivity} method=${snapshot.captureMethod} " +
                    "hasScreenshot=${snapshot.screenshotPath != null} screenshotPath=${snapshot.screenshotPath} " +
                    "visibleTextLen=${snapshot.visibleText.length} contentDescLen=${snapshot.contentDescriptionText.length} " +
                    "focused=${snapshot.focusedText?.take(48)}"
            )

            val invocation = AssistantInvocation(
                id = currentInvocationId,
                source = AssistantInvocationSource.SYSTEM_ASSIST,
                mode = AssistantMode.ASK_SCREEN,
                userText = null,
                contextSnapshot = snapshot,
                mediaPath = null,
                mediaMimeType = null,
                projectId = null,
                conversationId = null,
                createdAt = System.currentTimeMillis()
            )

            Log.i(TAG, "present overlay invocationId=${invocation.id}")
            AssistantInvocationRouter.present(context, invocation)
        }
    }
}
