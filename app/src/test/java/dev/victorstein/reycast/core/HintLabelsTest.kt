package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintLabelsTest {
    private fun assertPrefixFree(labels: List<String>) {
        assertEquals(labels.size, labels.toSet().size)
        for (a in labels) for (b in labels) if (a != b) assertTrue("$a prefixes $b", !b.startsWith(a))
    }

    @Test
    fun noTargetsNoLabels() {
        assertEquals(emptyList<String>(), HintLabels.assign(0))
    }

    @Test
    fun upToFourteenTargetsGetSingleLettersInAlphabetOrder() {
        assertEquals(listOf("s"), HintLabels.assign(1))
        assertEquals(HintLabels.ALPHABET.map { it.toString() }, HintLabels.assign(14))
    }

    @Test
    fun fifteenTargetsAllGetTwoLetters() {
        val labels = HintLabels.assign(15)
        assertEquals(15, labels.size)
        assertTrue(labels.all { it.length == 2 })
        assertEquals(listOf("ss", "sa", "sd"), labels.take(3))
        assertPrefixFree(labels)
    }

    @Test
    fun twoLettersCoverExactly196() {
        val labels = HintLabels.assign(196)
        assertTrue(labels.all { it.length == 2 })
        assertPrefixFree(labels)
        assertTrue(HintLabels.assign(197).all { it.length == 3 })
    }

    @Test
    fun labelsUseOnlyTheAlphabet() {
        assertTrue(HintLabels.assign(300).all { label -> label.all { it in HintLabels.ALPHABET } })
    }
}
