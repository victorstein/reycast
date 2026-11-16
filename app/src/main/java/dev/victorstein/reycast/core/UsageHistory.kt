package dev.victorstein.reycast.core

class UsageHistory {
    private val launches = HashMap<String, MutableList<Long>>()

    fun record(key: String, nowMs: Long) {
        launches.getOrPut(key) { ArrayList() }.add(nowMs)
        prune(nowMs)
    }

    fun scores(nowMs: Long): Map<String, Int> =
        launches.mapValues { (_, times) -> times.sumOf { pointsFor(nowMs - it) } }
            .filterValues { it > 0 }

    fun serialize(): String =
        launches.entries.joinToString("\n") { (key, times) -> "$key\t${times.joinToString(",")}" }

    private fun prune(nowMs: Long) {
        val iterator = launches.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val kept = entry.value.map { minOf(it, nowMs) }
                .filter { nowMs - it < MAX_AGE_MS }.sorted().takeLast(MAX_LAUNCHES)
            if (kept.isEmpty()) iterator.remove() else entry.setValue(kept.toMutableList())
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24 * HOUR_MS
        private const val MAX_AGE_MS = 30 * DAY_MS
        private const val MAX_LAUNCHES = 20

        fun pointsFor(ageMs: Long): Int = when {
            ageMs < 4 * HOUR_MS -> 100
            ageMs < DAY_MS -> 80
            ageMs < 3 * DAY_MS -> 60
            ageMs < 7 * DAY_MS -> 40
            ageMs < MAX_AGE_MS -> 20
            else -> 0
        }

        fun parse(text: String): UsageHistory {
            val history = UsageHistory()
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 2 || parts[0].isBlank()) continue
                val times = parts[1].split(',').mapNotNull { it.toLongOrNull() }
                if (times.isNotEmpty()) history.launches[parts[0]] = times.toMutableList()
            }
            return history
        }
    }
}
