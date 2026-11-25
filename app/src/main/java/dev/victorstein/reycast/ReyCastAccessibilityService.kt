package dev.victorstein.reycast

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.victorstein.reycast.android.LinkServer

/** Owns TYPE_ACCESSIBILITY_OVERLAY windows, which DeX does not force-hide, and the link server. */
class ReyCastAccessibilityService : AccessibilityService() {
    private var linkServer: LinkServer? = null

    override fun onServiceConnected() {
        ReyCastOverlay.accessibilityService = this
        linkServer?.stop()
        linkServer = LinkServer(this).also { it.start() }
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
        linkServer?.stop()
        linkServer = null
        ReyCastOverlay.closeSession()
        if (ReyCastOverlay.accessibilityService === this) ReyCastOverlay.accessibilityService = null
    }
}
