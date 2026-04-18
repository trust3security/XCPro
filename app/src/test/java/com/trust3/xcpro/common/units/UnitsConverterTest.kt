package com.trust3.xcpro.common.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsConverterTest {

    @Test
    fun `meters to feet round-trip`() {
        val altitudeMeters = 1234.5
        val feet = UnitsConverter.metersToFeet(altitudeMeters)
        val metersAgain = UnitsConverter.feetToMeters(feet)
        assertEquals(altitudeMeters, metersAgain, 1e-9)
    }

    @Test
    fun `ms to kmh and back`() {
        val speedMs = 27.7777777778 // ~100 km/h
        val kmh = UnitsConverter.msToKmh(speedMs)
        assertEquals(100.0, kmh, 1e-9)
        val msAgain = UnitsConverter.kmhToMs(kmh)
        assertEquals(speedMs, msAgain, 1e-9)
    }

    @Test
    fun `vertical speed ms to fpm`() {
        val climb = 2.5
        val fpm = UnitsConverter.verticalMsToFpm(climb)
        assertEquals(492.126, fpm, 0.001)
        val msAgain = UnitsConverter.fpmToVerticalMs(fpm)
        assertEquals(climb, msAgain, 1e-6)
    }

    @Test
    fun `ms to knots and back`() {
        val verticalSpeed = 1.25
        val knots = UnitsConverter.msToKnots(verticalSpeed)
        assertEquals(2.429805, knots, 1e-6)
        val msAgain = UnitsConverter.knotsToMs(knots)
        assertEquals(verticalSpeed, msAgain, 1e-9)
    }
}
