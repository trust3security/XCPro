package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseExternalAirspeedTest {

    @Test
    fun fresh_external_airspeed_takes_priority_over_wind_and_gps() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(10_000L),
                currentTimeMillis = 10_000L,
                wallTimeMillis = 10_000L,
                gpsTimestampMillis = 10_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 9_800L
                ),
                varioValidUntil = 12_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertEquals(24.0, result.indicatedAirspeedMs, 1e-6)
        assertEquals(26.0, result.trueAirspeedMs, 1e-6)
    }

    @Test
    fun stale_external_airspeed_falls_back_to_wind_solution() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(10_000L),
                currentTimeMillis = 10_000L,
                wallTimeMillis = 10_000L,
                gpsTimestampMillis = 10_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                externalAirspeedSample = airspeedSample(
                    trueMs = 30.0,
                    indicatedMs = 28.0,
                    clockMillis = 6_000L
                ),
                varioValidUntil = 12_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("WIND", result.airspeedSourceLabel)
    }

    @Test
    fun timestamp_freshness_fallback_accepts_external_sample_when_clock_is_missing() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(10_000L),
                currentTimeMillis = 10_000L,
                wallTimeMillis = 10_000L,
                gpsTimestampMillis = 10_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                externalAirspeedSample = airspeedSample(
                    trueMs = 27.0,
                    indicatedMs = 25.0,
                    clockMillis = 0L,
                    timestampMillis = 9_800L
                ),
                varioValidUntil = 12_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertEquals(25.0, result.indicatedAirspeedMs, 1e-6)
        assertEquals(27.0, result.trueAirspeedMs, 1e-6)
    }

    @Test
    fun invalid_external_sample_is_ignored() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(10_000L),
                currentTimeMillis = 10_000L,
                wallTimeMillis = 10_000L,
                gpsTimestampMillis = 10_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 9_800L,
                    valid = false
                ),
                varioValidUntil = 12_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("WIND", result.airspeedSourceLabel)
    }

    @Test
    fun tas_only_external_sample_keeps_sensor_tas_without_fabricating_ias() {
        val useCase = newUseCase()

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(10_000L),
                currentTimeMillis = 10_000L,
                wallTimeMillis = 10_000L,
                gpsTimestampMillis = 10_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = null,
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = Double.NaN,
                    clockMillis = 9_800L
                ),
                varioValidUntil = 12_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("SENSOR", result.airspeedSourceLabel)
        assertEquals(26.0, result.trueAirspeedMs, 1e-6)
        assertTrue(result.indicatedAirspeedMs.isNaN())
        assertTrue(result.tasValid)
    }
}
