package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnIdentifierDistanceLabelMapperTest {

    @Test
    fun map_usesCompetitionIdWhenPresent() {
        val label = OgnIdentifierDistanceLabelMapper.map(
            competitionId = "AB1",
            registration = "D-KABC",
            distanceMeters = 1700.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals("AB1", label.identifier)
        assertEquals("AB1 1.7 km", label.text)
    }

    @Test
    fun map_usesRegistrationTailWhenCompetitionIdMissing() {
        val label = OgnIdentifierDistanceLabelMapper.map(
            competitionId = " ",
            registration = "d-kabx",
            distanceMeters = 1700.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals("ABX", label.identifier)
        assertEquals("ABX 1.7 km", label.text)
    }

    @Test
    fun map_usesUnknownIdentifierWhenNoIdData() {
        val label = OgnIdentifierDistanceLabelMapper.map(
            competitionId = null,
            registration = null,
            distanceMeters = 1700.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals(OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER, label.identifier)
        assertEquals("${OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER} 1.7 km", label.text)
    }

    @Test
    fun map_omitsDistanceWhenUnavailable() {
        val label = OgnIdentifierDistanceLabelMapper.map(
            competitionId = "CD2",
            registration = "D-KABC",
            distanceMeters = null,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals("CD2", label.identifier)
        assertEquals("CD2", label.text)
    }
}
