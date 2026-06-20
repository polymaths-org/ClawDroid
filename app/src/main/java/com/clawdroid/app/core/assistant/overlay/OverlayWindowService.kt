package com.clawdroid.app.core.assistant.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
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
import com.clawdroid.app.core.assistant.AssistantInvocation
import com.clawdroid.app.core.assistant.AssistantInvocationSource
import com.clawdroid.app.core.assistant.AssistantMode
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.theme.ClawDroidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt
import java.util.UUID

class OverlayWindowService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val mViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = mViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val EXTRA_OVERLAY_MODE = "OVERLAY_MODE"
        const val EXTRA_VOICE_GREETING = "VOICE_GREETING"
        const val EXTRA_VOICE_LISTEN_TIMEOUT_SECONDS = "VOICE_LISTEN_TIMEOUT_SECONDS"
        const val EXTRA_VOICE_SOURCE = "VOICE_SOURCE"
        const val MODE_VOICE = "voice"
        const val MODE_CHAT = "chat"
        const val SOURCE_NOTIFICATION = "notification"
        private var pendingChatInvocation: AssistantInvocation? = null

        fun startVoice(
            context: Context,
            greeting: String? = null,
            listenTimeoutSeconds: Int? = null,
            source: AssistantInvocationSource = AssistantInvocationSource.VOICE_CALL,
        ) {
            context.startService(
                Intent(context, OverlayWindowService::class.java)
                    .putExtra(EXTRA_OVERLAY_MODE, MODE_VOICE)
                    .putExtra(EXTRA_VOICE_GREETING, greeting)
                    .putExtra(EXTRA_VOICE_SOURCE, if (source == AssistantInvocationSource.NOTIFICATION_ACTION) SOURCE_NOTIFICATION else "")
                    .apply {
                        if (listenTimeoutSeconds != null) {
                            putExtra(EXTRA_VOICE_LISTEN_TIMEOUT_SECONDS, listenTimeoutSeconds)
                        }
                    },
            )
        }

        fun startChat(
            context: Context,
            invocation: AssistantInvocation? = null,
        ) {
            pendingChatInvocation = invocation
            context.startService(
                Intent(context, OverlayWindowService::class.java)
                    .putExtra(EXTRA_OVERLAY_MODE, MODE_CHAT),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayWindow()
    }

    private fun setupOverlayWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        layoutParams = params

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayWindowService)
            setViewTreeViewModelStoreOwner(this@OverlayWindowService)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowService)
            setContent {
                ClawDroidTheme {
                    AssistantOverlayCoordinator.ContentOverlay(
                        onWindowDrag = { dx, dy -> moveWindowBy(dx, dy) },
                    )
                }
            }
        }

        windowManager?.addView(composeView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_OVERLAY_MODE)) {
            MODE_CHAT -> {
                val invocation = pendingChatInvocation ?: AssistantInvocation(
                    id = UUID.randomUUID().toString(),
                    source = AssistantInvocationSource.OVERLAY_BUTTON,
                    mode = AssistantMode.ASK_SCREEN,
                    userText = null,
                    contextSnapshot = null,
                    mediaPath = null,
                    mediaMimeType = null,
                    projectId = AppConfigManager.activeProjectId,
                    conversationId = AppConfigManager.activeConversationId,
                    createdAt = System.currentTimeMillis(),
                )
                pendingChatInvocation = null
                AssistantOverlayCoordinator.showOverlay(this, invocation)
            }
            MODE_VOICE -> {
            val source = if (intent.getStringExtra(EXTRA_VOICE_SOURCE) == SOURCE_NOTIFICATION) {
                AssistantInvocationSource.NOTIFICATION_ACTION
            } else {
                AssistantInvocationSource.VOICE_CALL
            }
            val listenTimeoutSeconds = if (intent.hasExtra(EXTRA_VOICE_LISTEN_TIMEOUT_SECONDS)) {
                intent.getIntExtra(EXTRA_VOICE_LISTEN_TIMEOUT_SECONDS, 8)
            } else {
                null
            }
            AssistantOverlayCoordinator.showOverlay(
                this,
                AssistantInvocation(
                    id = UUID.randomUUID().toString(),
                    source = source,
                    mode = AssistantMode.VOICE_CHAT,
                    userText = null,
                    contextSnapshot = null,
                    mediaPath = null,
                    mediaMimeType = null,
                    projectId = AppConfigManager.activeProjectId,
                    conversationId = AppConfigManager.activeConversationId,
                    createdAt = System.currentTimeMillis(),
                ),
                greeting = intent.getStringExtra(EXTRA_VOICE_GREETING),
                listenTimeoutSeconds = listenTimeoutSeconds,
            )
            }
        }
        return START_NOT_STICKY
    }

    private fun moveWindowBy(dx: Float, dy: Float) {
        val view = composeView ?: return
        val params = layoutParams ?: return
        val displayMetrics = resources.displayMetrics
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (displayMetrics.widthPixels - 72).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (displayMetrics.heightPixels - 96).coerceAtLeast(0))
        runCatching {
            windowManager?.updateViewLayout(view, params)
        }
    }

    override fun onDestroy() {
        overlayScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        composeView?.let {
            windowManager?.removeView(it)
        }
        mViewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
