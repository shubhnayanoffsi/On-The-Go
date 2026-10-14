package com.onthego.stt

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.onthego.Config
import com.onthego.vad.SileroVad

/**
 * Resident whisper.cpp context keyed by model path. Clips under 15s of
 * trimmed audio use tiny.en, longer ones base.en. Context stays loaded.
 * All failures return "" and never throw.
 */
class WhisperEngine {
    @Volatile private var loadedPath: String? = null

    fun isReady(ctx: Context): Boolean =
        ModelDownloader.isReady(ctx, ModelDownloader.TINY) ||
            ModelDownloader.isReady(ctx, ModelDownloader.BASE)

    @Synchronized
    fun transcribe(ctx: Context, pcm16k: ShortArray, language: String = "en"): String {
        try {
            val speech = SileroVad.filterSpeech(pcm16k, Config.vadStrict)
            if (speech.isEmpty()) return ""
            val name = if (speech.size / 16000 < 15) ModelDownloader.TINY else ModelDownloader.BASE
            if (!ensureLoaded(ctx, name)) return ""
            val lang = if (language == "auto") "" else language
            return try {
                nativeTranscribeChunk(speech, lang)
            } catch (_: Throwable) { "" }
        } catch (_: Throwable) {
            return ""
        }
    }

    @Synchronized
    fun preload(ctx: Context, name: String): Boolean {
        return try { ensureLoaded(ctx, name) } catch (_: Throwable) { false }
    }

    @Synchronized
    fun release() {
        try { nativeFree() } catch (_: Throwable) { /* ignore */ }
        loadedPath = null
    }

    private fun ensureLoaded(ctx: Context, name: String): Boolean {
        val f = ModelDownloader.modelFile(ctx, name)
        if (!f.exists()) return false
        val path = f.absolutePath
        if (loadedPath == path) return true
        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val t0 = SystemClock.elapsedRealtime()
        val ok = try { nativeInit(path, cores) } catch (_: Throwable) { false }
        if (!ok) { loadedPath = null; return false }
        loadedPath = path
        Log.i("OTG-TIME", "whisper load $name ${SystemClock.elapsedRealtime() - t0}ms threads=$cores")
        return true
    }

    private external fun nativeInit(modelPath: String, threads: Int): Boolean
    private external fun nativeTranscribeChunk(pcm: ShortArray, lang: String): String
    private external fun nativeFree()

    companion object {
        init {
            try { System.loadLibrary("otg-whisper") } catch (_: UnsatisfiedLinkError) {}
        }
    }
}
