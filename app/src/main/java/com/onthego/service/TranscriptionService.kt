package com.onthego.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onthego.Config
import com.onthego.KeysStore
import com.onthego.ai.ApiFixer
import com.onthego.audio.AudioCapture
import com.onthego.output.ClipboardHelper
import com.onthego.output.HistoryStore
import com.onthego.stt.ApiTranscriber
import com.onthego.stt.ModelDownloader
import com.onthego.stt.WhisperEngine

/**
 * Single-instance foreground mic service. Bubble tap, Quick tile, and TOGGLE
 * intent all funnel into toggle(). Manual tap never auto-stops; 10-min cap only.
 */
class TranscriptionService : Service() {

    private val capture = AudioCapture()
    private val whisper = WhisperEngine()
    @Volatile private var recording = false
    @Volatile private var busy = false
    private var recStartMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var tickRun: Runnable? = null
    private var capRun: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Config.load(this)
        fg(notif("Ready - tap bubble to dictate"))
        BubbleManager.show(this, ::toggle)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggle()
        return START_STICKY
    }

    override fun onDestroy() {
        cancelTick()
        cancelCap()
        try { whisper.release() } catch (_: Exception) {}
        try { BubbleManager.hide() } catch (_: Exception) {}
        super.onDestroy()
    }

    fun toggle() {
        if (busy) return
        if (!recording) startRec() else stopAndTranscribe()
    }

    private fun startRec() {
        if (needsLocal() && !localReady()) {
            downloadOnce()
            return
        }
        try {
            capture.start()
        } catch (e: SecurityException) {
            ResultCard.show(this, "Mic permission needed.") { toggle() }
            return
        } catch (e: Exception) {
            ResultCard.show(this, "Could not start mic: ${e.message}") { toggle() }
            return
        }
        recording = true
        recStartMs = SystemClock.elapsedRealtime()
        BubbleManager.setRecording(true)
        fg(notif("Listening... 0s"))
        scheduleTick()
        scheduleCap()
    }

    private fun stopAndTranscribe() {
        if (!recording) return
        recording = false
        cancelTick()
        cancelCap()
        BubbleManager.setRecording(false)
        busy = true
        fg(notif("Transcribing..."))
        val pcm = try { capture.stop() } catch (_: Exception) { ShortArray(0) }
        val recSec = ((SystemClock.elapsedRealtime() - recStartMs) / 1000).coerceAtLeast(0)
        Thread {
            val t0 = SystemClock.elapsedRealtime()
            var engine = "local"
            var text = ""
            var err: String? = null
            val info = capture.info()
            try {
                if (info.samples == 0) {
                    err = "Mic captured nothing. Check permission, close other recorder apps, retry."
                } else {
                    val sttKey = try { KeysStore.getSttKey(this) } catch (_: Exception) { "" }
                    engine = when (Config.engineMode) {
                        "offline" -> "local"
                        "api" -> "api"
                        else -> if (sttKey.isNotBlank()) "api" else "local"
                    }
                    text = if (engine == "api") {
                        if (sttKey.isBlank()) {
                            err = "Missing API key. Add it in Settings."
                            ""
                        } else {
                            ApiTranscriber.transcribe(pcm, Config.sttProvider, Config.sttEndpoint, sttKey)
                        }
                    } else {
                        whisper.transcribe(this, pcm, Config.language)
                    }
                    if (text.isBlank() && err == null) {
                        err = when (whisper.lastReason) {
                            "no-model" -> "Model missing. Download it in Settings."
                            "load-fail" -> "Model file broken. Delete it in Settings and download again."
                            "engine-crash" -> "Speech engine crashed. Restart the service and retry."
                            else -> "Heard only silence (${info.seconds.toInt()}s, peak ${info.peakPct}%). Speak closer and retry."
                        }
                    }
                }
                if (text.isNotBlank() && Config.aiFixMode == "api") {
                    val fixKey = try { KeysStore.getFixKey(this) } catch (_: Exception) { "" }
                    text = ApiFixer.apply("api", text, Config.fixProvider, Config.fixEndpoint, fixKey)
                }
            } catch (e: Exception) {
                if (err == null) err = "Transcription failed: ${e.message}"
                text = ""
            }
            val inferMs = SystemClock.elapsedRealtime() - t0
            Log.d("OTG-TIME", "rec=${recSec}s mic=${info.samples}@${info.peakPct}% err=${info.readErrors} infer=${inferMs}ms engine=$engine chars=${text.length} reason=${whisper.lastReason}")
            val out = text
            val outErr = err
            handler.post {
                if (out.isNotBlank()) {
                    try { ClipboardHelper.copy(this, out) } catch (_: Exception) {}
                    ResultCard.show(this, out) { toggle() }
                    try { HistoryStore.append(this, out) } catch (_: Exception) {}
                } else {
                    ResultCard.show(this, outErr ?: "Heard nothing. Try again.") { toggle() }
                }
                busy = false
                fg(notif("Ready - tap bubble to dictate"))
            }
        }.start()
    }

    private fun needsLocal(): Boolean = when (Config.engineMode) {
        "offline" -> true
        "api" -> false
        else -> try { KeysStore.getSttKey(this).isBlank() } catch (_: Exception) { true }
    }

    private fun localReady(): Boolean = try {
        ModelDownloader.isReady(this, ModelDownloader.TINY) ||
            ModelDownloader.isReady(this, ModelDownloader.BASE) ||
            ModelDownloader.hasLegacy(this)
    } catch (_: Exception) {
        false
    }

    private fun downloadOnce() {
        ResultCard.show(this, "Downloading base model, one time only...")
        busy = true
        Thread {
            try {
                if (!ModelDownloader.isReady(this, ModelDownloader.TINY)) {
                    ModelDownloader.download(this, ModelDownloader.TINY) {}
                }
                if (!ModelDownloader.isReady(this, ModelDownloader.BASE)) {
                    ModelDownloader.download(this, ModelDownloader.BASE) {}
                }
            } catch (e: Exception) {
                handler.post { ResultCard.show(this, "Model download failed: ${e.message}") }
            } finally {
                busy = false
            }
        }.start()
    }

    private fun scheduleTick() {
        cancelTick()
        val r = object : Runnable {
            override fun run() {
                if (!recording) return
                val s = ((SystemClock.elapsedRealtime() - recStartMs) / 1000).toInt()
                fg(notif("Listening... ${s}s"))
                handler.postDelayed(this, 1000)
            }
        }
        tickRun = r
        handler.postDelayed(r, 1000)
    }

    private fun cancelTick() {
        tickRun?.let { handler.removeCallbacks(it) }
        tickRun = null
    }

    private fun scheduleCap() {
        cancelCap()
        val r = Runnable { if (recording) stopAndTranscribe() }
        capRun = r
        handler.postDelayed(r, 600_000)
    }

    private fun cancelCap() {
        capRun?.let { handler.removeCallbacks(it) }
        capRun = null
    }

    private fun fg(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, n)
            }
        } catch (_: Exception) {
            try { startForeground(1, n) } catch (_: Exception) {}
        }
    }

    private fun notif(msg: String): Notification {
        val ch = "otg"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "On The Go", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("On The Go").setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
    }

    companion object {
        const val ACTION_TOGGLE = "com.onthego.TOGGLE"
    }
}
