package dev.victorstein.reycast.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UsageHistoryTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    @Test
    fun pointsDropAtEachAgeBoundary() {
        assertEquals(100, UsageHistory.pointsFor(0))
        assertEquals(100, UsageHistory.pointsFor(4 * hour - 1))
        assertEquals(80, UsageHistory.pointsFor(4 * hour))
        assertEquals(60, UsageHistory.pointsFor(day))
        assertEquals(40, UsageHistory.pointsFor(3 * day))
        assertEquals(20, UsageHistory.pointsFor(7 * day))
        assertEquals(20, UsageHistory.pointsFor(30 * day - 1))
        assertEquals(0, UsageHistory.pointsFor(30 * day))
    }

    @Test
    fun frecencySumsLaunchPoints() {
        val history = UsageHistory()
        history.record("a/A", now - 2 * day)
        history.record("a/A", now)
        assertEquals(160, history.scores(now)["a/A"])
    }

    @Test
    fun keepsOnlyTheTwentyMostRecentLaunches() {
        val history = UsageHistory()
        repeat(25) { history.record("a/A", now) }
        assertEquals(20 * 100, history.scores(now)["a/A"])
    }

    @Test
    fun launchesOlderThanThirtyDaysArePruned() {
        val history = UsageHistory()
        history.record("old/Old", now)
        history.record("new/New", now + 31 * day)
        assertFalse(history.scores(now + 31 * day).containsKey("old/Old"))
        assertFalse(history.serialize().contains("old/Old"))
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val history = UsageHistory()
        history.record("a/A", now - hour)
        history.record("b/B", now)
        val parsed = UsageHistory.parse(history.serialize())
        assertEquals(history.scores(now), parsed.scores(now))
    }

    @Test
    fun corruptTextGivesEmptyHistory() {
        assertEquals(emptyMap<String, Int>(), UsageHistory.parse("not\ta,number\n\u0000garbage").scores(now))
    }
}
