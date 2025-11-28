package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class WindEstimatorTest {

    @Test
    fun fromWind_returns_tas_and_indicated() {
        val estimator = WindEstimator()
        // Ground: 10 m/s east, wind: 5 m/s headwind (toward west => east component -5)
        val vector = WindVector(east = -5.0, north = 0.0)
        val result = estimator.fromWind(
            gpsSpeed = 10.0,
            gpsBearingDeg = 90.0,
            altitudeMeters = 0.0,
            qnhHpa = 1013.25,
            windVector = vector
        )
        requireNotNull(result)
        assertEquals(5.0, result.trueMs, 0.001)
        assertEquals(5.0, result.indicatedMs, 0.001) // sea level density => IAS ~= TAS
    }

    @Test
    fun fromWind_returns_null_without_wind() {
        val estimator = WindEstimator()
        val result = estimator.fromWind(
            gpsSpeed = 10.0,
            gpsBearingDeg = 90.0,
            altitudeMeters = 0.0,
            qnhHpa = 1013.25,
            windVector = null
        )
        assertNull(result)
    }

    @Test
    fun fromPolarSink_uses_sink_provider() {
        val sinkProvider = mock<StillAirSinkProvider> {
            on { sinkAtSpeed(10.0) }.thenReturn(1.0)
            on { sinkAtSpeed(10.5) }.thenReturn(1.05)
            on { sinkAtSpeed(11.0) }.thenReturn(1.1)
        }
        val estimator = WindEstimator(sinkProvider)
        val result = estimator.fromPolarSink(
            netto = 0f,
            verticalSpeed = -1.05,
            altitudeMeters = 0.0,
            qnhHpa = 1013.25
        )
        // Should pick the closest sink curve (~1.05 m/s) => around 10.5 m/s TAS
        requireNotNull(result)
        assertEquals(AirspeedSource.POLAR_SINK, result.source)
    }
}
