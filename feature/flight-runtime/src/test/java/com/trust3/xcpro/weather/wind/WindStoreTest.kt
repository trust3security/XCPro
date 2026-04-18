package com.trust3.xcpro.weather.wind

import com.trust3.xcpro.weather.wind.domain.WindMeasurementList
import com.trust3.xcpro.weather.wind.domain.WindStore
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WindStoreTest {

    @Test
    fun `blends multiple measurements`() {
        val store = WindStore(WindMeasurementList(), altitudeDeltaThreshold = 50.0)
        val now = 1_000_000L

        val vector1 = WindVector(east = 4.0, north = 0.0)
        val vector2 = WindVector(east = 6.0, north = 2.0)

        store.slotMeasurement(
            clockMillis = now,
            timestampMillis = now,
            altitudeMeters = 1000.0,
            vector = vector1,
            quality = 5,
            source = WindSource.CIRCLING
        )
        store.slotMeasurement(
            clockMillis = now + 5000,
            timestampMillis = now + 5000,
            altitudeMeters = 1010.0,
            vector = vector2,
            quality = 4,
            source = WindSource.EKF
        )

        val evaluated = store.evaluate(now + 6000, 1005.0)
        assertNotNull(evaluated)

        val avgEast = (vector1.east + vector2.east) / 2
        val avgNorth = (vector1.north + vector2.north) / 2

        val result = requireNotNull(evaluated)
        assertEquals(avgEast, result.vector.east, 0.5)
        assertEquals(avgNorth, result.vector.north, 0.5)
    }
}
