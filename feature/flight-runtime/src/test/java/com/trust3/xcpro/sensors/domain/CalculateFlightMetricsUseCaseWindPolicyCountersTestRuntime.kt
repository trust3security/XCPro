package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateFlightMetricsUseCaseWindPolicyCountersTest {

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
