package com.example.xcpro.sensors.domain

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseWindPolicyTest {

    @Test
    fun te_toggle_off_disables_te_even_with_valid_wind_airspeed() {
        val teValue = 2.75
        val useCase = newUseCase { teValue }
        val wind = WindState(
            vector = WindVector(east = 2.0, north = 0.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val altitude = 700.0

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(0L),
                currentTimeMillis = 0L,
                wallTimeMillis = 0L,
                gpsTimestampMillis = 0L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.6, altitude),
                varioGpsValue = 0.6,
                baroResult = null,
                windState = wind,
                varioValidUntil = 3_000L,
                isFlying = true, macCreadySetting = 0.0, autoMcEnabled = false, flightMode = FlightMode.CRUISE,
                teCompensationEnabled = false
            )
        )
        assertEquals("WIND", result.airspeedSourceLabel)
        assertTrue(result.teVario == null)
        assertTrue(result.varioSource != "TE")
    }

    @Test
    fun te_wind_low_confidence_falls_back_to_gps_airspeed() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 3.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 0.05
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("GPS", result.airspeedSourceLabel)
    }

    @Test
    fun te_wind_stale_vector_falls_back_to_gps_airspeed() {
        val useCase = newUseCase()
        val wind = WindState(
            vector = WindVector(east = 3.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = true,
            confidence = 1.0
        )

        val result = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = wind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        assertEquals("GPS", result.airspeedSourceLabel)
    }

    @Test
    fun wind_dropout_shorter_than_grace_keeps_wind_then_falls_back_after_grace() {
        val useCase = newUseCase()
        val goodWind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val poorWind = goodWind.copy(confidence = 0.02)

        val first = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = goodWind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertEquals("WIND", first.airspeedSourceLabel)

        val withinGrace = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = poorWind,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertEquals("WIND", withinGrace.airspeedSourceLabel)

        val afterGrace = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(3_700L),
                currentTimeMillis = 3_700L,
                wallTimeMillis = 3_700L,
                gpsTimestampMillis = 3_700L,
                deltaTimeSeconds = 1.7,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = poorWind,
                varioValidUntil = 4_700L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertEquals("GPS", afterGrace.airspeedSourceLabel)
    }

    @Test
    fun dwell_blocks_immediate_return_to_wind_after_fallback() {
        val useCase = newUseCase()
        val goodWind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val poorWind = goodWind.copy(confidence = 0.02)

        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = goodWind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = poorWind,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(3_700L),
                currentTimeMillis = 3_700L,
                wallTimeMillis = 3_700L,
                gpsTimestampMillis = 3_700L,
                deltaTimeSeconds = 1.7,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = poorWind,
                varioValidUntil = 4_700L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        val blocked = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(4_000L),
                currentTimeMillis = 4_000L,
                wallTimeMillis = 4_000L,
                gpsTimestampMillis = 4_000L,
                deltaTimeSeconds = 0.3,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = goodWind,
                varioValidUntil = 5_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertEquals("GPS", blocked.airspeedSourceLabel)

        val recovered = useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(6_300L),
                currentTimeMillis = 6_300L,
                wallTimeMillis = 6_300L,
                gpsTimestampMillis = 6_300L,
                deltaTimeSeconds = 2.3,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = goodWind,
                varioValidUntil = 7_300L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        assertEquals("WIND", recovered.airspeedSourceLabel)
    }

    @Test
    fun wind_decision_counters_track_accept_and_reject_paths() {
        val useCase = newUseCase()
        val acceptedWind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val lowConfidenceWind = acceptedWind.copy(confidence = 0.05)

        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = lowConfidenceWind,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        val counters = useCase.windAirspeedDecisionCounts()
        assertEquals(1L, counters.getOrDefault(WindAirspeedDecisionCode.WIND_ACCEPTED, 0L))
        assertEquals(1L, counters.getOrDefault(WindAirspeedDecisionCode.WIND_LOW_CONFIDENCE, 0L))
    }

    @Test
    fun wind_transition_counters_track_switches_and_hysteresis_events() {
        val useCase = newUseCase()
        val acceptedWind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val rejectedWind = acceptedWind.copy(confidence = 0.01)

        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = rejectedWind,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(3_600L),
                currentTimeMillis = 3_600L,
                wallTimeMillis = 3_600L,
                gpsTimestampMillis = 3_600L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = rejectedWind,
                varioValidUntil = 4_600L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(4_000L),
                currentTimeMillis = 4_000L,
                wallTimeMillis = 4_000L,
                gpsTimestampMillis = 4_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 5_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        val counters = useCase.windAirspeedTransitionCounts()
        assertTrue(counters.getOrDefault(AirspeedSourceTransitionEvent.GPS_TO_WIND, 0L) >= 1L)
        assertTrue(counters.getOrDefault(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD, 0L) >= 1L)
        assertTrue(counters.getOrDefault(AirspeedSourceTransitionEvent.WIND_TO_GPS, 0L) >= 1L)
        assertTrue(counters.getOrDefault(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK, 0L) >= 1L)
    }

    @Test
    fun wind_transition_counts_are_exact_for_single_episode_and_reset_clears_state() {
        val useCase = newUseCase()
        val acceptedWind = WindState(
            vector = WindVector(east = 2.0, north = 1.0),
            source = WindSource.MANUAL,
            quality = 5,
            stale = false,
            confidence = 1.0
        )
        val rejectedWind = acceptedWind.copy(confidence = 0.01)

        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(1_000L),
                currentTimeMillis = 1_000L,
                wallTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 0.1,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 2_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(2_000L),
                currentTimeMillis = 2_000L,
                wallTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 1.0,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = rejectedWind,
                varioValidUntil = 3_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(3_700L),
                currentTimeMillis = 3_700L,
                wallTimeMillis = 3_700L,
                gpsTimestampMillis = 3_700L,
                deltaTimeSeconds = 1.7,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = rejectedWind,
                varioValidUntil = 4_700L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(4_000L),
                currentTimeMillis = 4_000L,
                wallTimeMillis = 4_000L,
                gpsTimestampMillis = 4_000L,
                deltaTimeSeconds = 0.3,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 5_000L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )
        useCase.execute(
            FlightMetricsRequest(
                gps = gpsSample(4_200L),
                currentTimeMillis = 4_200L,
                wallTimeMillis = 4_200L,
                gpsTimestampMillis = 4_200L,
                deltaTimeSeconds = 0.2,
                varioResult = varioSample(0.4, 600.0),
                varioGpsValue = 0.4,
                baroResult = null,
                windState = acceptedWind,
                varioValidUntil = 5_200L,
                isFlying = true,
                macCreadySetting = 0.0,
                autoMcEnabled = false,
                flightMode = FlightMode.CRUISE
            )
        )

        val transitionCounts = useCase.windAirspeedTransitionCounts()
        assertEquals(1L, transitionCounts.getOrDefault(AirspeedSourceTransitionEvent.GPS_TO_WIND, 0L))
        assertEquals(1L, transitionCounts.getOrDefault(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD, 0L))
        assertEquals(1L, transitionCounts.getOrDefault(AirspeedSourceTransitionEvent.WIND_TO_GPS, 0L))
        assertEquals(1L, transitionCounts.getOrDefault(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK, 0L))

        useCase.reset()

        assertTrue(useCase.windAirspeedTransitionCounts().values.all { it == 0L })
        assertTrue(useCase.windAirspeedDecisionCounts().values.all { it == 0L })
    }
}
