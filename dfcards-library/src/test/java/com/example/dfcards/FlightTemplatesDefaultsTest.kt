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
}
