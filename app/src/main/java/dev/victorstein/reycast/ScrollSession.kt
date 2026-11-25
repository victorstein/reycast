package dev.victorstein.reycast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import dev.victorstein.reycast.core.ScrollKeys
import dev.victorstein.reycast.core.ScrollKeys.Command

/**
 * Keyboard scrolling: starts on the first area (the focused app's), Tab / Shift+Tab move between areas, and
 * j/k, d/u, gg/G, h/l scroll with injected swipes. Areas are not picked by letter: the labels would use the
 * scroll keys. The overlay keeps keyboard focus through the swipes (checked on the tablet in DeX), so keys
 * keep coming here until Esc.
 */
class ScrollSession(
    private val service: AccessibilityService,
    private val areas: List<Rect>,
    private val onClosed: () -> Unit,
) : Session {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val root = OverlayRoot(ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault), null)
    private val keys = ScrollKeys()
    private val border = SpinningBorder(dp(BORDER_DP).toFloat(), dp(CORNER_DP).toFloat(), fillColor = null)
    private val frame = View(root.context).apply {
        background = border
        visibility = View.INVISIBLE
    }
    private val help = TextView(root.context).apply {
        text = if (areas.size > 1) "$HELP  ·  Tab next area" else HELP
        setTextColor(ReyCastColors.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = SpinningBorder(dp(1).toFloat(), dp(8).toFloat())
        setPadding(dp(12), dp(6), dp(12), dp(6))
        visibility = View.INVISIBLE
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private val origin = IntArray(2)
    private var spinner: ValueAnimator? = null
    private var active = 0
    private var closed = false
    private var shiftPressedDuringSession = false
    private var gestureInFlight = false

    fun open() {
        root.onKey = ::handleKey
        root.onFocusLost = ::close
        root.addView(frame)
        root.addView(help)
        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                root.getLocationOnScreen(origin)
                select(0)
            }
        })
        windowManager.addView(root, overlayParams())
    }

    override fun close() {
        if (closed) return
        closed = true
        spinner?.cancel()
        try {
            if (root.parent != null) windowManager.removeView(root)
        } finally {
            onClosed()
        }
    }

    private fun select(index: Int) {
        active = index
        val area = areas[index]
        frame.layoutParams = FrameLayout.LayoutParams(area.width(), area.height())
        frame.translationX = (area.left - origin[0]).toFloat()
        frame.translationY = (area.top - origin[1]).toFloat()
        frame.visibility = View.VISIBLE
        if (spinner == null) spinner = border.spin()
        help.visibility = View.INVISIBLE
        help.post {
            help.translationX = area.exactCenterX() - origin[0] - help.width / 2f
            help.translationY = (area.bottom - origin[1] - help.height - dp(HELP_MARGIN_DP)).toFloat()
            help.visibility = View.VISIBLE
        }
    }

    // Matched by keyCode, as in hint mode: the summon chord's modifiers may still be held.
    private fun handleKey(event: KeyEvent): Boolean {
        // Alt/Ctrl/Cmd chords belong to the system: Alt+Tab's later Tabs used to land here and switch areas.
        if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed || KeyEvent.isModifierKey(event.keyCode) &&
            event.keyCode != KeyEvent.KEYCODE_SHIFT_LEFT && event.keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return false
        }
        if (closed || event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER -> close()
            // Shift may still be held from the summon chord; only one pressed in scroll mode makes G.
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftPressedDuringSession = true
            else -> scroll(event)
        }
        return true
    }

    private fun scroll(event: KeyEvent) {
        val held = event.repeatCount > 0
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_TAB -> {
                val step = if (event.isShiftPressed && shiftPressedDuringSession) areas.size - 1 else 1
                if (!held) select((active + step) % areas.size)
                null
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> Command.DOWN
            KeyEvent.KEYCODE_DPAD_UP -> Command.UP
            KeyEvent.KEYCODE_DPAD_LEFT -> Command.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Command.RIGHT
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> Command.HALF_DOWN
            KeyEvent.KEYCODE_PAGE_UP -> Command.HALF_UP
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val letter = 'a' + (event.keyCode - KeyEvent.KEYCODE_A)
                // Holding g must not turn into gg; holding the step keys repeats them.
                if (held && letter == 'g') null else keys.type(letter, shiftPressedDuringSession && event.isShiftPressed)
            }
            else -> null
        } ?: return
        run(command)
    }

    private fun run(command: Command) {
        val area = areas[active]
        val height = area.height().toFloat()
        val width = area.width().toFloat()
        // A finger moving up scrolls the content down.
        when (command) {
            Command.DOWN -> drag(0f, -height * STEP_SHARE)
            Command.UP -> drag(0f, height * STEP_SHARE)
            Command.HALF_DOWN -> drag(0f, -height / 2)
            Command.HALF_UP -> drag(0f, height / 2)
            Command.LEFT -> drag(width * STEP_SHARE, 0f)
            Command.RIGHT -> drag(-width * STEP_SHARE, 0f)
            Command.BOTTOM -> fling(-height * FLING_SHARE, FLINGS_TO_END)
            Command.TOP -> fling(height * FLING_SHARE, FLINGS_TO_END)
            Command.EXIT -> close()
        }
    }

    // A drag that stops and holds before lifting, so the app sees no release velocity and does not fling:
    // every key press moves the content the same distance.
    private fun drag(dx: Float, dy: Float) {
        if (gestureInFlight) return
        val (start, end) = endpoints(dx, dy)
        val move = GestureDescription.StrokeDescription(line(start, end), 0, DRAG_MS, true)
        dispatch(move) {
            val hold = Path().apply { moveTo(end.first, end.second) }
            dispatch(move.continueStroke(hold, 0, HOLD_MS, false)) { gestureInFlight = false }
        }
    }

    private fun fling(dy: Float, times: Int) {
        if (gestureInFlight && times == FLINGS_TO_END) return
        val (start, end) = endpoints(0f, dy)
        dispatch(GestureDescription.StrokeDescription(line(start, end), 0, FLING_MS)) {
            if (times > 1 && !closed) fling(dy, times - 1) else gestureInFlight = false
        }
    }

    private fun endpoints(dx: Float, dy: Float): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val area = areas[active]
        val cx = area.exactCenterX()
        val cy = area.exactCenterY()
        return (cx - dx / 2 to cy - dy / 2) to (cx + dx / 2 to cy + dy / 2)
    }

    private fun line(start: Pair<Float, Float>, end: Pair<Float, Float>) =
        Path().apply {
            moveTo(start.first, start.second)
            lineTo(end.first, end.second)
        }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, then: () -> Unit) {
        gestureInFlight = true
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = then()

            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureInFlight = false
            }
        }, null)
        if (!dispatched) gestureInFlight = false
    }

    // Not touchable, so the injected swipes reach the app underneath; focusable, so the keys come here.
    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        fitInsetsTypes = 0
        title = "ReyCast scroll"
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val HELP = "j k scroll  ·  d u half page  ·  gg G top/bottom  ·  h l sideways  ·  Esc done"
        const val STEP_SHARE = 0.12f
        const val FLING_SHARE = 0.6f
        const val FLINGS_TO_END = 6
        const val DRAG_MS = 180L
        const val HOLD_MS = 80L
        const val FLING_MS = 60L
        const val BORDER_DP = 3
        const val CORNER_DP = 10
        const val HELP_MARGIN_DP = 16
    }
}
