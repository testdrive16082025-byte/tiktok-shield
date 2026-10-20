package com.oled.tiktokshield

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

class TikTokDetectService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableListOf<View>()
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isEditMode = false

    private val hideRunnable = Runnable {
        hideOverlays()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("OLED_PREFS", Context.MODE_PRIVATE)
    }

    private fun isTikTokApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("musically") || lower.contains("tiktok") || lower.contains("trill") || lower.contains("aweme")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isEditMode) return 

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: ""

            if (pkg == packageName) return

            val isRealScreen = className.contains("Activity") || 
                               className.contains("Launcher") || 
                               className.contains("Home")

            if (isTikTokApp(pkg)) {
                handler.removeCallbacks(hideRunnable)
                showOverlays()
            } else if (isRealScreen) {
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, 300)
            }
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_EDIT" -> {
                isEditMode = !isEditMode
                if (isEditMode) {
                    handler.removeCallbacks(hideRunnable)
                    showOverlays()
                }
                updateViewsMode()
            }
        }
        return START_STICKY
    }

    private fun showOverlays() {
        if (overlayViews.isNotEmpty()) return

        createBox(0, 50, 1800, 980, 250) // Низ
        createBox(1, 860, 800, 220, 850)  // Справа
        createBox(2, 200, 80, 680, 160)   // Верх
    }

    private fun createBox(id: Int, defaultX: Int, defaultY: Int, defaultW: Int, defaultH: Int) {
        val savedX = prefs.getInt("b_${id}_x", defaultX)
        val savedY = prefs.getInt("b_${id}_y", defaultY)
        val savedW = prefs.getInt("b_${id}_w", defaultW)
        val savedH = prefs.getInt("b_${id}_h", defaultH)

        val params = WindowManager.LayoutParams(
            savedW, savedH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val container = FrameLayout(this).apply {
            setBackgroundColor(if (isEditMode) Color.parseColor("#99FF0000") else Color.BLACK)
        }

        val resizeHandle = TextView(this).apply {
            text = " ⇲ "
            textSize = 20f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.YELLOW)
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            layoutParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }

        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initW = 0
            private var initH = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initW = params.width
                        initH = params.height
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = (initW + (event.rawX - touchX).toInt()).coerceAtLeast(100)
                        params.height = (initH + (event.rawY - touchY).toInt()).coerceAtLeast(80)
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        prefs.edit()
                            .putInt("b_${id}_w", params.width)
                            .putInt("b_${id}_h", params.height)
                            .apply()
                        return true
                    }
                }
                return false
            }
        })

        container.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isEditMode) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initX = params.x
                            initY = params.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initX + (event.rawX - touchX).toInt()
                            params.y = initY + (event.rawY - touchY).toInt()
                            try {
                                windowManager.updateViewLayout(container, params)
                            } catch (e: Exception) {}
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            prefs.edit().putInt("b_${id}_x", params.x).putInt("b_${id}_y", params.y).apply()
                            return true
                        }
                    }
                } else {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (abs(event.rawX - touchX) < 30 && abs(event.rawY - touchY) < 30) {
                                container.visibility = View.INVISIBLE
                                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                try {
                                    windowManager.updateViewLayout(container, params)
                                } catch (e: Exception) {}

                                container.postDelayed({
                                    if (container.isAttachedToWindow && !isEditMode) {
                                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                        container.visibility = View.VISIBLE
                                        try {
                                            windowManager.updateViewLayout(container, params)
                                        } catch (e: Exception) {}
                                    }
                                }, 4000)
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })

        container.addView(resizeHandle)
        try {
            windowManager.addView(container, params)
            overlayViews.add(container)
        } catch (e: Exception) {}
    }

    private fun updateViewsMode() {
        for (view in overlayViews) {
            val container = view as FrameLayout
            val handle = container.getChildAt(0)
            val params = container.layoutParams as WindowManager.LayoutParams

            container.visibility = View.VISIBLE
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            if (isEditMode) {
                container.setBackgroundColor(Color.parseColor("#99FF0000"))
                handle.visibility = View.VISIBLE
            } else {
                container.setBackgroundColor(Color.BLACK)
                handle.visibility = View.GONE
            }
            try {
                windowManager.updateViewLayout(container, params)
            } catch (e: Exception) {}
        }
    }

    private fun hideOverlays() {
        if (!isEditMode) {
            overlayViews.forEach {
                try {
                    if (it.isAttachedToWindow) windowManager.removeView(it)
                } catch (e: Exception) {}
            }
            overlayViews.clear()
        }
    }

    override fun onDestroy() {
        hideOverlays()
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
