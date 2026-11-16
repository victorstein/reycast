package dev.victorstein.reycast

import android.app.Activity
import android.os.Bundle

/** Invisible entry point: Key Mapper and the app drawer start this, it toggles the overlay and exits. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReyCastOverlay.toggle(applicationContext)
        finish()
    }
}
