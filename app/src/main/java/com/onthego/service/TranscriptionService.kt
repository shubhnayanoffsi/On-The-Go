package com.onthego.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onthego.audio.AudioCapture
import com.onthego.output.HistoryStore
import com.onthego.stt.ModelDownloader
import com.onthego.stt.WhisperEngine

/**
 * Single-instance foreground service. Owns mic + bubble state.
 * Tap bubble -> start, tap again -> stop, transcribe off-main-thread, output.
 */
class TranscriptionService : Service() {

    private val capture = AudioCapture()
    private val whisper = WhisperEngine()
    @Volatile private var recording = false
    @Volatile private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notif("Ready - tap bubble to dictate"))
        BubbleManager.show(this, ::onBubbleTap)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // single instance: ignore repeat starts while busy
        return START_STICKY
    }

    private fun onBubbleTap() {
        if (busy) return
        if (!recording) {
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
        } else {
            recording = false
            BubbleManager.setRecording(false)
            busy = true
            startForeground(1, notif("Transcribing..."))
            Thread {
                val pcm = capture.stop() // 16kHz mono s16
                val text = try {
                    whisper.transcribe(this, pcm)
                } catch (e: Exception) {
                    ""
                }
                runOnUiThreadFix {
                    if (text.isNotBlank()) {
                        if (com.onthego.Config.clipboard)
                            com.onthego.output.ClipboardHelper.copy(this, text)
                        ResultCard.show(this, text)
                        if (com.onthego.Config.historyFile) HistoryStore.append(this, text)
                    } else {
                        ResultCard.show(this, "Heard nothing. Try again.")
                    }
                    busy = false
                    startForeground(1, notif("Ready - tap bubble to dictate"))
                }
            }.start()
        }
    }

    private fun runOnUiThreadFix(fn: () -> Unit) {
        android.os.Handler(mainLooper).post { fn() }
    }

    private fun notif(msg: String): Notification {
        val ch = "otg"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "On The Go", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("On The Go").setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
    }
}
