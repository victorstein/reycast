package dev.victorstein.reycast.android

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.Region
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

object ScrollAreas {
    // Below this share of its window a scrollable is a chip row or carousel, not the content people scroll.
    private const val MIN_WINDOW_SHARE = 0.15
    // DeX draws each window's title bar as a window of its own; those are a few percent of the screen.
    private const val MIN_SCREEN_SHARE = 0.05
    // An inner scrollable at least this share of the one around it is the same area (a pager wrapping a list);
    // a smaller one is a carousel inside a feed, e.g. YouTube's Shorts shelf, and both stay.
    private const val SAME_AREA_SHARE = 0.6

    /**
     * The big scrollable views in each visible app window; a window with none (Termux's terminal is not an
     * accessibility scrollable) is offered whole. The focused window's areas come first, so scroll mode starts
     * on the app being used. Blocking cross-process calls: run off the main thread.
     */
    fun collect(service: AccessibilityService): List<Rect> {
        val areas = ArrayList<Rect>()
        val focusedAreas = ArrayList<Rect>()
        val metrics = service.resources.displayMetrics
        val minWindowArea = metrics.widthPixels.toLong() * metrics.heightPixels * MIN_SCREEN_SHARE
        // Same top-most-first walk as HintTargets: whatever is already walked covers what comes later.
        val covered = Region()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val windowBounds = Rect().also(window::getBoundsInScreen)
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val visible = Region(windowBounds).apply { op(covered, Region.Op.DIFFERENCE) }
                val visibleArea = visible.bounds.width().toLong() * visible.bounds.height()
                if (visibleArea >= minWindowArea) {
                    val inWindow = window.root?.let { scrollables(it, visible.bounds, covered) }.orEmpty()
                    val windowAreas = innermost(inWindow).ifEmpty { listOf(visible.bounds) }
                    if (window.isFocused) focusedAreas += windowAreas else areas += windowAreas
                }
            }
            covered.op(windowBounds, Region.Op.UNION)
        }
        return focusedAreas + areas
    }

    private fun scrollables(root: AccessibilityNodeInfo, visibleBounds: Rect, covered: Region): List<Rect> {
        val minArea = visibleBounds.width().toLong() * visibleBounds.height() * MIN_WINDOW_SHARE
        val found = ArrayList<Rect>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
            if (!node.isScrollable || !node.isVisibleToUser) continue
            val rect = Rect().also(node::getBoundsInScreen)
            if (!rect.intersect(visibleBounds) || rect.width().toLong() * rect.height() < minArea) continue
            if (covered.contains(rect.centerX(), rect.centerY())) continue
            found += rect
        }
        return found
    }

    // A pager wrapping a list are both big and nearly the same box; keep the inner one, which takes the swipe.
    private fun innermost(rects: List<Rect>): List<Rect> = rects.filter { outer ->
        rects.none { inner ->
            inner !== outer && outer.contains(inner) && inner != outer && area(inner) >= area(outer) * SAME_AREA_SHARE
        }
    }.distinct()

    private fun area(rect: Rect) = rect.width().toLong() * rect.height()
}
