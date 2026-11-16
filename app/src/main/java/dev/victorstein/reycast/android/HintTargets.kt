package dev.victorstein.reycast.android

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.Region
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

data class HintTarget(val node: AccessibilityNodeInfo, val rect: Rect, val editable: Boolean)

object HintTargets {
    /** Blocking cross-process calls: run off the main thread. */
    fun collect(service: AccessibilityService): List<HintTarget> {
        val targets = ArrayList<HintTarget>()
        val indexByRect = HashMap<Rect, Int>()
        // Window bounds are not cropped by windows above them, and getWindows() lists the top-most first,
        // so everything already walked covers what comes later.
        val covered = Region()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val windowBounds = Rect().also(window::getBoundsInScreen)
            window.root?.let { collectWindow(it, windowBounds, covered, targets, indexByRect) }
            covered.op(windowBounds, Region.Op.UNION)
        }
        return targets
    }

    private fun collectWindow(
        root: AccessibilityNodeInfo,
        windowBounds: Rect,
        covered: Region,
        targets: MutableList<HintTarget>,
        indexByRect: MutableMap<Rect, Int>,
    ) {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
            if (!node.isVisibleToUser || !node.isEnabled) continue
            if (!(node.isClickable || node.isLongClickable || node.isEditable)) continue
            val rect = Rect().also(node::getBoundsInScreen)
            if (!rect.intersect(windowBounds) || rect.isEmpty) continue
            if (covered.contains(rect.centerX(), rect.centerY())) continue
            val target = HintTarget(node, rect, node.isEditable)
            // Identical rects: the later node in tree order is the deeper one; the outer is usually a ripple wrapper.
            val existing = indexByRect[rect]
            if (existing != null) {
                targets[existing] = target
            } else {
                indexByRect[rect] = targets.size
                targets += target
            }
        }
    }
}
