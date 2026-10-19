package com.oled.tiktokshield

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class TikTokDetectService : AccessibilityService() {

    private val tikTokPackages = setOf(
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            val intent = Intent(this, OverlayService::class.java)
            if (tikTokPackages.contains(packageName)) {
                intent.action = OverlayService.ACTION_SHOW
                startService(intent)
            } else {
                intent.action = OverlayService.ACTION_HIDE
                startService(intent)
            }
        }
    }

    override fun onInterrupt() {}
}
