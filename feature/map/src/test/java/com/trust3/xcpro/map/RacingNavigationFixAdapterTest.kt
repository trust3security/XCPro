package com.trust3.xcpro.map

import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.sensors.GPSData
import org.junit.Assert.assertEquals
import org.junit.Test

class RacingNavigationFixAdapterTest {

    @Test
    fun usesMonotonicTimeWhenAvailable() {
        val gps = GPSData(
            position = GeoPoint(latitude = 1.0, longitude = 2.0),
            altitude = AltitudeM(100.0),
            speed = SpeedMs(12.0),
            bearing = 45.0,
            accuracy = 1f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 2_000L
        )

        val fix = RacingNavigationFixAdapter.toFix(gps)

        assertEquals(2_000L, fix.timestampMillis)
        assertEquals(1.0, fix.lat, 0.0)
        assertEquals(2.0, fix.lon, 0.0)
    }

    @Test
    fun fallsBackToWallTimestampWhenMonotonicMissing() {
        val gps = GPSData(
            position = GeoPoint(latitude = 3.0, longitude = 4.0),
            altitude = AltitudeM(200.0),
            speed = SpeedMs(0.0),
            bearing = 0.0,
            accuracy = 5f,
            timestamp = 3_000L,
            monotonicTimestampMillis = 0L
        )

        val fix = RacingNavigationFixAdapter.toFix(gps)

        assertEquals(3_000L, fix.timestampMillis)
    }
}
