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

    private val tikTokPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill",
        "com.ss.android.ugc.aweme"
    )

    // Системные процессы, из-за которых плашки раньше моргали
    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.miui.securitycenter"
    )

    private val hideRunnable = Runnable {
        hideOverlays()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("OLED_PREFS", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isEditMode) return // В режиме настройки не прячем плашки

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (tikTokPackages.contains(pkg)) {
                handler.removeCallbacks(hideRunnable)
                showOverlays()
            } else if (!ignoredPackages.contains(pkg)) {
                // Защита от моргания: ждем 800 мс перед скрытием
                handler.removeCallbacks(hideRunnable)
                handler.postDelayed(hideRunnable, 800)
            }
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TOGGLE_EDIT") {
            isEditMode = !isEditMode
            if (isEditMode) {
                handler.removeCallbacks(hideRunnable)
                showOverlays()
            }
            updateViewsMode()
        }
        return START_STICKY
    }

    private fun showOverlays() {
        if (overlayViews.isNotEmpty()) return

        // 3 плашки: низ, колонка кнопок справа, верх
        createBox(0, 50, 1800, 980, 250)
        createBox(1, 860, 800, 220, 850)
        createBox(2, 200, 80, 680, 160)
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

        // Ручка для изменения размера (желтый квадратик в правом нижнем углу)
        val resizeHandle = TextView(this).apply {
            text = " ⇲ "
            textSize = 20f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.YELLOW)
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            val handleParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            layoutParams = handleParams
        }

        // Логика изменения РАЗМЕРА
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
                        windowManager.updateViewLayout(container, params)
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

        // Логика ПЕРЕМЕЩЕНИЯ и КЛИКА (скрытия на 4 сек)
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
                            windowManager.updateViewLayout(container, params)
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
                                // Исчезает на 4 секунды
                                container.visibility = View.INVISIBLE
                                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                windowManager.updateViewLayout(container, params)

                                container.postDelayed({
                                    if (container.isAttachedToWindow && !isEditMode) {
                                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                        container.visibility = View.VISIBLE
                                        windowManager.updateViewLayout(container, params)
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
        windowManager.addView(container, params)
        overlayViews.add(container)
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
            windowManager.updateViewLayout(container, params)
        }
    }

    private fun hideOverlays() {
        if (!isEditMode) {
            overlayViews.forEach {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            }
            overlayViews.clear()
        }
    }

    override fun onInterrupt() {}
}
