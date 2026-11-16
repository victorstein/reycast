package dev.victorstein.reycast

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock

/** App-drawer entry point; Key Mapper uses [ToggleReceiver] instead, which DeX doesn't count as an app. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReyCastOverlay.toggle(applicationContext, SystemClock.uptimeMillis(), display)
        finish()
    }
}
