package com.onthego.stt

import android.content.Context
import java.io.File

/**
 * JNI wrapper over whisper.cpp. Loads ggml-base once per utterance,
 * then unloads to keep 6GB RAM free. Model downloaded on first run.
 */
class WhisperEngine {
    fun modelFile(ctx: Context): File =
        File(ctx.filesDir, "models/ggml-base.bin")

    fun transcribe(ctx: Context, pcm16k: ShortArray): String {
        val f = modelFile(ctx)
        if (!f.exists()) return "" // TODO: trigger ModelDownloader then retry
        // TODO v0.1: call native transcribe(f.path, pcm16k, lang="en")
        return nativeTranscribe(f.absolutePath, pcm16k)
    }

    private external fun nativeTranscribe(modelPath: String, pcm: ShortArray): String

    companion object {
        init {
            try { System.loadLibrary("otg-whisper") } catch (_: UnsatisfiedLinkError) {}
        }
    }
}
