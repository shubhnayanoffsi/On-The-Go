package com.onthego.output

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Forever history. Monthly split so file stays fast:
 * files/history/history-YYYY-MM.md, lines: - HH:MM text
 */
object HistoryStore {
    fun append(ctx: Context, text: String) {
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val dir = File(ctx.filesDir, "history").apply { mkdirs() }
        File(dir, "history-$month.md").appendText("- $time $text\n")
    }
}
