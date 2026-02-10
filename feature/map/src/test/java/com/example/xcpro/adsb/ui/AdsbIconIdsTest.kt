package com.example.xcpro.adsb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbIconIdsTest {

    @Test
    fun mapsAllKindsToStableIconIds() {
        assertEquals(AdsbIconIds.SMALL_SINGLE_ENGINE, AdsbAircraftKind.SmallSingleEngine.toAdsbIconId())
        assertEquals(AdsbIconIds.SMALL_JET, AdsbAircraftKind.SmallJet.toAdsbIconId())
        assertEquals(AdsbIconIds.LARGE_JET, AdsbAircraftKind.LargeJet.toAdsbIconId())
        assertEquals(AdsbIconIds.HELICOPTER, AdsbAircraftKind.Helicopter.toAdsbIconId())
        assertEquals(AdsbIconIds.GLIDER, AdsbAircraftKind.Glider.toAdsbIconId())
        assertEquals(AdsbIconIds.UNKNOWN, AdsbAircraftKind.Unknown.toAdsbIconId())
    }
}

