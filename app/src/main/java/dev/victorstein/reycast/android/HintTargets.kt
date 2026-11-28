package dev.victorstein.reycast.android

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.Region
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.victorstein.reycast.core.DrawPath

data class HintTarget(val node: AccessibilityNodeInfo, val rect: Rect, val editable: Boolean)

object HintTargets {
    /** Blocking cross-process calls: run off the main thread. */
    fun collect(service: AccessibilityService): List<HintTarget> {
        val targets = ArrayList<HintTarget>()
        // Window bounds are not cropped by windows above them, and getWindows() lists the top-most first,
        // so everything already walked covers what comes later. Covering uses the touchable region, not the
        // bounds: a DeX caption window spans the whole title bar but lets touches through to Chrome's tabs.
        val covered = Region()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val windowBounds = Rect().also(window::getBoundsInScreen)
            window.root?.let { collectWindow(it, windowBounds, covered, targets) }
            covered.op(Region().also(window::getRegionInScreen), Region.Op.UNION)
        }
        return targets
    }

    private fun collectWindow(
        root: AccessibilityNodeInfo,
        windowBounds: Rect,
        covered: Region,
        targets: MutableList<HintTarget>,
    ) {
        val found = ArrayList<Pair<HintTarget, DrawPath>>()
        val indexByRect = HashMap<Rect, Int>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, DrawPath>>().apply { add(root to DrawPath()) }
        while (stack.isNotEmpty()) {
            val (node, path) = stack.removeLast()
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.add(it to path.child(it.drawingOrder, i)) }
            }
            if (!node.isVisibleToUser || !node.isEnabled) continue
            if (!(node.isClickable || node.isLongClickable || node.isEditable)) continue
            val rect = Rect().also(node::getBoundsInScreen)
            if (!rect.intersect(windowBounds) || rect.isEmpty) continue
            if (covered.contains(rect.centerX(), rect.centerY())) continue
            val target = HintTarget(node, rect, node.isEditable) to path
            // Identical rects: keep the later node in tree order (deeper, or drawn on top); the outer is usually a ripple wrapper.
            val existing = indexByRect[rect]
            if (existing != null) {
                found[existing] = target
            } else {
                indexByRect[rect] = found.size
                found += target
            }
        }
        // Views inside one window overlap too, e.g. YouTube's mini player drawn over the feed.
        found.filter { (target, path) ->
            found.none { (other, otherPath) ->
                otherPath.isDrawnOver(path) && other.rect.contains(target.rect.centerX(), target.rect.centerY())
            }
        }.mapTo(targets) { it.first }
    }
}
