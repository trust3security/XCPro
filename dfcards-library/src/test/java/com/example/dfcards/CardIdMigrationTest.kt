package com.example.dfcards

import org.junit.Assert.assertEquals
import org.junit.Test

class CardIdMigrationTest {

    @Test
    fun normalize_mapsAliasesAndTrims() {
        val result = CardIdMigration.normalize(" satellites ")
        assertEquals(CardId.SATELITES, result)
    }

    @Test
    fun normalizeAll_filtersEmptyValues() {
        val result = CardIdMigration.normalizeAll(listOf(" vario ", "  "))
        assertEquals(listOf(CardId.VARIO), result)
    }
}
