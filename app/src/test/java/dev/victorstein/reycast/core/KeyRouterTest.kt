package dev.victorstein.reycast.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRouterTest {
    private fun route(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, unicode: Int = 0) =
        KeyRouter.route(keyCode, ctrl, alt, unicode)

    @Test
    fun arrowsAndCtrlJkMoveTheHighlight() {
        assertEquals(KeyAction.MoveUp, route(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(KeyAction.MoveDown, route(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(KeyAction.MoveUp, route(KeyEvent.KEYCODE_K, ctrl = true, unicode = 'k'.code))
        assertEquals(KeyAction.MoveDown, route(KeyEvent.KEYCODE_J, ctrl = true, unicode = 'j'.code))
    }

    @Test
    fun ctrlTakesPrecedenceOverAlt() {
        assertEquals(KeyAction.MoveUp, route(KeyEvent.KEYCODE_K, ctrl = true, alt = true, unicode = 'k'.code))
    }

    @Test
    fun enterAndNumpadEnterLaunch() {
        assertEquals(KeyAction.Launch, route(KeyEvent.KEYCODE_ENTER))
        assertEquals(KeyAction.Launch, route(KeyEvent.KEYCODE_NUMPAD_ENTER))
    }

    @Test
    fun escapeCloses() {
        assertEquals(KeyAction.Close, route(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun altHeldLetterTypesThePlainLetter() {
        assertEquals(KeyAction.Type('t'), route(KeyEvent.KEYCODE_T, alt = true, unicode = 't'.code))
    }

    @Test
    fun altWithSpaceTypesASpaceAndAltWithEnterOrEscapeStillLaunchesOrCloses() {
        assertEquals(KeyAction.Type(' '), route(KeyEvent.KEYCODE_SPACE, alt = true, unicode = ' '.code))
        assertEquals(KeyAction.Launch, route(KeyEvent.KEYCODE_ENTER, alt = true))
        assertEquals(KeyAction.Close, route(KeyEvent.KEYCODE_ESCAPE, alt = true))
    }

    @Test
    fun everythingElsePassesThrough() {
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_T, ctrl = true, alt = true, unicode = 't'.code))
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_DEL, alt = true))
        val deadAcute = 0x80000000.toInt() or '´'.code
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_E, alt = true, unicode = deadAcute))
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_T, unicode = 't'.code))
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_TAB, alt = true, unicode = '\t'.code))
        assertEquals(KeyAction.PassThrough, route(KeyEvent.KEYCODE_T, ctrl = true, unicode = 't'.code))
    }
}
