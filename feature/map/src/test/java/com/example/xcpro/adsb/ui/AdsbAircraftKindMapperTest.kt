package com.example.xcpro.adsb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbAircraftKindMapperTest {

    @Test
    fun classifiesRotorcraft() {
        assertEquals(
            AdsbAircraftKind.Helicopter,
            classifyAdsbAircraftKind(category = 8, speedMps = null)
        )
    }

    @Test
    fun classifiesGlider() {
        assertEquals(
            AdsbAircraftKind.Glider,
            classifyAdsbAircraftKind(category = 9, speedMps = 35.0)
        )
    }

    @Test
    fun classifiesLargeJetCategories() {
        assertEquals(AdsbAircraftKind.LargeJet, classifyAdsbAircraftKind(category = 4, speedMps = 90.0))
        assertEquals(AdsbAircraftKind.LargeJet, classifyAdsbAircraftKind(category = 5, speedMps = 130.0))
        assertEquals(AdsbAircraftKind.LargeJet, classifyAdsbAircraftKind(category = 6, speedMps = null))
    }

    @Test
    fun classifiesHighPerformanceAsSmallJet() {
        assertEquals(
            AdsbAircraftKind.SmallJet,
            classifyAdsbAircraftKind(category = 7, speedMps = 80.0)
        )
    }

    @Test
    fun classifiesSmallCategoryUsingSpeedThreshold() {
        assertEquals(
            AdsbAircraftKind.SmallSingleEngine,
            classifyAdsbAircraftKind(category = 3, speedMps = 119.9)
        )
        assertEquals(
            AdsbAircraftKind.SmallJet,
            classifyAdsbAircraftKind(category = 3, speedMps = 120.0)
        )
        assertEquals(
            AdsbAircraftKind.SmallJet,
            classifyAdsbAircraftKind(category = 3, speedMps = 180.0)
        )
    }

    @Test
    fun classifiesLightAsSingleEngine() {
        assertEquals(
            AdsbAircraftKind.SmallSingleEngine,
            classifyAdsbAircraftKind(category = 2, speedMps = null)
        )
    }

    @Test
    fun fallsBackToUnknown() {
        assertEquals(
            AdsbAircraftKind.SmallJet,
            classifyAdsbAircraftKind(category = null, speedMps = 200.0)
        )
        assertEquals(
            AdsbAircraftKind.SmallSingleEngine,
            classifyAdsbAircraftKind(category = null, speedMps = 40.0)
        )
        assertEquals(
            AdsbAircraftKind.Unknown,
            classifyAdsbAircraftKind(category = null, speedMps = null)
        )
        assertEquals(
            AdsbAircraftKind.Unknown,
            classifyAdsbAircraftKind(category = 1, speedMps = 10.0)
        )
    }
}
