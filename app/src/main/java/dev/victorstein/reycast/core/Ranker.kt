package dev.victorstein.reycast.core

object Ranker {
    const val MAX_RESULTS = 8

    fun rank(entries: List<CatalogEntry>, query: String, frecency: Map<String, Int>): List<CatalogEntry> {
        val score = { entry: CatalogEntry -> frecency[entry.component] ?: 0 }
        if (query.isBlank()) {
            return entries
                .sortedWith(compareByDescending(score).thenBy { it.label.lowercase() }.thenBy { it.component })
                .take(MAX_RESULTS)
        }
        val trimmedQuery = query.trim()
        return entries
            .mapNotNull { entry -> Matcher.match(trimmedQuery, entry.label)?.let { entry to it } }
            .sortedWith(
                compareBy<Pair<CatalogEntry, Match>> { it.second.tier.ordinal }
                    .thenBy { if (it.second.tier == Tier.SUBSEQUENCE) it.second.gaps else 0 }
                    .thenByDescending { score(it.first) }
                    .thenBy { it.first.label.lowercase() }
                    .thenBy { it.first.component },
            )
            .map { it.first }
            .take(MAX_RESULTS)
    }
}
