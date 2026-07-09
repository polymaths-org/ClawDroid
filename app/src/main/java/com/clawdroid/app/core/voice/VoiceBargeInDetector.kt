package com.clawdroid.app.core.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VoiceBargeInDetector(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var recorder: AudioRecord? = null

    fun start(
        threshold: Float,
        onSpeech: () -> Unit,
    ) {
        if (job?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        job = scope.launch(Dispatchers.IO) {
            monitor(threshold.coerceIn(0.04f, 0.80f), onSpeech)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun monitor(
        threshold: Float,
        onSpeech: () -> Unit,
    ) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(FRAME_BYTES * 4)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        recorder = audioRecord
        val frame = ByteArray(FRAME_BYTES)
        var hotFrames = 0
        try {
            audioRecord.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(frame, 0, frame.size)
                if (read > 0) {
                    val amp = pcmAmplitude(frame, read)
                    hotFrames = if (amp >= threshold) hotFrames + 1 else 0
                    if (hotFrames >= 2) {
                        onSpeech()
                        break
                    }
                }
                delay(20)
            }
        } finally {
            stop()
        }
    }

    private fun pcmAmplitude(buffer: ByteArray, read: Int): Float {
        var sum = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < read) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val sample = (hi shl 8) or lo
            sum += sample * sample
            samples++
            i += 2
        }
        if (samples == 0) return 0f
        return (sqrt(sum / samples) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_BYTES = 640
    }
}
