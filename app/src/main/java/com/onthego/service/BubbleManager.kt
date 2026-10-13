package com.onthego.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.WindowManager

/** Floating bubble. Grey idle, red recording. Position/size from Config. */
object BubbleManager {
    fun canDraw(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true

    fun show(ctx: Context, onTap: () -> Unit) {
        // TODO v0.1: WindowManager TYPE_APPLICATION_OVERLAY view + drag to 4 corners
    }

    fun setRecording(rec: Boolean) {
        // TODO v0.1: swap color idle #444444 / recording #FF3333
    }
}
