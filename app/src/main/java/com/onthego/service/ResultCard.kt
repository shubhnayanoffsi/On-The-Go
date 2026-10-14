package com.onthego.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.onthego.output.ClipboardHelper

/**
 * Result card overlay. Text + [Copy again] [Close]. Always shown, 5s auto-hide.
 * Copy + history already happened in TranscriptionService.
 */
object ResultCard {
    private const val CARD_MS = 5000L
    private var wm: WindowManager? = null
    private var view: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideRun: Runnable? = null

    fun show(ctx: Context, text: String, onRetry: (() -> Unit)? = null) {
        if (!BubbleManager.canDraw(ctx)) return
        val app = ctx.applicationContext
        dismiss()

        wm = app.getSystemService(WindowManager::class.java)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        val pad = (16 * app.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 2)
            setBackgroundColor(0xEE222222.toInt())
        }
        layout.addView(TextView(app).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        })
        val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(app).apply {
            this.text = "Copy again"
            setOnClickListener { ClipboardHelper.copy(app, text) }
        })
        if (onRetry != null) {
            row.addView(Button(app).apply {
                this.text = "Retry"
                setOnClickListener { dismiss(); onRetry() }
            })
        }
        row.addView(Button(app).apply {
            this.text = "Close"
            setOnClickListener { dismiss() }
        })
        layout.addView(row)

        view = layout
        wm?.addView(layout, p)

        hideRun = Runnable { dismiss() }
        handler.postDelayed(hideRun!!, CARD_MS)
    }

    fun dismiss() {
        hideRun?.let { handler.removeCallbacks(it) }
        hideRun = null
        try { wm?.removeView(view) } catch (_: Exception) {}
        view = null; wm = null
    }
}
