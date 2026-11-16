package dev.victorstein.reycast

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Exists only to own TYPE_ACCESSIBILITY_OVERLAY windows, which DeX does not force-hide. */
class ReyCastAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        ReyCastOverlay.accessibilityService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        ReyCastOverlay.closeSession()
        if (ReyCastOverlay.accessibilityService === this) ReyCastOverlay.accessibilityService = null
    }
}
