package dev.victorstein.reycast.core

import dev.victorstein.reycast.core.ScrollKeys.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollKeysTest {
    private val keys = ScrollKeys()

    @Test fun stepsAndHalfPages() {
        assertEquals(Command.DOWN, keys.type('j', shift = false))
        assertEquals(Command.UP, keys.type('k', shift = false))
        assertEquals(Command.HALF_DOWN, keys.type('d', shift = false))
        assertEquals(Command.HALF_UP, keys.type('u', shift = false))
        assertEquals(Command.LEFT, keys.type('h', shift = false))
        assertEquals(Command.RIGHT, keys.type('l', shift = false))
        assertEquals(Command.EXIT, keys.type('q', shift = false))
    }

    @Test fun ggIsTopAndShiftGIsBottom() {
        assertNull(keys.type('g', shift = false))
        assertEquals(Command.TOP, keys.type('g', shift = false))
        assertEquals(Command.BOTTOM, keys.type('g', shift = true))
    }

    @Test fun anotherKeyCancelsAPendingG() {
        assertNull(keys.type('g', shift = false))
        assertEquals(Command.DOWN, keys.type('j', shift = false))
        assertNull(keys.type('g', shift = false))
    }

    @Test fun unknownKeysDoNothing() {
        assertNull(keys.type('z', shift = false))
        assertNull(keys.type('J', shift = true))
    }
}
