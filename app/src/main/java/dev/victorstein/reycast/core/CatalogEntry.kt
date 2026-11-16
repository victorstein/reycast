package dev.victorstein.reycast.core

data class CatalogEntry(
    val component: String,
    val label: String,
    val packageName: String,
    val lastUpdate: Long,
)
