package com.clawdroid.app.core.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

class ScreenReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenReaderService"
        private const val MAX_DEPTH = 12
        private const val MAX_NODES = 200
        private const val MIN_DUMP_INTERVAL_MS = 100L
        private const val GESTURE_TIMEOUT_MS = 3_000L

        @Volatile
        var instance: ScreenReaderService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastDumpTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "ScreenReaderService connected")
    }

    override fun onDestroy() {
        instance = null
        Log.i(TAG, "ScreenReaderService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    suspend fun dumpNodeTree(): String = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        if (now - lastDumpTime < MIN_DUMP_INTERVAL_MS) {
            Thread.sleep(MIN_DUMP_INTERVAL_MS - (now - lastDumpTime))
        }
        lastDumpTime = System.currentTimeMillis()

        val root = withContext(Dispatchers.Main) {
            getUsableRoot()
        } ?: return@withContext JSONObject()
            .put("truncated", false)
            .put("nodes", JSONArray())
            .put("package", JSONObject.NULL)
            .toString()

        try {
            val counter = NodeCounter()
            val nodes = JSONArray()
            serializeNode(root, depth = 0, nodes = nodes, counter = counter)
            JSONObject()
                .put("truncated", counter.truncated)
                .put("package", root.packageName?.toString() ?: JSONObject.NULL)
                .put("nodes", nodes)
                .toString()
        } finally {
            root.recycle()
        }
    }

    private data class NodeCounter(var count: Int = 0, var truncated: Boolean = false)

    private fun serializeNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: JSONArray,
        counter: NodeCounter,
    ) {
        if (depth > MAX_DEPTH || counter.count >= MAX_NODES) {
            counter.truncated = true
            return
        }

        val text = node.text?.toString()?.trim().orEmpty()
        val contentDesc = node.contentDescription?.toString()?.trim().orEmpty()
        val isClickable = node.isClickable
        val isScrollable = node.isScrollable
        val isEditable = node.isEditable

        if (text.isEmpty() && contentDesc.isEmpty() && !isClickable && !isScrollable && !isEditable) {
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    serializeNode(child, depth, nodes, counter)
                } finally {
                    child.recycle()
                }
            }
            return
        }

        counter.count++
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val obj = JSONObject()
            .put("text", text.ifEmpty { JSONObject.NULL })
            .put("contentDescription", contentDesc.ifEmpty { JSONObject.NULL })
            .put("className", node.className?.toString() ?: JSONObject.NULL)
            .put("resourceId", node.viewIdResourceName ?: JSONObject.NULL)
            .put(
                "bounds",
                JSONObject()
                    .put("l", bounds.left)
                    .put("t", bounds.top)
                    .put("r", bounds.right)
                    .put("b", bounds.bottom),
            )
            .put("isClickable", isClickable)
            .put("isScrollable", isScrollable)
            .put("isEditable", isEditable)
            .put("isEnabled", node.isEnabled)
            .put("childCount", node.childCount)

        if (depth < MAX_DEPTH && counter.count < MAX_NODES) {
            val children = JSONArray()
            val childCount = node.childCount
            for (i in 0 until childCount) {
                if (counter.count >= MAX_NODES) {
                    counter.truncated = true
                    break
                }
                val child = node.getChild(i) ?: continue
                try {
                    serializeNode(child, depth + 1, children, counter)
                } finally {
                    child.recycle()
                }
            }
            if (children.length() > 0) {
                obj.put("children", children)
            }
        }

        nodes.put(obj)
    }

    suspend fun tap(x: Float, y: Float): Boolean = dispatchStroke(
        buildStroke(x, y, x, y, 50L),
    )

    suspend fun longPress(x: Float, y: Float): Boolean = dispatchStroke(
        buildStroke(x, y, x, y, 600L),
    )

    suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
    ): Boolean = dispatchStroke(buildStroke(x1, y1, x2, y2, durationMs))

    private fun buildStroke(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): GestureDescription.StrokeDescription {
        val path = Path().apply {
            moveTo(x1, y1)
            if (x1 != x2 || y1 != y2) {
                lineTo(x2, y2)
            }
        }
        return GestureDescription.StrokeDescription(path, 0, durationMs)
    }

    private suspend fun dispatchStroke(stroke: GestureDescription.StrokeDescription): Boolean {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                mainHandler.post {
                    val dispatched = dispatchGesture(
                        gesture,
                        object : GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(true)
                            }

                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (cont.isActive) cont.resume(false)
                            }
                        },
                        null,
                    )
                    if (!dispatched && cont.isActive) {
                        cont.resume(false)
                    }
                }
            }
        } ?: false
    }

    fun scroll(direction: String): Boolean {
        val root = getUsableRoot() ?: return false
        return try {
            val scrollable = findScrollableNode(root) ?: return false
            val action = when (direction.lowercase()) {
                "down", "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                "up", "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> return false
            }
            scrollable.performAction(action)
        } finally {
            root.recycle()
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun tapByText(label: String): Boolean {
        val root = getUsableRoot() ?: return false
        return try {
            val node = findNodeByText(root, label) ?: return false
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            root.recycle()
        }
    }

    fun tapByResourceId(id: String): Boolean {
        val root = getUsableRoot() ?: return false
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNullOrEmpty()) return false
            val node = nodes.first()
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            nodes.forEach { it.recycle() }
            result
        } finally {
            root.recycle()
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if ((text.equals(label, ignoreCase = true) || desc.equals(label, ignoreCase = true)) && node.isClickable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, label)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun typeText(text: String): Boolean {
        val root = getUsableRoot() ?: return false
        return try {
            val editable = findFocusedEditable(root) ?: findEditableNode(root) ?: return false
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycle()
        }
    }

    fun clearText(): Boolean {
        return typeText("")
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    private fun getUsableRoot(): AccessibilityNodeInfo? {
        val ownPackage = packageName
        val windowRoots = windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window -> window.root?.also { it.refresh() } }
            .toList()

        val preferred = windowRoots.firstOrNull { root ->
            val pkg = root.packageName?.toString()
            !pkg.isNullOrBlank() && pkg != ownPackage
        }
        if (preferred != null) {
            windowRoots.filter { it !== preferred }.forEach { it.recycle() }
            Log.d(TAG, "getUsableRoot selected package=${preferred.packageName} from windows=${windowRoots.size}")
            return preferred
        }

        windowRoots.forEach { it.recycle() }
        return rootInActiveWindow?.also {
            it.refresh()
            Log.d(TAG, "getUsableRoot fallback package=${it.packageName}")
        }
    }
}
