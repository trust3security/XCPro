package com.trust3.xcpro.puretrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PureTrackTypeMapperTest {
    private val mapper = PureTrackTypeMapper()

    @Test
    fun map_knownAirType() {
        val mapped = mapper.map(3)

        assertEquals(PureTrackObjectType(3, PureTrackCategory.AIR, "Helicopter"), mapped)
    }

    @Test
    fun map_knownOtherType() {
        val mapped = mapper.map(20)

        assertEquals(PureTrackObjectType(20, PureTrackCategory.OTHER, "Person"), mapped)
    }

    @Test
    fun map_unknownOrNullTypeReturnsNull() {
        assertNull(mapper.map(999))
        assertNull(mapper.map(null))
    }
}
