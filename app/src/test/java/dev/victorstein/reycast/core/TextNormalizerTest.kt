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
    fun keepsLettersThatHaveNoAsciiForm() {
        assertEquals("øre", TextNormalizer.normalize("Øre").text)
        assertEquals("straße", TextNormalizer.normalize("Straße").text)
        assertEquals("テレグラム", TextNormalizer.normalize("テレグラム").text)
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
