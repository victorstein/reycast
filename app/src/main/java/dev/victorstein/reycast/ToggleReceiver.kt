package dev.victorstein.reycast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestedAtMs = SystemClock.uptimeMillis()
        if (intent.action == ACTION_HINTS) {
            ReyCastOverlay.toggleHints(context.applicationContext, requestedAtMs)
        } else {
            ReyCastOverlay.toggle(context.applicationContext, requestedAtMs)
        }
    }

    companion object {
        const val ACTION_HINTS = "dev.victorstein.reycast.HINTS"
    }
}
