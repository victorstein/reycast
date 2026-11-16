package dev.victorstein.reycast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReyCastOverlay.toggle(context.applicationContext, SystemClock.uptimeMillis())
    }
}
