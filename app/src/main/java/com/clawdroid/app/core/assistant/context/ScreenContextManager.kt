package com.clawdroid.app.core.assistant.context

import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.clawdroid.app.core.assistant.AssistantContextSnapshot
import com.clawdroid.app.core.assistant.CaptureMethod
import com.clawdroid.app.core.config.AppConfigManager
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
        val mode = AppConfigManager.screenContextMode
        val wantsScreenshot = mode == "screenshot_only" ||
            mode == "both" ||
            AppConfigManager.visualContextFallbackEnabled
        var screenshotPath: String? = null

        // Save a provided screenshot only when visual context is enabled.
        if (screenshotBitmap != null && wantsScreenshot) {
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
        }

        // Priority 1: Process AssistStructure if available.
        if (structure != null && mode != "screenshot_only") {
            val extracted = AssistStructureExtractor.extract(structure)
            Log.i(
                TAG,
                "assist extracted package=${extracted.sourcePackage} activity=${extracted.sourceActivity} " +
                    "visibleLen=${extracted.visibleText.length} descLen=${extracted.contentDescriptionText.length} " +
                    "focused=${extracted.focusedText?.take(48)} webUri=${extracted.webUri}"
            )
            if (extracted.visibleText.isNotBlank() || extracted.contentDescriptionText.isNotBlank()) {
                if (mode == "both" && screenshotPath == null && wantsScreenshot) {
                    screenshotPath = ScreenshotSource.captureToFile(context)
                    Log.i(TAG, "captured screenshot for both-mode snapshot path=$screenshotPath")
                }
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

        // Priority 2: Process Accessibility Service tree dump.
        val controlSnapshot = if (mode == "screenshot_only") null else AndroidControlContextBridge.captureSnapshot(screenshotPath)
        if (controlSnapshot != null && (controlSnapshot.visibleText.isNotBlank() || controlSnapshot.contentDescriptionText.isNotBlank())) {
            if (mode == "both" && controlSnapshot.screenshotPath == null && wantsScreenshot) {
                screenshotPath = ScreenshotSource.captureToFile(context)
                Log.i(TAG, "captured screenshot for both-mode control snapshot path=$screenshotPath")
                return controlSnapshot.copy(screenshotPath = screenshotPath)
            }
            Log.i(
                TAG,
                "returning control snapshot package=${controlSnapshot.sourcePackage} method=${controlSnapshot.captureMethod} " +
                    "visibleLen=${controlSnapshot.visibleText.length} descLen=${controlSnapshot.contentDescriptionText.length}"
            )
            return controlSnapshot
        }

        if (wantsScreenshot && screenshotPath == null) {
            screenshotPath = ScreenshotSource.captureToFile(context)
            Log.i(TAG, "captureToFile visual fallback path=$screenshotPath")
        }

        // Priority 3: Fallback to plain screenshot source.
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
