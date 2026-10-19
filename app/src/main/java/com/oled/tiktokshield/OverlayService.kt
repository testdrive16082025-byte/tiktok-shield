package com.oled.tiktokshield

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()
    private lateinit var prefs: SharedPreferences

    // Время скрытия плашки при нажатии (4 секунды)
    private val HIDE_DURATION_MS = 4000L 

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_HIDE = "ACTION_HIDE"
        const val ACTION_TOGGLE_EDIT = "ACTION_TOGGLE_EDIT"
        var isEditMode = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("OLED_PREFS", Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlays()
            ACTION_HIDE -> hideOverlays()
            ACTION_TOGGLE_EDIT -> {
                isEditMode = !isEditMode
                updateOverlayTouchMode()
            }
        }
        return START_STICKY
    }

    private fun showOverlays() {
        if (overlayViews.isNotEmpty()) return

        // 3 плашки по умолчанию
        createBlackBox(0, 50, 1800, 980, 250) // Низ (навигация)
        createBlackBox(1, 880, 800, 200, 850)  // Справа (лайки, комменты)
        createBlackBox(2, 200, 80, 680, 160)   // Верх (вкладки)
    }

    private fun createBlackBox(id: Int, defaultX: Int, defaultY: Int, w: Int, h: Int) {
        val savedX = prefs.getInt("box_${id}_x", defaultX)
        val savedY = prefs.getInt("box_${id}_y", defaultY)

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val box = View(this).apply {
            setBackgroundColor(if (isEditMode) Color.parseColor("#77FF0000") else Color.BLACK)
            tag = id
        }

        box.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isEditMode) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(box, params)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            prefs.edit().putInt("box_${id}_x", params.x).putInt("box_${id}_y", params.y).apply()
                            return true
                        }
                    }
                } else {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = abs(event.rawX - initialTouchX)
                            val diffY = abs(event.rawY - initialTouchY)
                            if (diffX < 30 && diffY < 30) {
                                temporarilyHide(box, params)
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })

        windowManager.addView(box, params)
        overlayViews.add(box)
    }

    private fun temporarilyHide(view: View, params: WindowManager.LayoutParams) {
        view.visibility = View.INVISIBLE
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windowManager.updateViewLayout(view, params)

        view.removeCallbacks(null)
        view.postDelayed({
            if (view.isAttachedToWindow && !isEditMode) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                view.visibility = View.VISIBLE
                windowManager.updateViewLayout(view, params)
            }
        }, HIDE_DURATION_MS)
    }

    private fun updateOverlayTouchMode() {
        for (view in overlayViews) {
            val params = view.layoutParams as WindowManager.LayoutParams
            view.removeCallbacks(null)
            view.visibility = View.VISIBLE

            if (isEditMode) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                view.setBackgroundColor(Color.parseColor("#77FF0000"))
            } else {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                view.setBackgroundColor(Color.BLACK)
            }
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun hideOverlays() {
        if (!isEditMode) {
            overlayViews.forEach {
                it.removeCallbacks(null)
                if (it.isAttachedToWindow) windowManager.removeView(it)
            }
            overlayViews.clear()
        }
    }

    override fun onDestroy() {
        hideOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
