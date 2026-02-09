package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSkyStateVectorMapperTest {

    @Test
    fun parseResponse_mapsDocumentedIndexesAndTrimsCallsign() {
        val json = """
            {
              "time": 1710000000,
              "states": [
                [
                  "abc123", "CALL123 ", "Australia",
                  1710000000, 1710000001,
                  151.2000, -33.8600,
                  1200.0,
                  false,
                  80.0,  270.0,  1.5,
                  null,
                  1250.0,
                  "7000",
                  false,
                  0,
                  2
                ],
                [
                  "def456", null, "Australia",
                  null, 1710000002,
                  151.2500, -33.9000,
                  null,
                  false,
                  null, null, null,
                  null,
                  null,
                  null,
                  false,
                  3,
                  0
                ]
              ]
            }
        """.trimIndent()

        val parsed = OpenSkyStateVectorMapper.parseResponse(json)
        assertEquals(1710000000L, parsed.timeSec)
        assertEquals(2, parsed.states.size)

        val first = parsed.states[0]
        assertEquals("abc123", first.icao24)
        assertEquals("CALL123", first.callsign)
        assertEquals(-33.8600, first.latitude!!, 1e-6)
        assertEquals(151.2000, first.longitude!!, 1e-6)
        assertEquals(1250.0, first.altitudeM!!, 1e-6)
        assertEquals(80.0, first.velocityMps!!, 1e-6)
        assertEquals(270.0, first.trueTrackDeg!!, 1e-6)
        assertEquals(1.5, first.verticalRateMps!!, 1e-6)
        assertEquals(0, first.positionSource)
        assertEquals(2, first.category)

        val second = parsed.states[1]
        assertEquals("def456", second.icao24)
        assertNull(second.callsign)
        assertEquals(-33.9000, second.latitude!!, 1e-6)
        assertEquals(151.2500, second.longitude!!, 1e-6)
        assertEquals(3, second.positionSource)
        assertEquals(0, second.category)
        assertNull(second.altitudeM)
    }
}

