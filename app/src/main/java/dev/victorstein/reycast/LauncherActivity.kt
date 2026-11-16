package dev.victorstein.reycast

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager

class LauncherActivity : Activity() {
    private var hasHadFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        setContentView(R.layout.activity_launcher)
        placeWindow()
    }

    private fun placeWindow() {
        val screen = windowManager.maximumWindowMetrics.bounds
        window.setLayout((screen.width() * 0.40).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (screen.height() * 0.15).toInt()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_DOWN) close()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        close()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hasHadFocus = true else if (hasHadFocus) close()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (!isTopResumedActivity && hasHadFocus) close()
    }

    private fun close() {
        if (isFinishing) return
        // finishAndRemoveTask, not finish: a surviving task would be brought forward on the next
        // summon and could drag the user back to the desk it was opened on.
        finishAndRemoveTask()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
