package dev.victorstein.reycast.core

object CatalogCache {
    private val fieldAndLineBreaks = Regex("[\t\r\n]")

    fun cleanLabel(label: String): String = label.replace(fieldAndLineBreaks, " ")

    fun serialize(entries: List<CatalogEntry>): String =
        entries.joinToString("\n") { e ->
            listOf(e.component, cleanLabel(e.label), e.packageName, e.lastUpdate.toString())
                .joinToString("\t")
        }

    fun parse(text: String): List<CatalogEntry> =
        text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 4) null else parts[3].toLongOrNull()?.let { CatalogEntry(parts[0], parts[1], parts[2], it) }
        }.toList()
}
