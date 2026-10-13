package com.onthego.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onthego.service.TranscriptionService

/** Autostart after boot (like OpenWhispr autostart). Respects user toggle. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: check Config autostart_on_boot before start
            ctx.startForegroundService(Intent(ctx, TranscriptionService::class.java))
        }
    }
}
