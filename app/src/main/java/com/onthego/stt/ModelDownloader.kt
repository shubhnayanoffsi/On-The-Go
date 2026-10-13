package com.onthego.stt

import android.content.Context
import java.io.File
import java.net.URL

/**
 * First-run model download. ggml-base.bin ~142MB to files/models/.
 * Resume skipped in v0.1 (single GET + progress). SHA check optional.
 */
object ModelDownloader {
    const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"

    fun modelFile(ctx: Context): File =
        File(ctx.filesDir, "models/ggml-base.bin")

    fun isReady(ctx: Context, minBytes: Long = 100_000_000L): Boolean {
        val f = modelFile(ctx)
        return f.exists() && f.length() > minBytes
    }

    fun download(ctx: Context, onProgress: (pct: Int) -> Unit) {
        val out = modelFile(ctx).apply { parentFile?.mkdirs() }
        val tmp = File(out.parent, "ggml-base.bin.tmp")
        URL(MODEL_URL).openStream().use { inp ->
            tmp.outputStream().use { o ->
                val b = ByteArray(256 * 1024)
                var done = 0L
                while (true) {
                    val n = inp.read(b)
                    if (n < 0) break
                    o.write(b, 0, n)
                    done += n
                    onProgress(((done * 100) / 145_000_000L).toInt().coerceIn(0, 100))
                }
            }
        }
        tmp.renameTo(out)
    }
}
