package com.clawdroid.app.core.assistant.context

import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.clawdroid.app.core.assistant.AssistantContextSnapshot
import com.clawdroid.app.core.assistant.CaptureMethod
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ScreenContextManager {
    private const val TAG = "ScreenContextManager"

    suspend fun getScreenSnapshot(
        context: Context,
        structure: AssistStructure? = null,
        screenshotBitmap: Bitmap? = null
    ): AssistantContextSnapshot {
        Log.i(TAG, "getScreenSnapshot start hasStructure=${structure != null} screenshotBitmap=${screenshotBitmap != null} bitmapSize=${screenshotBitmap?.width ?: 0}x${screenshotBitmap?.height ?: 0}")
        val capturedAt = System.currentTimeMillis()
        var screenshotPath: String? = null

        // Priority 1: Save screenshot if provided
        if (screenshotBitmap != null) {
            runCatching {
                val file = File(context.cacheDir, "screenshot_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { out ->
                    screenshotBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.flush()
                }
                screenshotPath = file.absolutePath
                Log.i(TAG, "saved assist screenshot path=$screenshotPath bytes=${file.length()}")
            }.onFailure { error ->
                Log.e(TAG, "failed saving assist screenshot", error)
            }
        } else {
            screenshotPath = ScreenshotSource.captureToFile(context)
            Log.i(TAG, "captureToFile fallback path=$screenshotPath")
        }

        // Priority 2: Process AssistStructure if available
        if (structure != null) {
            val extracted = AssistStructureExtractor.extract(structure)
            Log.i(
                TAG,
                "assist extracted package=${extracted.sourcePackage} activity=${extracted.sourceActivity} " +
                    "visibleLen=${extracted.visibleText.length} descLen=${extracted.contentDescriptionText.length} " +
                    "focused=${extracted.focusedText?.take(48)} webUri=${extracted.webUri}"
            )
            if (extracted.visibleText.isNotBlank() || extracted.contentDescriptionText.isNotBlank()) {
                Log.i(TAG, "returning ASSIST_STRUCTURE snapshot screenshot=$screenshotPath")
                return AssistantContextSnapshot(
                    sourcePackage = extracted.sourcePackage,
                    sourceActivity = extracted.sourceActivity,
                    visibleText = extracted.visibleText,
                    contentDescriptionText = extracted.contentDescriptionText,
                    focusedText = extracted.focusedText,
                    webUri = extracted.webUri,
                    screenshotPath = screenshotPath,
                    selectedRegionPath = null,
                    capturedAt = capturedAt,
                    captureMethod = CaptureMethod.ASSIST_STRUCTURE
                )
            }
        }

        // Priority 3: Process Accessibility Service tree dump
        val controlSnapshot = AndroidControlContextBridge.captureSnapshot(screenshotPath)
        if (controlSnapshot != null && (controlSnapshot.visibleText.isNotBlank() || controlSnapshot.contentDescriptionText.isNotBlank())) {
            Log.i(
                TAG,
                "returning control snapshot package=${controlSnapshot.sourcePackage} method=${controlSnapshot.captureMethod} " +
                    "visibleLen=${controlSnapshot.visibleText.length} descLen=${controlSnapshot.contentDescriptionText.length}"
            )
            return controlSnapshot
        }

        // Priority 4: Fallback to plain screenshot source
        Log.w(TAG, "returning fallback snapshot method=${if (screenshotPath != null) CaptureMethod.ANDROID_CONTROL_SCREENSHOT else CaptureMethod.NONE} screenshot=$screenshotPath")
        return AssistantContextSnapshot(
            sourcePackage = null,
            sourceActivity = null,
            visibleText = "",
            contentDescriptionText = "",
            focusedText = null,
            webUri = null,
            screenshotPath = screenshotPath,
            selectedRegionPath = null,
            capturedAt = capturedAt,
            captureMethod = if (screenshotPath != null) CaptureMethod.ANDROID_CONTROL_SCREENSHOT else CaptureMethod.NONE
        )
    }
}
