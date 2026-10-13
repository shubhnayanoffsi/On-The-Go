package com.onthego.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL

/**
 * Normal OTA: read latest.json from GitHub Releases, compare semver,
 * download APK to cache, fire install intent (REQUEST_INSTALL_PACKAGES).
 */
object OtaUpdater {
    const val LATEST_URL =
        "https://github.com/shubhnayanoffsi/On-The-Go/releases/latest/download/latest.json"

    data class Latest(val version: String, val apk: String, val note: String = "")

    fun isNewer(installed: String, latest: String): Boolean {
        fun parts(v: String) = v.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(installed); val b = parts(latest)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (y != x) return y > x
        }
        return false
    }

    fun parseLatest(json: String): Latest {
        fun f(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
        return Latest(f("version"), f("apk"), f("note"))
    }

    fun apkUrl(l: Latest): String =
        "https://github.com/shubhnayanoffsi/On-The-Go/releases/latest/download/${l.apk}"

    /** Full check + download. Status lines go to onStatus. Call off main thread. */
    fun checkAndUpdate(ctx: Context, onStatus: (String) -> Unit) {
        val installed = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1.0"
        } catch (_: Exception) { "0.1.0" }
        onStatus("Checking... (installed $installed)")
        val latest = parseLatest(URL(LATEST_URL).readText())
        if (latest.version.isBlank() || latest.apk.isBlank()) {
            onStatus("Update file unreadable."); return
        }
        if (!isNewer(installed, latest.version)) {
            onStatus("You are on latest ($installed)."); return
        }
        onStatus("Found ${latest.version}. Downloading...")
        val dir = File(ctx.cacheDir, "ota").apply { mkdirs() }
        val out = File(dir, latest.apk)
        URL(apkUrl(latest)).openStream().use { inp ->
            out.outputStream().use { o -> inp.copyTo(o) }
        }
        onStatus("Downloaded. Opening installer...")
        installApk(ctx, out)
    }

    fun installApk(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
