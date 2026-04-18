package com.trust3.xcpro.weather.wind

import com.trust3.xcpro.weather.wind.domain.WindEkfUseCase
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.GpsSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindEkfUseCaseTest {

    @Test
    fun `ekf drops when gps timestamp repeats`() {
        val ekf = WindEkfUseCase()
        val gps = gpsSample(timestampMillis = 1_000L)
        val airspeed = airspeedSample(timestampMillis = 1_000L)

        ekf.update(
            gps = gps,
            airspeed = airspeed,
            isCircling = false,
            turnRateRad = null,
            gLoad = null
        )

        val result = ekf.update(
            gps = gps,
            airspeed = airspeed,
            isCircling = false,
            turnRateRad = null,
            gLoad = null
        )

        assertNull(result)
        assertEquals(WindEkfUseCase.DropReason.NO_UPDATE, ekf.lastRejectReason)
        assertEquals(1_000L, ekf.lastRejectTimestamp)
    }

    @Test
    fun `ekf drops when airspeed timestamp repeats`() {
        val ekf = WindEkfUseCase()
        val gps1 = gpsSample(timestampMillis = 1_000L)
        val airspeed1 = airspeedSample(timestampMillis = 1_000L)

        ekf.update(
            gps = gps1,
            airspeed = airspeed1,
            isCircling = false,
            turnRateRad = null,
            gLoad = null
        )

        val gps2 = gpsSample(timestampMillis = 1_500L)
        val airspeed2 = airspeedSample(timestampMillis = 1_000L)
        val result = ekf.update(
            gps = gps2,
            airspeed = airspeed2,
            isCircling = false,
            turnRateRad = null,
            gLoad = null
        )

        assertNull(result)
        assertEquals(WindEkfUseCase.DropReason.NO_UPDATE, ekf.lastRejectReason)
        assertEquals(1_500L, ekf.lastRejectTimestamp)
    }

    private fun gpsSample(timestampMillis: Long): GpsSample =
        GpsSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = 1000.0,
            groundSpeedMs = 25.0,
            trackRad = 0.0,
            timestampMillis = timestampMillis,
            clockMillis = timestampMillis
        )

    private fun airspeedSample(timestampMillis: Long): AirspeedSample =
        AirspeedSample(
            trueMs = 30.0,
            indicatedMs = 30.0,
            timestampMillis = timestampMillis,
            clockMillis = timestampMillis,
            valid = true
        )
}
