package dev.victorstein.reycast

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

class OverlayRoot(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    var onKey: (KeyEvent) -> Boolean = { false }
    var onFocusLost: () -> Unit = {}
    private var hasHadFocus = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = onKey(event) || super.dispatchKeyEvent(event)

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) hasHadFocus = true else if (hasHadFocus) onFocusLost()
    }
}
