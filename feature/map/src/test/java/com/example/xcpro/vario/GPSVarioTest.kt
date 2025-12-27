package com.example.xcpro.vario

import org.junit.Assert.assertEquals
import org.junit.Test

class GPSVarioTest {

    @Test
    fun updateFromGpsFix_returns_zero_until_window_is_ready() {
        val vario = GPSVario()

        vario.updateFromGpsFix(gpsAltitudeMeters = 100.0, gpsTimestampMillis = 1_000L)
        vario.updateFromGpsFix(gpsAltitudeMeters = 100.0, gpsTimestampMillis = 2_000L)
        vario.updateFromGpsFix(gpsAltitudeMeters = 100.0, gpsTimestampMillis = 3_000L)
        assertEquals(0.0, vario.getVerticalSpeed(), 1e-6)

        // Once the window span reaches ~3s, we can produce a stable slope (still zero here).
        vario.updateFromGpsFix(gpsAltitudeMeters = 100.0, gpsTimestampMillis = 4_000L)
        assertEquals(0.0, vario.getVerticalSpeed(), 1e-6)
    }

    @Test
    fun updateFromGpsFix_ignores_duplicate_timestamps() {
        val vario = GPSVario()

        vario.updateFromGpsFix(gpsAltitudeMeters = 0.0, gpsTimestampMillis = 1_000L)
        vario.updateFromGpsFix(gpsAltitudeMeters = 999.0, gpsTimestampMillis = 1_000L) // duplicate timestamp

        vario.updateFromGpsFix(gpsAltitudeMeters = 10.0, gpsTimestampMillis = 2_000L)
        vario.updateFromGpsFix(gpsAltitudeMeters = 20.0, gpsTimestampMillis = 3_000L)
        vario.updateFromGpsFix(gpsAltitudeMeters = 30.0, gpsTimestampMillis = 4_000L)

        // 0m -> 30m over 3 seconds => 10 m/s.
        assertEquals(10.0, vario.getVerticalSpeed(), 1e-3)
    }
}

