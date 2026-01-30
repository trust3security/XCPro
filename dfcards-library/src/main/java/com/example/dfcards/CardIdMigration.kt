package com.example.dfcards

// Legacy ID normalization for persisted layouts.
// Invariants: map only known legacy aliases to current canonical IDs.

internal object CardIdMigration {
    private val aliases = mapOf(
        // Preserve historic typo while accepting corrected spelling.
        "satellites" to CardId.SATELITES
    )

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return aliases[trimmed] ?: trimmed
    }

    fun normalizeAll(rawIds: Iterable<String>): List<String> =
        rawIds.mapNotNull { id ->
            val normalized = normalize(id)
            normalized.takeIf { it.isNotEmpty() }
        }
}
