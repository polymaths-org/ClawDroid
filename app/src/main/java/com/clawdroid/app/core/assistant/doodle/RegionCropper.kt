package com.clawdroid.app.core.assistant.doodle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object RegionCropper {

    fun cropRegion(screenshotPath: String, bounds: Rect, outputDir: File): String? {
        val file = File(screenshotPath)
        if (!file.exists()) return null

        val bitmap = BitmapFactory.decodeFile(screenshotPath) ?: return null
        return try {
            val left = bounds.left.coerceIn(0, bitmap.width)
            val top = bounds.top.coerceIn(0, bitmap.height)
            val width = bounds.width().coerceAtMost(bitmap.width - left)
            val height = bounds.height().coerceAtMost(bitmap.height - top)

            if (width <= 0 || height <= 0) return null

            val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
            val outFile = File(outputDir, "doodle_crop_${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }
            croppedBitmap.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }
}
