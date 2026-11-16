package dev.victorstein.reycast.core

class HintFilter(private val labels: List<String>) {
    var typed = ""
        private set

    /** Returns the index of the label [letter] completes; null while still partial or when ignored. */
    fun type(letter: Char): Int? {
        val next = typed + letter
        if (labels.none { it.startsWith(next) }) return null
        typed = next
        return labels.indexOf(typed).takeIf { it >= 0 }
    }

    fun backspace() {
        typed = typed.dropLast(1)
    }

    fun isVisible(index: Int) = labels[index].startsWith(typed)
}
