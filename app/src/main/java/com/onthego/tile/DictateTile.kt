package com.onthego.tile

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** Quick Settings tile: tap to toggle dictation (Android-side global key). */
@RequiresApi(Build.VERSION_CODES.N)
class DictateTile : TileService() {
    override fun onClick() {
        super.onClick()
        // TODO: bind to TranscriptionService toggle via broadcast
    }
}
