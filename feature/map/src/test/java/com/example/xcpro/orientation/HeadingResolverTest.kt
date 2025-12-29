package com.example.xcpro.orientation

import com.example.xcpro.common.orientation.BearingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingResolverTest {

    private val resolver = HeadingResolver()

    @Test
    fun `uses compass when reliable`() {
        val input = HeadingResolverInput(
            compassHeadingDeg = 123.0,
            compassReliable = true,
            gpsTrackDeg = 90.0,
            groundSpeedMs = 10.0,
            hasGpsFix = true,
            windFromDeg = 180.0,
            windSpeedMs = 5.0,
            minTrackSpeedMs = 1.0
        )

        val result = resolver.resolve(input)

        assertEquals(123.0, result.bearingDeg, 0.001)
        assertTrue(result.isValid)
        assertEquals(BearingSource.COMPASS, result.source)
    }

    @Test
    fun `derives heading from wind vector when compass missing`() {
        val input = HeadingResolverInput(
            compassHeadingDeg = null,
            compassReliable = false,
            gpsTrackDeg = 90.0,
            groundSpeedMs = 30.0,
            hasGpsFix = true,
            windFromDeg = 180.0, // Wind blowing from south to north
            windSpeedMs = 10.0,
            minTrackSpeedMs = 5.0
        )

        val result = resolver.resolve(input)

        assertTrue(result.isValid)
        assertEquals(BearingSource.WIND, result.source)
        assertEquals(108.435, result.bearingDeg, 0.01) // atan2(30E,-10N)
    }

    @Test
    fun `falls back to track but marks invalid when below threshold`() {
        val input = HeadingResolverInput(
            compassHeadingDeg = null,
            compassReliable = false,
            gpsTrackDeg = 45.0,
            groundSpeedMs = 0.5,
            hasGpsFix = true,
            windFromDeg = null,
            windSpeedMs = 0.0,
            minTrackSpeedMs = 2.0
        )

        val result = resolver.resolve(input)

        assertEquals(45.0, result.bearingDeg, 0.001)
        assertFalse(result.isValid)
        assertEquals(BearingSource.TRACK, result.source)
    }
}
