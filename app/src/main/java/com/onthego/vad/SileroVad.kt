package com.onthego.vad

/**
 * v0.1 VAD: energy-based speech filter, zero RAM, no model file.
 * 20ms frames at 16kHz, RMS gate by strict level, 200ms hangover
 * so word ends are not cut. Drops pure silence before whisper.
 * Upgrade path v0.2: Silero ONNX via onnxruntime-android (same call site).
 */
object SileroVad {
    fun filterSpeech(pcm16k: ShortArray, strict: Int = 2): ShortArray {
        if (pcm16k.size < 320) return pcm16k
        val thresh = when (strict) {
            1 -> 0.010f
            3 -> 0.040f
            else -> 0.020f
        }
        val frame = 320 // 20ms @16kHz
        val hang = 10 // 200ms hangover frames
        val keep = BooleanArray((pcm16k.size + frame - 1) / frame)
        for (f in keep.indices) {
            var sum = 0.0
            val n = minOf(frame, pcm16k.size - f * frame)
            for (i in 0 until n) {
                val s = pcm16k[f * frame + i] / 32768.0f
                sum += s * s
            }
            val rms = kotlin.math.sqrt(sum / n).toFloat()
            if (rms > thresh) {
                for (k in f until minOf(keep.size, f + hang + 1)) keep[k] = true
            }
        }
        if (!keep.any { it }) return pcm16k // all silence: let whisper decide
        val out = mutableListOf<Short>()
        for (f in keep.indices) {
            if (!keep[f]) continue
            val n = minOf(frame, pcm16k.size - f * frame)
            for (i in 0 until n) out.add(pcm16k[f * frame + i])
        }
        return out.toShortArray()
    }
}
