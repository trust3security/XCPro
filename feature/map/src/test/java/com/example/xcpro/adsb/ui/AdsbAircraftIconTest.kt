package com.example.xcpro.adsb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbAircraftIconTest {

    @Test
    fun hasStableStyleImageIds() {
        assertEquals("adsb_icon_plane_light", AdsbAircraftIcon.PlaneLight.styleImageId)
        assertEquals("adsb_icon_plane_large", AdsbAircraftIcon.PlaneLarge.styleImageId)
        assertEquals("adsb_icon_helicopter", AdsbAircraftIcon.Helicopter.styleImageId)
        assertEquals("adsb_icon_glider", AdsbAircraftIcon.Glider.styleImageId)
        assertEquals("adsb_icon_balloon", AdsbAircraftIcon.Balloon.styleImageId)
        assertEquals("adsb_icon_parachutist", AdsbAircraftIcon.Parachutist.styleImageId)
        assertEquals("adsb_icon_hangglider", AdsbAircraftIcon.Hangglider.styleImageId)
        assertEquals("adsb_icon_drone", AdsbAircraftIcon.Drone.styleImageId)
        assertEquals("adsb_icon_unknown", AdsbAircraftIcon.Unknown.styleImageId)
    }
}
