package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HintFilterTest {
    private val labels = listOf("sa", "sd", "as")

    @Test
    fun aPrefixNarrowsTheVisibleLabels() {
        val filter = HintFilter(labels)
        assertNull(filter.type('s'))
        assertEquals("s", filter.typed)
        assertTrue(filter.isVisible(0))
        assertTrue(filter.isVisible(1))
        assertFalse(filter.isVisible(2))
    }

    @Test
    fun theCompletingLetterReturnsTheMatchIndex() {
        val filter = HintFilter(labels)
        filter.type('s')
        assertEquals(1, filter.type('d'))
    }

    @Test
    fun aLetterThatMatchesNothingIsIgnored() {
        val filter = HintFilter(labels)
        filter.type('s')
        assertNull(filter.type('z'))
        assertNull(filter.type('s'))
        assertEquals("s", filter.typed)
    }

    @Test
    fun backspaceRemovesTheLastLetterAndIsSafeWhenEmpty() {
        val filter = HintFilter(labels)
        filter.backspace()
        assertEquals("", filter.typed)
        filter.type('a')
        filter.backspace()
        assertEquals("", filter.typed)
        assertTrue(filter.isVisible(2))
    }

    @Test
    fun singleLetterLabelsMatchImmediately() {
        assertEquals(2, HintFilter(listOf("s", "a", "d")).type('d'))
    }

    @Test
    fun lettersAfterAFullMatchAreIgnored() {
        val filter = HintFilter(labels)
        filter.type('s')
        filter.type('d')
        assertNull(filter.type('a'))
        assertEquals("sd", filter.typed)
    }

    @Test
    fun backspaceAfterAnIgnoredLetterRemovesTheRealLastLetter() {
        val filter = HintFilter(labels)
        filter.type('a')
        filter.type('z')
        filter.backspace()
        assertEquals("", filter.typed)
    }
}
