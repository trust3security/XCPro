package com.example.xcpro.adsb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbAircraftIconMapperTest {

    @Test
    fun mapsHelicopterCategory() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForCategory(8)
        )
    }

    @Test
    fun mapsGliderCategory() {
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForCategory(9)
        )
    }

    @Test
    fun mapsLightAircraftCategories() {
        assertEquals(AdsbAircraftIcon.PlaneLight, iconForCategory(2))
        assertEquals(AdsbAircraftIcon.PlaneLight, iconForCategory(3))
    }

    @Test
    fun mapsLargeAircraftCategories() {
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(4))
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(5))
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(6))
    }

    @Test
    fun mapsHighPerformanceCategoryToLargePlane() {
        assertEquals(AdsbAircraftIcon.PlaneLarge, iconForCategory(7))
    }

    @Test
    fun mapsBalloonParachutistHanggliderAndDroneCategories() {
        assertEquals(AdsbAircraftIcon.Balloon, iconForCategory(10))
        assertEquals(AdsbAircraftIcon.Parachutist, iconForCategory(11))
        assertEquals(AdsbAircraftIcon.Hangglider, iconForCategory(12))
        assertEquals(AdsbAircraftIcon.Drone, iconForCategory(14))
    }

    @Test
    fun mapsNullOrUnsupportedToUnknown() {
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(null))
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(1))
        assertEquals(AdsbAircraftIcon.Unknown, iconForCategory(20))
    }

    @Test
    fun labelsNoCategoryInfoForZeroAndOne() {
        assertEquals("No category information", openSkyCategoryLabel(0))
        assertEquals("No ADS-B category information", openSkyCategoryLabel(1))
    }

    @Test
    fun labelsKnownAndUnknownCategories() {
        assertEquals("Rotorcraft", openSkyCategoryLabel(8))
        assertEquals("Unknown", openSkyCategoryLabel(null))
        assertEquals("Unknown", openSkyCategoryLabel(19))
    }

    @Test
    fun prefersIcaoAircraftTypeWhenAvailable() {
        assertEquals(
            AdsbAircraftIcon.Helicopter,
            iconForAircraft(
                category = 3,
                metadataTypecode = "C172",
                metadataIcaoAircraftType = "H1P"
            )
        )
    }

    @Test
    fun fallsBackToTypecodeHeuristicsWhenIcaoAircraftTypeMissing() {
        assertEquals(
            AdsbAircraftIcon.Glider,
            iconForAircraft(
                category = null,
                metadataTypecode = "ASW2",
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun fallsBackToOpenSkyCategoryWhenMetadataDoesNotClassify() {
        assertEquals(
            AdsbAircraftIcon.Balloon,
            iconForAircraft(
                category = 10,
                metadataTypecode = null,
                metadataIcaoAircraftType = null
            )
        )
    }

    @Test
    fun classifiesMediumJetClassAsLargeAircraft() {
        assertEquals(
            AdsbAircraftIcon.PlaneLarge,
            iconForAircraft(
                category = null,
                metadataTypecode = "B738",
                metadataIcaoAircraftType = "L2J"
            )
        )
    }

    @Test
    fun keepsLightIconForSmallJetClass() {
        assertEquals(
            AdsbAircraftIcon.PlaneLight,
            iconForAircraft(
                category = null,
                metadataTypecode = "VLJ1",
                metadataIcaoAircraftType = "L1J"
            )
        )
    }
}
