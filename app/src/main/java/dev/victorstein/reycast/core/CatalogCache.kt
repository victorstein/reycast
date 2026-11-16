package dev.victorstein.reycast.core

object CatalogCache {
    private val lineBreaking = Regex("[\t\r\n]")

    fun serialize(entries: List<CatalogEntry>): String =
        entries.joinToString("\n") { e ->
            listOf(e.component, e.label.replace(lineBreaking, " "), e.packageName, e.lastUpdate.toString())
                .joinToString("\t")
        }

    fun parse(text: String): List<CatalogEntry> =
        text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            val lastUpdate = parts.getOrNull(3)?.toLongOrNull()
            if (parts.size != 4 || lastUpdate == null) null
            else CatalogEntry(parts[0], parts[1], parts[2], lastUpdate)
        }.toList()
}
