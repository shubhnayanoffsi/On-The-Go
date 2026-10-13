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
import com.onthego.stt.WhisperEngine

/**
 * Single-instance foreground service. Owns mic + bubble state.
 * Tap bubble -> start, tap again -> stop, transcribe, output.
 */
class TranscriptionService : Service() {

    private val capture = AudioCapture()
    private val whisper = WhisperEngine()
    private var recording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notif("Ready - tap bubble to dictate"))
        BubbleManager.show(this, ::onBubbleTap)
    }

    private fun onBubbleTap() {
        if (!recording) {
            recording = true
            BubbleManager.setRecording(true)
            capture.start()
        } else {
            recording = false
            BubbleManager.setRecording(false)
            val pcm = capture.stop() // 16kHz mono s16
            val text = whisper.transcribe(this, pcm) // loads/unloads ggml-base
            if (text.isNotBlank()) {
                com.onthego.output.ClipboardHelper.copy(this, text)
                ResultCard.show(this, text)
                HistoryStore.append(this, text)
            }
            startForeground(1, notif("Ready - tap bubble to dictate"))
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
}
