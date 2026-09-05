package com.clawdroid.app.core.assistant.context

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.clawdroid.app.core.control.ScreenCaptureManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ScreenshotSource {
    private const val TAG = "ScreenshotSource"

    fun captureToFile(context: Context, quality: Int = 80): String? {
        Log.i(TAG, "captureToFile start active=${ScreenCaptureManager.isActive()} quality=$quality")
        val bitmap = ScreenCaptureManager.captureFrame(context) ?: run {
            Log.w(TAG, "captureToFile failed: captureFrame returned null")
            return null
        }
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, "screenshot_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.flush()
            }
            Log.i(TAG, "captureToFile success path=${file.absolutePath} bytes=${file.length()} size=${bitmap.width}x${bitmap.height}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "captureToFile failed", e)
            null
        } finally {
            bitmap.recycle()
        }
    }
}
