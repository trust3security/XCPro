package com.example.xcpro.sensors.domain

import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWindValidityPolicyTest {

    @Test
    fun wind_is_not_live_usable_when_airspeed_source_is_not_wind() {
        val wind = WindState(
            vector = WindVector(east = 3.0, north = 2.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        assertFalse(LiveWindValidityPolicy.isLiveWindUsable(wind, "GPS"))
    }

    @Test
    fun wind_is_live_usable_only_when_source_is_wind_and_vector_is_available() {
        val wind = WindState(
            vector = WindVector(east = 3.0, north = 2.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 0.2
        )

        assertTrue(LiveWindValidityPolicy.isLiveWindUsable(wind, "WIND"))
        assertFalse(LiveWindValidityPolicy.isLiveWindUsable(wind.copy(stale = true), "WIND"))
        assertFalse(LiveWindValidityPolicy.isLiveWindUsable(wind.copy(vector = null), "WIND"))
    }
}
