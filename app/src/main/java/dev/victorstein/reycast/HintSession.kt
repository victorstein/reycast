package dev.victorstein.reycast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import dev.victorstein.reycast.android.HintTarget
import dev.victorstein.reycast.core.HintFilter
import dev.victorstein.reycast.core.HintLabels
import kotlin.math.abs

class HintSession(
    private val service: AccessibilityService,
    private val targets: List<HintTarget>,
    private val requestedAtMs: Long,
    private val onClosed: () -> Unit,
) : Session {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val root = OverlayRoot(ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault), null)
    private val labels = HintLabels.assign(targets.size)
    private val filter = HintFilter(labels)
    private val badges = labels.map(::createBadge)
    private var closed = false
    private var shiftPressedDuringSession = false

    fun open() {
        root.onKey = ::handleKey
        root.onFocusLost = ::close
        badges.forEach(root::addView)
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                placeBadges()
                Log.i(TAG, "hints shown: ${targets.size} +${SystemClock.uptimeMillis() - requestedAtMs} ms")
            }
        })
        windowManager.addView(root, overlayParams())
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            // parent is set inside addView; isAttachedToWindow stays false until the first traversal.
            if (root.parent != null) windowManager.removeView(root)
        } finally {
            onClosed()
        }
    }

    // Not touchable: taps (the user's and the fallback gesture) reach the app beneath, and a tap there
    // moves focus away, which closes the session.
    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        fitInsetsTypes = 0
        title = "ReyCast hints"
    }

    private fun createBadge(label: String) = TextView(root.context).apply {
        text = label.uppercase()
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setBackgroundResource(R.drawable.hint_badge_bg)
        val padX = dp(4)
        val padY = dp(1)
        setPadding(padX, padY, padX, padY)
        visibility = View.INVISIBLE
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()

    private fun placeBadges() {
        val origin = IntArray(2).also(root::getLocationOnScreen)
        val placed = ArrayList<PointF>()
        targets.forEachIndexed { index, target ->
            val badge = badges[index]
            val width = badge.width.toFloat()
            val height = badge.height.toFloat()
            var x = (target.rect.left - origin[0]).toFloat()
            var y = (target.rect.top - origin[1]).toFloat()
            if (placed.any { abs(it.x - x) < width && abs(it.y - y) < height }) {
                x = target.rect.exactCenterX() - origin[0] - width / 2
                y = target.rect.exactCenterY() - origin[1] - height / 2
            }
            x = x.coerceIn(0f, maxOf(0f, root.width - width))
            y = y.coerceIn(0f, maxOf(0f, root.height - height))
            badge.translationX = x
            badge.translationY = y
            placed += PointF(x, y)
        }
        render()
    }

    private fun render() {
        val typedLength = filter.typed.length
        badges.forEachIndexed { index, badge ->
            if (!filter.isVisible(index)) {
                badge.visibility = View.INVISIBLE
                return@forEachIndexed
            }
            badge.visibility = View.VISIBLE
            badge.text = SpannableString(labels[index].uppercase()).apply {
                if (typedLength > 0) {
                    setSpan(ForegroundColorSpan(TYPED_COLOR), 0, typedLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    // Matched by keyCode, not by character: the summon chord's Alt may still be held, and Alt+letter
    // produces no character.
    private fun handleKey(event: KeyEvent): Boolean {
        if (closed || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> close()
            KeyEvent.KEYCODE_DEL -> {
                filter.backspace()
                render()
            }
            // Shift may still be held from the summon chord; only a Shift pressed while hints are up long-presses.
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftPressedDuringSession = true
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val match = filter.type('a' + (event.keyCode - KeyEvent.KEYCODE_A))
                val longPress = shiftPressedDuringSession && event.isShiftPressed
                if (match == null) render() else act(targets[match], longPress)
            }
        }
        return true
    }

    // shortcut: refresh() and performAction() are blocking binder calls on the main thread (up to ~5 s if the
    // target app hangs); move act() to the background executor if that shows up in use.
    private fun act(target: HintTarget, longPress: Boolean) {
        close()
        val node = target.node
        if (!node.refresh()) return toast(R.string.hint_target_gone)
        val action = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        if (node.performAction(action)) {
            if (target.editable) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return
        }
        // Clipped to what was visible when labelled: an unclipped centre can be off screen (StrokeDescription
        // throws on negative coordinates) or under another window.
        val bounds = Rect().also(node::getBoundsInScreen)
        if (!bounds.intersect(target.rect)) return toast(R.string.hint_target_gone)
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (longPress) LONG_PRESS_MS else TAP_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) = toast(R.string.hint_cannot_click)
        }, null)
        if (!dispatched) toast(R.string.hint_cannot_click)
    }

    private fun toast(message: Int) {
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "ReyCast"
        const val TAP_MS = 50L
        const val LONG_PRESS_MS = 600L
        val TYPED_COLOR = Color.argb(110, 0, 0, 0)
    }
}
