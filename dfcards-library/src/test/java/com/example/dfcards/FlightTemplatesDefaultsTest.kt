package com.example.dfcards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FlightTemplatesDefaultsTest {
    @Test
    fun cruiseDefaultTemplate_usesAglCardOnly() {
        val cruise = FlightTemplates.getDefaultTemplates().find { it.id == "id01" }

        assertNotNull(cruise)
        assertEquals(listOf("agl"), cruise?.cardIds)
    }

    @Test
    fun thermalDefaultTemplate_usesRequestedThermalCards() {
        val thermal = FlightTemplates.getDefaultTemplates().find { it.id == "id02" }

        assertNotNull(thermal)
        assertEquals(
            listOf("thermal_tc_gain", "thermal_tc_avg", "thermal_t_avg"),
            thermal?.cardIds
        )
    }

    @Test
    fun glideDefaultTemplate_uses_live_polar_cards() {
        val glide = FlightTemplates.getDefaultTemplates().find { it.id == "id03" }

        assertNotNull(glide)
        assertEquals(
            listOf("gps_alt", "polar_ld", "best_ld", "mc_speed"),
            glide?.cardIds
        )
    }

    @Test
    fun builtInTemplates_do_not_ship_placeholder_only_glide_or_task_cards() {
        val placeholderOnly = setOf("wpt_dist", "wpt_brg", "wpt_eta", "task_spd", "task_dist", "start_alt")
        val allDefaultCardIds = FlightTemplates.getDefaultTemplates().flatMap { it.cardIds }.toSet()

        assertEquals(emptySet<String>(), allDefaultCardIds.intersect(placeholderOnly))
    }
}
