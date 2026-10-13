package com.onthego.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** 16kHz mono 16-bit capture for whisper.cpp. No resample needed. */
class AudioCapture {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    private val buf = mutableListOf<Short>()

    fun start() {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // TODO: runtime RECORD_AUDIO permission check before this call
        rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2
        )
        buf.clear()
        rec?.startRecording()
        thread = Thread {
            val tmp = ShortArray(min)
            while (rec?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = rec?.read(tmp, 0, tmp.size) ?: 0
                if (n > 0) synchronized(buf) {
                    for (i in 0 until n) buf.add(tmp[i])
                }
            }
        }.also { it.start() }
    }

    fun stop(): ShortArray {
        rec?.stop()
        rec?.release()
        rec = null
        thread?.join(500)
        synchronized(buf) { return buf.toShortArray() }
    }
}
