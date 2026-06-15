package com.clawdroid.app.core.assistant.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
import com.clawdroid.app.ui.theme.ClawDroidTheme
import kotlin.math.roundToInt

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

    override fun onCreate() {
        super.onCreate()
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 18
            y = 140
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
