package dev.victorstein.reycast.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun lowercasesAndStripsAccents() {
        assertEquals("cafe bar", TextNormalizer.normalize("Café Bar").text)
    }

    @Test
    fun keepsLettersWithoutDecomposition() {
        assertEquals("øre", TextNormalizer.normalize("Øre").text)
        assertEquals("straße", TextNormalizer.normalize("Straße").text)
        assertEquals("テレグラム", TextNormalizer.normalize("テレグラム").text)
    }

    @Test
    fun keepsKanaVoicingMarks() {
        assertEquals("グーグル", TextNormalizer.normalize("グーグル").text)
    }

    @Test
    fun standaloneCombiningMarkProducesNoOutput() {
        val normalized = TextNormalizer.normalize("Cafe\u0301")
        assertEquals("cafe", normalized.text)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), normalized.sourceIndex)
    }

    @Test
    fun surrogatePairMapsBothCharsToItsStart() {
        val normalized = TextNormalizer.normalize("a😀b")
        assertEquals(4, normalized.text.length)
        assertArrayEquals(intArrayOf(0, 1, 1, 3), normalized.sourceIndex)
    }

    @Test
    fun mapsEachNormalizedCharacterBackToItsSource() {
        val normalized = TextNormalizer.normalize("Café")
        assertArrayEquals(intArrayOf(0, 1, 2, 3), normalized.sourceIndex)
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals("", TextNormalizer.normalize("").text)
    }
}
