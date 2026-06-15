package com.clawdroid.app.core.control

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import java.io.ByteArrayOutputStream

object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensity = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped")
            releaseCaptureResources()
        }
    }

    fun isActive(): Boolean = mediaProjection != null && virtualDisplay != null

    fun startCapture(context: Context, resultCode: Int, data: Intent): Boolean {
        stopCapture()
        return runCatching {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)
                ?: return false

            val metrics = context.resources.displayMetrics
            captureWidth = metrics.widthPixels
            captureHeight = metrics.heightPixels
            captureDensity = metrics.densityDpi

            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight,
                PixelFormat.RGBA_8888,
                2,
            )

            projection.registerCallback(projectionCallback, mainHandler)

            val display = projection.createVirtualDisplay(
                "ClawDroidCapture",
                captureWidth,
                captureHeight,
                captureDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler,
            ) ?: run {
                reader.close()
                projection.stop()
                return false
            }

            mediaProjection = projection
            imageReader = reader
            virtualDisplay = display
            Log.i(TAG, "Screen capture session started (${captureWidth}x$captureHeight)")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to start screen capture", error)
            stopCapture()
            false
        }
    }

    fun stopCapture() {
        releaseCaptureResources()
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun releaseCaptureResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun captureFrame(context: Context): Bitmap? {
        val reader = imageReader ?: return null
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ClawDroid:ScreenCapture",
        )
        return try {
            wakeLock.acquire(10_000L)
            val image = reader.acquireLatestImage() ?: return null
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * captureWidth

                val bitmap = Bitmap.createBitmap(
                    captureWidth + rowPadding / pixelStride,
                    captureHeight,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)
                if (bitmap.width != captureWidth) {
                    Bitmap.createBitmap(bitmap, 0, 0, captureWidth, captureHeight)
                } else {
                    bitmap
                }
            } finally {
                image.close()
            }
        } catch (error: Exception) {
            Log.e(TAG, "captureFrame failed", error)
            null
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    fun captureAsBase64(context: Context, quality: Int = 75): String? {
        val bitmap = captureFrame(context) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    fun isTreeMeaningful(tree: String): Boolean {
        if (tree.length < 80) return false
        return runCatching {
            val json = org.json.JSONObject(tree)
            val nodes = json.optJSONArray("nodes") ?: return false
            val count = IntArray(1)
            countClickableNodes(nodes, count)
            count[0] >= 3
        }.getOrDefault(false)
    }

    private fun countClickableNodes(nodes: org.json.JSONArray, clickableCountRef: IntArray) {
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            if (node.optBoolean("isClickable")) {
                clickableCountRef[0]++
            }
            node.optJSONArray("children")?.let { countClickableNodes(it, clickableCountRef) }
        }
    }
}
