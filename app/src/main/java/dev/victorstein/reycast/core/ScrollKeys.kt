package dev.victorstein.reycast.core

/** Vim-style scroll keys: j/k step, d/u half page, gg top, G bottom, h/l sideways, q quit. */
class ScrollKeys {
    enum class Command { DOWN, UP, HALF_DOWN, HALF_UP, TOP, BOTTOM, LEFT, RIGHT, EXIT }

    private var pendingG = false

    /** [letter] is the lowercase key; [shift] is a Shift pressed during scroll mode (G = Shift+g). */
    fun type(letter: Char, shift: Boolean): Command? {
        val afterG = pendingG
        pendingG = false
        return when (letter) {
            'g' -> when {
                shift -> Command.BOTTOM
                afterG -> Command.TOP
                else -> {
                    pendingG = true
                    null
                }
            }
            'j' -> Command.DOWN
            'k' -> Command.UP
            'd' -> Command.HALF_DOWN
            'u' -> Command.HALF_UP
            'h' -> Command.LEFT
            'l' -> Command.RIGHT
            'q' -> Command.EXIT
            else -> null
        }
    }
}
