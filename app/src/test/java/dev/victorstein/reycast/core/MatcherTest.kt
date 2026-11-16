package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatcherTest {
    @Test
    fun prefixIgnoresCase() {
        assertEquals(Match(Tier.PREFIX, 0), Matcher.match("TER", "termux"))
        assertEquals(Match(Tier.PREFIX, 0), Matcher.match("te", "Termux"))
    }

    @Test
    fun wordStartAfterSpaceDashUnderscoreDot() {
        assertEquals(Tier.WORD_START, Matcher.match("store", "Play Store")?.tier)
        assertEquals(Tier.WORD_START, Matcher.match("box", "drop-box")?.tier)
        assertEquals(Tier.WORD_START, Matcher.match("doc", "my_docs")?.tier)
        assertEquals(Tier.WORD_START, Matcher.match("io", "app.io")?.tier)
    }

    @Test
    fun wordStartAtCamelCaseBoundary() {
        assertEquals(Tier.WORD_START, Matcher.match("mapper", "KeyMapper")?.tier)
    }

    @Test
    fun subsequenceCountsSkippedCharacters() {
        assertEquals(Match(Tier.SUBSEQUENCE, 3), Matcher.match("tmx", "Termux"))
        assertEquals(Match(Tier.SUBSEQUENCE, 4), Matcher.match("tx", "Termux"))
    }

    @Test
    fun noMatchIsNull() {
        assertNull(Matcher.match("zzz", "Termux"))
        assertNull(Matcher.match("xt", "Termux"))
    }

    @Test
    fun accentsAndNonAsciiLettersMatch() {
        assertEquals(Tier.PREFIX, Matcher.match("cafe", "Café Bar")?.tier)
        assertEquals(Tier.PREFIX, Matcher.match("ø", "Øre")?.tier)
        assertEquals(Tier.PREFIX, Matcher.match("straß", "Straße")?.tier)
        assertEquals(Tier.PREFIX, Matcher.match("テレ", "テレグラム")?.tier)
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertEquals(Match(Tier.PREFIX, 0), Matcher.match("", "Termux"))
    }
}
