package com.onthego.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.onthego.service.TranscriptionService

/** Quick Settings tile: tap toggles dictation (Android-side global key). */
@RequiresApi(Build.VERSION_CODES.N)
class DictateTile : TileService() {
    override fun onClick() {
        super.onClick()
        val i = Intent(this, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_TOGGLE
        }
        startForegroundService(i)
    }
}
