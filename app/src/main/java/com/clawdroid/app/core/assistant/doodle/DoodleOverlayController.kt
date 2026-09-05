package com.clawdroid.app.core.assistant.doodle

import android.content.Context
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.clawdroid.app.core.assistant.context.ScreenshotSource

object DoodleOverlayController {

    @Composable
    fun DoodleCanvas(
        context: Context,
        onSelectionComplete: (DoodleSelection) -> Unit,
        onCancel: () -> Unit
    ) {
        var points by remember { mutableStateOf(listOf<Offset>()) }
        val drawPath = remember { Path() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            points = listOf(offset)
                            drawPath.reset()
                            drawPath.moveTo(offset.x, offset.y)
                        },
                        onDragEnd = {
                            if (points.isNotEmpty()) {
                                var minX = Float.MAX_VALUE
                                var minY = Float.MAX_VALUE
                                var maxX = Float.MIN_VALUE
                                var maxY = Float.MIN_VALUE
                                for (p in points) {
                                    if (p.x < minX) minX = p.x
                                    if (p.y < minY) minY = p.y
                                    if (p.x > maxX) maxX = p.x
                                    if (p.y > maxY) maxY = p.y
                                }

                                val bounds = Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
                                val screenshot = ScreenshotSource.captureToFile(context)
                                if (screenshot != null) {
                                    val crop = RegionCropper.cropRegion(screenshot, bounds, context.cacheDir)
                                    if (crop != null) {
                                        onSelectionComplete(
                                            DoodleSelection(
                                                boundsPx = bounds,
                                                pathSvgLike = null,
                                                screenshotPath = screenshot,
                                                cropPath = crop,
                                                userPrompt = "Analyze this marked screen region."
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = onCancel,
                        onDrag = { change, _ ->
                            change.consume()
                            val nextPoint = change.position
                            points = points + nextPoint
                        }
                    )
                }
        ) {
            if (points.size > 1) {
                drawPath.reset()
                drawPath.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    drawPath.lineTo(points[i].x, points[i].y)
                }
                drawPath(
                    path = drawPath,
                    color = Color(0xFF8B5CF6),
                    style = Stroke(width = 6f)
                )
            }
        }
    }
}
