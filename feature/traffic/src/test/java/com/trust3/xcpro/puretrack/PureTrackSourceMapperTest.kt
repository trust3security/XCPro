package com.trust3.xcpro.puretrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PureTrackSourceMapperTest {
    private val mapper = PureTrackSourceMapper()

    @Test
    fun map_knownSources() {
        assertEquals(PureTrackSourceType(16, "puretrack"), mapper.map(16))
        assertEquals(PureTrackSourceType(35, "OGN ICAO"), mapper.map(35))
        assertEquals(PureTrackSourceType(51, "Garmin Watch"), mapper.map(51))
    }

    @Test
    fun map_unknownOrNullSourceReturnsNull() {
        assertNull(mapper.map(999))
        assertNull(mapper.map(null))
    }
}
