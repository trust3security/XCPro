package com.example.dfcards

// Coverage tests for card ID catalogs, templates, and spec registry.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdCoverageTest {

    @Test
    fun cardDefinitions_match_knownIds() {
        val catalogIds = allCardDefinitions.map { it.id }.toSet()
        assertEquals(CardId.knownIds, catalogIds)
    }

    @Test
    fun knownIds_match_enum() {
        val enumIds = KnownCardId.values().map { it.raw }.toSet()
        assertEquals(enumIds, CardId.knownIds)
    }

    @Test
    fun templates_use_known_ids() {
        val templateIds = FlightTemplates.getDefaultTemplates()
            .flatMap { it.cardIds }
            .toSet()
        assertTrue(templateIds.all { it in CardId.knownIds })
    }

    @Test
    fun specs_cover_all_known_ids() {
        val expected = KnownCardId.values().toSet()
        assertEquals(expected, CardFormatSpecs.specs.keys)
    }
}
