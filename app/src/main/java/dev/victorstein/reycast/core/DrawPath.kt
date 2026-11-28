package dev.victorstein.reycast.core

/** A node's position in its window's drawing order: one (drawingOrder, childIndex) pair per level from the root. */
class DrawPath(private val steps: IntArray = IntArray(0)) {
    fun child(drawingOrder: Int, index: Int) = DrawPath(steps + drawingOrder + index)

    /** Drawn after [other] and not its ancestor or descendant, so it can hide it. */
    fun isDrawnOver(other: DrawPath): Boolean {
        for (i in 0 until minOf(steps.size, other.steps.size)) {
            if (steps[i] != other.steps[i]) return steps[i] > other.steps[i]
        }
        return false
    }
}
