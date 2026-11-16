package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RankerTest {
    private fun entry(label: String) = CatalogEntry("pkg.$label/.Main", label, "pkg.$label", 0)
    private fun labels(list: List<CatalogEntry>) = list.map { it.label }

    @Test
    fun tierBeatsFrecency() {
        val stocks = entry("Stocks")
        val playStore = entry("Play Store")
        val rest = entry("Rest")
        val frecency = mapOf(rest.component to 500, playStore.component to 300)
        assertEquals(
            listOf("Stocks", "Play Store", "Rest"),
            labels(Ranker.rank(listOf(rest, playStore, stocks), "st", frecency)),
        )
    }

    @Test
    fun frecencyOrdersWithinATier() {
        val termux = entry("Termux")
        val telegram = entry("Telegram")
        val frecency = mapOf(termux.component to 100, telegram.component to 300)
        assertEquals(listOf("Telegram", "Termux"), labels(Ranker.rank(listOf(termux, telegram), "te", frecency)))
    }

    @Test
    fun gapsOrderTheSubsequenceTierBeforeFrecency() {
        val tax = entry("Tax")
        val termux = entry("Termux")
        val frecency = mapOf(termux.component to 999)
        assertEquals(listOf("Tax", "Termux"), labels(Ranker.rank(listOf(termux, tax), "tx", frecency)))
    }

    @Test
    fun tiesAreAlphabeticalIgnoringCase() {
        assertEquals(listOf("alpha", "Beta"), labels(Ranker.rank(listOf(entry("Beta"), entry("alpha")), "", emptyMap())))
    }

    @Test
    fun emptyOrBlankQueryOrdersByFrecency() {
        val a = entry("Alpha")
        val z = entry("Zulu")
        val frecency = mapOf(z.component to 50)
        assertEquals(listOf("Zulu", "Alpha"), labels(Ranker.rank(listOf(a, z), "  ", frecency)))
    }

    @Test
    fun returnsAtMostEightResults() {
        val many = (1..10).map { entry("App$it") }
        assertEquals(8, Ranker.rank(many, "", emptyMap()).size)
    }

    @Test
    fun nonMatchingEntriesAreHidden() {
        assertEquals(listOf("Termux"), labels(Ranker.rank(listOf(entry("Termux"), entry("Chrome")), "ter", emptyMap())))
    }
}
