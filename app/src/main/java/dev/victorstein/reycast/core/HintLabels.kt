package dev.victorstein.reycast.core

object HintLabels {
    /** Home row first, so the most common (single-letter or first) labels sit under the fingers. */
    const val ALPHABET = "sadfjklewcmpgh"

    /** Every label has the same length, which makes the set prefix-free: a full match can act at once. */
    fun assign(count: Int): List<String> {
        if (count <= 0) return emptyList()
        var length = 1
        var capacity = ALPHABET.length
        while (capacity < count) {
            length++
            capacity *= ALPHABET.length
        }
        return List(count) { index ->
            buildString {
                var remaining = index
                repeat(length) {
                    insert(0, ALPHABET[remaining % ALPHABET.length])
                    remaining /= ALPHABET.length
                }
            }
        }
    }
}
