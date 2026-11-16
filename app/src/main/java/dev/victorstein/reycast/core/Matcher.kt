package dev.victorstein.reycast.core

enum class Tier { PREFIX, WORD_START, SUBSEQUENCE }

data class Match(val tier: Tier, val gaps: Int)

object Matcher {
    private val separators = setOf(' ', '-', '_', '.')

    fun match(query: String, label: String): Match? {
        val wanted = TextNormalizer.normalize(query).text
        if (wanted.isEmpty()) return Match(Tier.PREFIX, 0)
        val normalizedLabel = TextNormalizer.normalize(label)
        val text = normalizedLabel.text
        if (text.startsWith(wanted)) return Match(Tier.PREFIX, 0)
        if (wordStarts(label, normalizedLabel).any { text.startsWith(wanted, it) }) {
            return Match(Tier.WORD_START, 0)
        }
        return subsequence(wanted, text)
    }

    private fun wordStarts(label: String, normalized: NormalizedText): List<Int> {
        val sourceStarts = HashSet<Int>()
        for (i in 1 until label.length) {
            val previous = label[i - 1]
            val current = label[i]
            val afterSeparator = previous in separators && current !in separators
            val camelHump = previous.isLowerCase() && current.isUpperCase()
            if (afterSeparator || camelHump) sourceStarts.add(i)
        }
        val index = normalized.sourceIndex
        return index.indices.filter { p ->
            index[p] in sourceStarts && (p == 0 || index[p - 1] != index[p])
        }
    }

    private fun subsequence(wanted: String, text: String): Match? {
        var from = 0
        var first = -1
        var last = -1
        for (c in wanted) {
            val found = text.indexOf(c, from)
            if (found < 0) return null
            if (first < 0) first = found
            last = found
            from = found + 1
        }
        return Match(Tier.SUBSEQUENCE, (last - first + 1) - wanted.length)
    }
}
