package com.custom.gemini.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyTriggerAccessibilityService : AccessibilityService() {

    private var lastVolumeDownTime: Long = 0
    private val DOUBLE_PRESS_INTERVAL_MS = 450L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVolumeDownTime < DOUBLE_PRESS_INTERVAL_MS) {
                // Double press detected!
                lastVolumeDownTime = 0
                launchAssistantOverlay()
                return true // Consume key event
            } else {
                lastVolumeDownTime = currentTime
            }
        }
        return super.onKeyEvent(event)
    }

    private fun launchAssistantOverlay() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TOGGLE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
