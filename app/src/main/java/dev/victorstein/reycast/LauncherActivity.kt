package dev.victorstein.reycast

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager

/** Invisible entry point: Key Mapper and the app drawer start this, it toggles the overlay and exits. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A window context for the display this was launched on, so the overlay follows DeX onto an
        // external monitor instead of always opening on the tablet screen.
        val overlayContext = applicationContext.createWindowContext(
            display,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            null,
        )
        ReyCastOverlay.toggle(overlayContext, SystemClock.uptimeMillis())
        finish()
    }
}
