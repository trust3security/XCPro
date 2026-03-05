package com.example.xcpro.ogn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnAddressingTest {

    @Test
    fun selectionLookupContains_matchesCanonicalCandidate_whenLegacySelected() {
        val lookup = buildOgnSelectionLookup(setOf("AB12CD"))

        assertTrue(
            selectionLookupContainsOgnKey(
                lookup = lookup,
                candidateKey = "FLARM:AB12CD"
            )
        )
    }

    @Test
    fun selectionLookupContains_matchesLegacyCandidate_whenCanonicalSelected() {
        val lookup = buildOgnSelectionLookup(setOf("FLARM:AB12CD"))

        assertTrue(
            selectionLookupContainsOgnKey(
                lookup = lookup,
                candidateKey = "AB12CD"
            )
        )
    }

    @Test
    fun selectionLookupContains_matchesCanonicalAcrossTypes_whenHexAliasMatches() {
        val lookup = buildOgnSelectionLookup(setOf("ICAO:AB12CD"))

        assertTrue(
            selectionLookupContainsOgnKey(
                lookup = lookup,
                candidateKey = "FLARM:AB12CD"
            )
        )
    }

    @Test
    fun selectionLookupContains_returnsFalse_whenHexAliasDoesNotMatch() {
        val lookup = buildOgnSelectionLookup(setOf("FLARM:AB12CD"))

        assertFalse(
            selectionLookupContainsOgnKey(
                lookup = lookup,
                candidateKey = "ICAO:EF34AA"
            )
        )
    }
}
