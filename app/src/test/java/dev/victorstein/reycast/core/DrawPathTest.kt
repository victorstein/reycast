package dev.victorstein.reycast.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawPathTest {
    private val root = DrawPath()

    @Test
    fun aLaterDrawnBranchIsOverAnEarlierOne() {
        val feedItem = root.child(1, 0).child(2, 4)
        val miniPlayer = root.child(5, 1)
        assertTrue(miniPlayer.isDrawnOver(feedItem))
        assertFalse(feedItem.isDrawnOver(miniPlayer))
    }

    @Test
    fun drawingOrderWinsOverChildIndex() {
        assertTrue(root.child(3, 0).isDrawnOver(root.child(1, 2)))
    }

    @Test
    fun childIndexBreaksDrawingOrderTies() {
        assertTrue(root.child(0, 2).isDrawnOver(root.child(0, 1)))
    }

    @Test
    fun anAncestorNeverHidesItsDescendant() {
        val card = root.child(1, 0)
        val button = card.child(1, 0)
        assertFalse(card.isDrawnOver(button))
        assertFalse(button.isDrawnOver(card))
    }
}
