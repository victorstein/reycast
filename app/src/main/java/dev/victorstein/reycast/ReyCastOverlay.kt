package dev.victorstein.reycast

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

/**
 * The panel is an overlay window, not an activity: in DeX an activity is a freeform task that is either
 * pinned to the left edge or, when full-screen, gets a caption bar. An overlay has neither.
 */
object ReyCastOverlay {
    private var root: OverlayRoot? = null

    fun toggle(context: Context) {
        if (root != null) hide(context) else show(context)
    }

    private fun show(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.needs_overlay_permission, Toast.LENGTH_LONG).show()
            return
        }
        val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_panel, null) as OverlayRoot
        val windowManager = context.getSystemService(WindowManager::class.java)
        val screen = windowManager.maximumWindowMetrics.bounds
        val panel = view.findViewById<View>(R.id.panel)
        panel.layoutParams = FrameLayout.LayoutParams(
            (screen.width() * 0.40).toInt(),
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            topMargin = (screen.height() * 0.15).toInt()
        }
        view.setOnClickListener { hide(context) }
        view.onFocusLost = { hide(context) }
        view.onKey = { event ->
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (event.action == KeyEvent.ACTION_DOWN) hide(context)
                true
            } else {
                false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, params)
        root = view
        view.findViewById<EditText>(R.id.query).requestFocus()
    }

    private fun hide(context: Context) {
        val view = root ?: return
        root = null
        context.getSystemService(WindowManager::class.java).removeView(view)
    }
}
