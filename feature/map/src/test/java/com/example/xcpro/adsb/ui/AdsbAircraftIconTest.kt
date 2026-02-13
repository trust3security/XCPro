package com.example.xcpro.adsb.ui

import com.example.xcpro.map.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbAircraftIconTest {

    @Test
    fun hasStableStyleImageIds() {
        assertEquals("adsb_icon_plane_light", AdsbAircraftIcon.PlaneLight.styleImageId)
        assertEquals("adsb_icon_plane_medium", AdsbAircraftIcon.PlaneMedium.styleImageId)
        assertEquals("adsb_icon_plane_large", AdsbAircraftIcon.PlaneLarge.styleImageId)
        assertEquals("adsb_icon_plane_heavy", AdsbAircraftIcon.PlaneHeavy.styleImageId)
        assertEquals(
            "adsb_icon_plane_large_icao_override",
            AdsbAircraftIcon.PlaneLargeIcaoOverride.styleImageId
        )
        assertEquals("adsb_icon_helicopter", AdsbAircraftIcon.Helicopter.styleImageId)
        assertEquals("adsb_icon_glider", AdsbAircraftIcon.Glider.styleImageId)
        assertEquals("adsb_icon_balloon", AdsbAircraftIcon.Balloon.styleImageId)
        assertEquals("adsb_icon_parachutist", AdsbAircraftIcon.Parachutist.styleImageId)
        assertEquals("adsb_icon_hangglider", AdsbAircraftIcon.Hangglider.styleImageId)
        assertEquals("adsb_icon_drone", AdsbAircraftIcon.Drone.styleImageId)
        assertEquals("adsb_icon_unknown", AdsbAircraftIcon.Unknown.styleImageId)
    }

    @Test
    fun hasExpectedDrawableMappings() {
        assertEquals(R.drawable.ic_adsb_plane_light, AdsbAircraftIcon.PlaneLight.resId)
        assertEquals(R.drawable.ic_adsb_plane_medium, AdsbAircraftIcon.PlaneMedium.resId)
        assertEquals(R.drawable.ic_adsb_plane_large, AdsbAircraftIcon.PlaneLarge.resId)
        assertEquals(R.drawable.ic_adsb_plane_heavy, AdsbAircraftIcon.PlaneHeavy.resId)
        assertEquals(
            R.drawable.ic_adsb_plane_large,
            AdsbAircraftIcon.PlaneLargeIcaoOverride.resId
        )
        assertEquals(R.drawable.ic_adsb_helicopter, AdsbAircraftIcon.Helicopter.resId)
        assertEquals(R.drawable.ic_adsb_glider, AdsbAircraftIcon.Glider.resId)
        assertEquals(R.drawable.ic_adsb_balloon, AdsbAircraftIcon.Balloon.resId)
        assertEquals(R.drawable.ic_adsb_parachutist_symbol, AdsbAircraftIcon.Parachutist.resId)
        assertEquals(R.drawable.ic_adsb_hangglider_symbol, AdsbAircraftIcon.Hangglider.resId)
        assertEquals(R.drawable.ic_adsb_drone, AdsbAircraftIcon.Drone.resId)
        assertEquals(R.drawable.ic_adsb_unknown, AdsbAircraftIcon.Unknown.resId)
    }

    @Test
    fun hasStableEmergencyStyleImageIds() {
        assertEquals(
            "adsb_icon_plane_light_emergency",
            AdsbAircraftIcon.PlaneLight.emergencyStyleImageId()
        )
        assertEquals(
            "adsb_icon_unknown_emergency",
            AdsbAircraftIcon.Unknown.emergencyStyleImageId()
        )
    }
}
