package com.clawdroid.app.core.voice

import android.annotation.SuppressLint
import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

class OutputAudioMeter(
    private val onLevels: (List<Float>, Float) -> Unit,
) {
    private var visualizer: Visualizer? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()
        return runCatching {
            val next = Visualizer(0)
            val captureSize = Visualizer.getCaptureSizeRange().lastOrNull()
                ?.coerceIn(Visualizer.getCaptureSizeRange().first(), Visualizer.getCaptureSizeRange().last())
                ?: 256
            next.captureSize = captureSize
            next.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        val data = waveform ?: return
                        onLevels(toLevels(data), rms(data))
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false,
            )
            next.enabled = true
            visualizer = next
            true
        }.onFailure { error ->
            Log.w("OutputAudioMeter", "Output visualizer unavailable", error)
            stop()
        }.getOrDefault(false)
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        onLevels(emptyList(), 0f)
    }

    private fun toLevels(data: ByteArray, buckets: Int = 14): List<Float> {
        if (data.isEmpty()) return emptyList()
        val bucketSize = (data.size / buckets).coerceAtLeast(1)
        return (0 until buckets).map { bucket ->
            val start = bucket * bucketSize
            val end = minOf(start + bucketSize, data.size)
            if (start >= end) return@map 0f
            var sum = 0.0
            for (i in start until end) {
                val centered = (data[i].toInt() and 0xFF) - 128
                sum += centered * centered
            }
            (sqrt(sum / (end - start)) / 128.0).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun rms(data: ByteArray): Float {
        if (data.isEmpty()) return 0f
        var sum = 0.0
        data.forEach { sample ->
            val centered = (sample.toInt() and 0xFF) - 128
            sum += centered * centered
        }
        return (sqrt(sum / data.size) / 128.0).toFloat().coerceIn(0f, 1f)
    }
}
