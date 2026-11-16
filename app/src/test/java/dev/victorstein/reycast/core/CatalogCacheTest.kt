package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCacheTest {
    @Test
    fun roundTrip() {
        val entries = listOf(
            CatalogEntry("com.termux/.app.TermuxActivity", "Termux", "com.termux", 123L),
            CatalogEntry("com.android.chrome/com.google.android.apps.chrome.Main", "Chrome", "com.android.chrome", 456L),
        )
        assertEquals(entries, CatalogCache.parse(CatalogCache.serialize(entries)))
    }

    @Test
    fun tabsAndNewlinesInLabelsBecomeSpaces() {
        val entry = CatalogEntry("a/.A", "Two\tWords\nHere", "a", 1L)
        assertEquals("Two Words Here", CatalogCache.parse(CatalogCache.serialize(listOf(entry))).single().label)
    }

    @Test
    fun malformedLinesAreSkipped() {
        assertEquals(
            listOf(CatalogEntry("a/.A", "A", "a", 1L)),
            CatalogCache.parse("garbage\na/.A\tA\ta\t1\nb/.B\tB\tb\tnot-a-number"),
        )
    }
}
