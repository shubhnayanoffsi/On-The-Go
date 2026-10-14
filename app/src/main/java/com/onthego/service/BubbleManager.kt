package com.onthego.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.onthego.Config
import kotlin.math.abs

/**
 * Real floating bubble. Grey idle (#444444), red recording (#FF3333).
 * Free-drag anywhere, tap to toggle dictation.
 * Raw x/y persist in Config.bubbleX/bubbleY (-1 = bottom-right default).
 */
object BubbleManager {
    private var wm: WindowManager? = null
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var bg: GradientDrawable? = null
    private var onTap: (() -> Unit)? = null

    fun canDraw(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true

    fun isShown(): Boolean = view != null

    fun show(ctx: Context, tap: () -> Unit) {
        if (view != null || !canDraw(ctx)) return
        onTap = tap
        val app = ctx.applicationContext
        wm = app.getSystemService(WindowManager::class.java)

        val sizeDp = if (Config.bubbleSize == "small") 48 else 64
        val sizePx = (sizeDp * app.resources.displayMetrics.density).toInt()

        bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF444444.toInt())
        }
        val v = View(app).apply { background = bg }

        val p = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val dm: DisplayMetrics = app.resources.displayMetrics
        if (Config.bubbleX >= 0 && Config.bubbleY >= 0) {
            p.x = Config.bubbleX.coerceIn(0, (dm.widthPixels - sizePx).coerceAtLeast(0))
            p.y = Config.bubbleY.coerceIn(0, (dm.heightPixels - sizePx).coerceAtLeast(0))
        } else {
            cornerXY("bottom_right", dm.widthPixels, dm.heightPixels, sizePx).let {
                p.x = it.first; p.y = it.second
            }
        }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    p.x = startX + dx; p.y = startY + dy
                    wm?.updateViewLayout(v, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onTap?.invoke()
                    } else {
                        // free position: clamp on-screen + persist raw x/y
                        val dm2: DisplayMetrics = app.resources.displayMetrics
                        p.x = p.x.coerceIn(0, (dm2.widthPixels - sizePx).coerceAtLeast(0))
                        p.y = p.y.coerceIn(0, (dm2.heightPixels - sizePx).coerceAtLeast(0))
                        wm?.updateViewLayout(v, p)
                        Config.bubbleX = p.x
                        Config.bubbleY = p.y
                        Config.save(app)
                    }
                    true
                }
                else -> false
            }
        }

        view = v; params = p
        wm?.addView(v, p)
    }

    fun setRecording(rec: Boolean) {
        bg?.setColor(if (rec) 0xFFFF3333.toInt() else 0xFF444444.toInt())
        view?.invalidate()
    }

    fun hide() {
        try { wm?.removeView(view) } catch (_: Exception) {}
        view = null; params = null; wm = null
    }

    private fun cornerXY(corner: String, w: Int, h: Int, s: Int, m: Int = 32): Pair<Int, Int> =
        when (corner) {
            "top_left" -> Pair(m, m)
            "top_right" -> Pair(w - s - m, m)
            "bottom_left" -> Pair(m, h - s - m * 4)
            else -> Pair(w - s - m, h - s - m * 4) // bottom_right
        }
}
