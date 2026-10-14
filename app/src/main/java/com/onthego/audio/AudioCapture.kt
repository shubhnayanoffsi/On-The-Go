package com.onthego.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * 16kHz mono 16-bit capture for whisper.cpp. Fails LOUDLY:
 * every init/read problem throws with a plain message instead of
 * recording silence. Callers surface it on the result card.
 */
class AudioCapture {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    private val buf = mutableListOf<Short>()
    @Volatile private var readErrors = 0
    @Volatile private var peak = 0

    data class Info(val samples: Int, val seconds: Double, val peakPct: Int, val readErrors: Int)

    fun start() {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) throw IllegalStateException("Mic init failed (code $min). Close other recorder apps and retry.")
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            try { r.release() } catch (_: Exception) {}
            throw IllegalStateException("Mic busy or blocked. Close other recorder apps and retry.")
        }
        rec = r
        buf.clear()
        readErrors = 0
        peak = 0
        r.startRecording()
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            stopQuiet()
            throw IllegalStateException("Mic refused to start. Check permission and retry.")
        }
        thread = Thread {
            val tmp = ShortArray(min)
            while (rec?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = try { rec?.read(tmp, 0, tmp.size) ?: -1 } catch (_: Exception) { -1 }
                if (n > 0) synchronized(buf) {
                    for (i in 0 until n) {
                        val s = tmp[i]
                        buf.add(s)
                        val a = kotlin.math.abs(s.toInt())
                        if (a > peak) peak = a
                    }
                } else if (n < 0) {
                    readErrors++
                }
            }
        }.also { it.start() }
    }

    fun stop(): ShortArray {
        stopQuiet()
        thread?.join(2000)
        synchronized(buf) { return buf.toShortArray() }
    }

    fun info(): Info {
        val n: Int = synchronized(buf) { buf.size }
        return Info(n, n / 16000.0, (peak * 100 / 32768).coerceIn(0, 100), readErrors)
    }

    private fun stopQuiet() {
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        rec = null
    }
}
