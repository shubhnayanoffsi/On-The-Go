package com.onthego.stt

import android.content.Context
import com.onthego.vad.SileroVad

/**
 * JNI wrapper over whisper.cpp. Loads ggml-base once per utterance,
 * then unloads to keep 6GB RAM free. Runs VAD first when enabled.
 */
class WhisperEngine {

    fun isReady(ctx: Context): Boolean = ModelDownloader.isReady(ctx)

    fun transcribe(ctx: Context, pcm16k: ShortArray, language: String = "en"): String {
        val f = ModelDownloader.modelFile(ctx)
        if (!isReady(ctx)) return ""
        val lang = if (language == "auto") "" else language
        val speech = if (com.onthego.Config.vadEnabled)
            SileroVad.filterSpeech(pcm16k, com.onthego.Config.vadStrict) else pcm16k
        if (speech.isEmpty()) return ""
        return nativeTranscribe(f.absolutePath, speech, lang)
    }

    private external fun nativeTranscribe(modelPath: String, pcm: ShortArray, lang: String): String

    companion object {
        init {
            try { System.loadLibrary("otg-whisper") } catch (_: UnsatisfiedLinkError) {}
        }
    }
}
