package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindEstimatorTest {

    @Test
    fun fromWind_returns_tas_and_indicated() {
        val estimator = WindEstimator()
        // Ground: 10 m/s east, wind: 5 m/s headwind (wind TO west => east component -5)
        val vector = WindVector(east = -5.0, north = 0.0)
        val result = estimator.fromWind(
            gpsSpeed = 10.0,
            gpsBearingDeg = 90.0,
            altitudeMeters = 0.0,
            qnhHpa = 1013.25,
            windVector = vector
        )
        requireNotNull(result)
        assertEquals(15.0, result.trueMs, 0.001)
        assertEquals(15.0, result.indicatedMs, 0.001) // sea level density => IAS ~= TAS
    }

    @Test
    fun fromWind_returns_tas_for_crosswind() {
        val estimator = WindEstimator()
        // Ground: 10 m/s north, wind: 10 m/s crosswind TO east
        val vector = WindVector(east = 10.0, north = 0.0)
        val result = estimator.fromWind(
            gpsSpeed = 10.0,
            gpsBearingDeg = 0.0,
            altitudeMeters = 0.0,
            qnhHpa = 1013.25,
            windVector = vector
        )
        requireNotNull(result)
        // air = ground - wind_to => (-10 east, +10 north) => TAS = sqrt(200)
        assertEquals(kotlin.math.sqrt(200.0), result.trueMs, 0.001)
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
    fun fromWind_higher_qnh_increases_indicated_for_same_tas() {
        val estimator = WindEstimator()
        val noWind = WindVector(east = 0.0, north = 0.0)
        val lowQnh = estimator.fromWind(
            gpsSpeed = 30.0,
            gpsBearingDeg = 0.0,
            altitudeMeters = 2000.0,
            qnhHpa = 990.0,
            windVector = noWind
        )
        val highQnh = estimator.fromWind(
            gpsSpeed = 30.0,
            gpsBearingDeg = 0.0,
            altitudeMeters = 2000.0,
            qnhHpa = 1030.0,
            windVector = noWind
        )
        requireNotNull(lowQnh)
        requireNotNull(highQnh)
        assertEquals(lowQnh.trueMs, highQnh.trueMs, 1e-6)
        assertTrue(highQnh.indicatedMs > lowQnh.indicatedMs)
    }
}
