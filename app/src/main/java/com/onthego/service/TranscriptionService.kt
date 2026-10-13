package com.onthego.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onthego.Config
import com.onthego.ai.ApiFixer
import com.onthego.audio.AudioCapture
import com.onthego.output.HistoryStore
import com.onthego.stt.ModelDownloader
import com.onthego.stt.WhisperEngine

/**
 * Single-instance foreground service. Owns mic + bubble state.
 * Bubble tap, Quick tile, or TOGGLE intent all funnel into toggle().
 */
class TranscriptionService : Service() {

    private val capture = AudioCapture()
    private val whisper = WhisperEngine()
    @Volatile private var recording = false
    @Volatile private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Config.load(this)
        startForeground(1, notif("Ready - tap bubble to dictate"))
        BubbleManager.show(this, ::toggle)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) toggle()
        return START_STICKY // single instance: repeat starts reuse this service
    }

    fun toggle() {
        if (busy) return
        if (!recording) startRec() else stopAndTranscribe()
    }

    private fun startRec() {
        if (!ModelDownloader.isReady(this)) {
            ResultCard.show(this, "Downloading base model, one time only...")
            busy = true
            Thread {
                try {
                    ModelDownloader.download(this) { /* TODO: progress on bubble */ }
                } catch (e: Exception) {
                    val msg = "Model download failed: ${e.message}"
                    android.os.Handler(mainLooper).post { ResultCard.show(this, msg) }
                } finally {
                    busy = false
                }
            }.start()
            return
        }
        recording = true
        BubbleManager.setRecording(true)
        capture.start()
    }

    private fun stopAndTranscribe() {
        recording = false
        BubbleManager.setRecording(false)
        busy = true
        startForeground(1, notif("Transcribing..."))
        Thread {
            val pcm = capture.stop() // 16kHz mono s16
            var text = try {
                whisper.transcribe(this, pcm, Config.language)
            } catch (e: Exception) {
                ""
            }
            text = ApiFixer.apply(Config.aiFixMode, text)
            val done = text
            android.os.Handler(mainLooper).post {
                if (done.isNotBlank()) {
                    if (Config.clipboard)
                        com.onthego.output.ClipboardHelper.copy(this, done)
                    ResultCard.show(this, done) { toggle() }
                    if (Config.historyFile) HistoryStore.append(this, done)
                } else {
                    ResultCard.show(this, "Heard nothing. Try again.") { toggle() }
                }
                busy = false
                startForeground(1, notif("Ready - tap bubble to dictate"))
            }
        }.start()
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
