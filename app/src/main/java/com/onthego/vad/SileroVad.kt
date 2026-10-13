package com.onthego.vad

/** Phase 2: Silero VAD via onnxruntime-android. Keeps speech, drops silence. */
object SileroVad {
    fun filterSpeech(pcm16k: ShortArray): ShortArray {
        // TODO: run silero_vad.onnx windowed at 16kHz, return speech-only chunks
        return pcm16k
    }
}
