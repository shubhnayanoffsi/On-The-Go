package com.onthego.stt

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Two English-only models in files/models/. Download to .tmp then rename.
 * whisper.cpp "en" lang tag covers both tiny.en and base.en models.
 */
object ModelDownloader {
    const val TINY = "ggml-tiny.en.bin" // ~75MB, clips <15s
    const val BASE = "ggml-base.en.bin" // ~142MB, clips >=15s
    const val LEGACY = "ggml-base.bin" // v0.1.x file, reused if present

    private const val HF = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    private val EXPECTED = mapOf(TINY to 75_000_000L, BASE to 142_000_000L)

    fun url(name: String): String = HF + name

    fun modelFile(ctx: Context, name: String = BASE): File =
        File(ctx.filesDir, "models/$name")

    /** v0.1.x left ggml-base.bin behind: valid whisper model, reuse it, no re-download. */
    fun legacyFile(ctx: Context): File = File(ctx.filesDir, "models/$LEGACY")

    fun hasLegacy(ctx: Context): Boolean {
        val f = legacyFile(ctx)
        return f.exists() && f.length() >= 120_000_000L
    }

    fun isReady(ctx: Context, name: String = BASE, minBytes: Long = minBytesFor(name)): Boolean {
        val f = modelFile(ctx, name)
        return f.exists() && f.length() >= minBytes
    }

    /** Anything usable: either voice pack file or the v0.1 file. */
    fun isPackReady(ctx: Context): Boolean =
        isReady(ctx, TINY) || isReady(ctx, BASE) || hasLegacy(ctx)

    fun packSizeMb(ctx: Context): Long {
        var total = 0L
        for (n in listOf(TINY, BASE, LEGACY)) {
            val f = modelFile(ctx, n)
            if (n == LEGACY && !f.exists()) continue
            if (f.exists()) total += f.length()
        }
        return total / 1_000_000
    }

    fun download(ctx: Context, name: String = BASE, onProgress: (pct: Int) -> Unit = {}) {
        val out = modelFile(ctx, name).apply { parentFile?.mkdirs() }
        val tmp = File(out.parent, "$name.tmp")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url(name)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                connect()
                if (responseCode !in 200..299) throw java.io.IOException("HTTP $responseCode")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: EXPECTED[name] ?: 142_000_000L
            conn.inputStream.use { inp ->
                tmp.outputStream().use { o ->
                    val b = ByteArray(256 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = inp.read(b)
                        if (n < 0) break
                        o.write(b, 0, n)
                        done += n
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != last) { last = pct; onProgress(pct) }
                    }
                }
            }
            if (!tmp.renameTo(out)) {
                out.delete()
                if (!tmp.renameTo(out)) throw java.io.IOException("rename failed")
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    private fun minBytesFor(name: String): Long = when (name) {
        TINY -> 60_000_000L
        else -> 120_000_000L
    }
}
