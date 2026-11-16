package dev.victorstein.reycast.core

import android.view.KeyEvent

sealed interface KeyAction {
    data object MoveUp : KeyAction
    data object MoveDown : KeyAction
    data object Launch : KeyAction
    data object Close : KeyAction
    data class Type(val char: Char) : KeyAction
    data object PassThrough : KeyAction
}

object KeyRouter {
    /** [unicodeWithoutAlt] is the character the key produces with Alt removed from the meta state. */
    fun route(keyCode: Int, ctrl: Boolean, alt: Boolean, unicodeWithoutAlt: Int): KeyAction = when {
        keyCode == KeyEvent.KEYCODE_DPAD_UP || (ctrl && keyCode == KeyEvent.KEYCODE_K) -> KeyAction.MoveUp
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN || (ctrl && keyCode == KeyEvent.KEYCODE_J) -> KeyAction.MoveDown
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyAction.Launch
        keyCode == KeyEvent.KEYCODE_ESCAPE -> KeyAction.Close
        // Cmd arrives as Alt (system Meta→Alt remap); a letter typed before Cmd is released must still type.
        alt && !ctrl && unicodeWithoutAlt > 0 && !Character.isISOControl(unicodeWithoutAlt) ->
            KeyAction.Type(unicodeWithoutAlt.toChar())
        else -> KeyAction.PassThrough
    }
}
