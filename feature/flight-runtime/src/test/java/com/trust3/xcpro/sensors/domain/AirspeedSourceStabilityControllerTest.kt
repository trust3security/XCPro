package com.trust3.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirspeedSourceStabilityControllerTest {

    private val accepted = WindAirspeedDecision(
        eligible = true,
        code = WindAirspeedDecisionCode.WIND_ACCEPTED
    )
    private val rejected = WindAirspeedDecision(
        eligible = false,
        code = WindAirspeedDecisionCode.WIND_LOW_CONFIDENCE
    )

    @Test
    fun transient_dropout_within_grace_keeps_wind_source() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        val first = controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.WIND_VECTOR, first?.source)

        val graceHold = controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.WIND_VECTOR, graceHold?.source)
    }

    @Test
    fun sustained_dropout_past_grace_falls_back_to_gps() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )

        val fallback = controller.select(
            currentTimeMillis = 3_600L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.GPS_GROUND, fallback?.source)
    }

    @Test
    fun dwell_blocks_immediate_bounce_back_to_wind() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 3_600L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = gps
        )

        val blocked = controller.select(
            currentTimeMillis = 4_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.GPS_GROUND, blocked?.source)

        val recovered = controller.select(
            currentTimeMillis = 6_200L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.WIND_VECTOR, recovered?.source)
    }

    @Test
    fun returns_null_after_grace_when_no_gps_fallback_available() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = null
        )
        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = null
        )

        val noFallback = controller.select(
            currentTimeMillis = 3_600L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = null
        )
        assertNull(noFallback)
    }

    @Test
    fun transition_events_include_switches_grace_and_dwell_blocks() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertTrue(controller.drainTransitionEvents().contains(AirspeedSourceTransitionEvent.GPS_TO_WIND))

        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertTrue(controller.drainTransitionEvents().contains(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD))

        controller.select(
            currentTimeMillis = 3_600L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = gps
        )
        assertTrue(controller.drainTransitionEvents().contains(AirspeedSourceTransitionEvent.WIND_TO_GPS))

        controller.select(
            currentTimeMillis = 4_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        assertTrue(controller.drainTransitionEvents().contains(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK))
    }

    @Test
    fun grace_hold_event_emits_once_per_dropout_episode() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.drainTransitionEvents()

        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        val first = controller.drainTransitionEvents()
        assertTrue(first.contains(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD))

        controller.select(
            currentTimeMillis = 2_200L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 2_400L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        val repeated = controller.drainTransitionEvents()
        assertFalse(repeated.contains(AirspeedSourceTransitionEvent.WIND_GRACE_HOLD))
    }

    @Test
    fun missing_wind_candidate_bypasses_grace_and_falls_back_immediately() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.drainTransitionEvents()

        val fallback = controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = gps
        )
        assertEquals(AirspeedSource.GPS_GROUND, fallback?.source)
        val transitions = controller.drainTransitionEvents()
        assertTrue(transitions.contains(AirspeedSourceTransitionEvent.WIND_TO_GPS))
    }

    @Test
    fun dwell_block_event_emits_once_per_contiguous_block_episode() {
        val controller = AirspeedSourceStabilityController(
            minDwellMs = 2_500L,
            transientGraceMs = 1_500L
        )
        val gps = gpsCandidate(20.0)
        val wind = windCandidate(24.0)

        controller.select(
            currentTimeMillis = 1_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 2_000L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.select(
            currentTimeMillis = 3_600L,
            windDecision = rejected,
            windCandidate = null,
            gpsCandidate = gps
        )
        controller.drainTransitionEvents()

        controller.select(
            currentTimeMillis = 4_000L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        val firstBlock = controller.drainTransitionEvents()
        assertTrue(firstBlock.contains(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK))

        controller.select(
            currentTimeMillis = 4_200L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        val repeated = controller.drainTransitionEvents()
        assertFalse(repeated.contains(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK))

        controller.select(
            currentTimeMillis = 4_300L,
            windDecision = rejected,
            windCandidate = wind,
            gpsCandidate = gps
        )
        controller.drainTransitionEvents()

        controller.select(
            currentTimeMillis = 4_400L,
            windDecision = accepted,
            windCandidate = wind,
            gpsCandidate = gps
        )
        val nextEpisode = controller.drainTransitionEvents()
        assertTrue(nextEpisode.contains(AirspeedSourceTransitionEvent.WIND_DWELL_BLOCK))
    }

    private fun windCandidate(speed: Double): AirspeedEstimate = AirspeedEstimate(
        indicatedMs = speed - 2.0,
        trueMs = speed,
        source = AirspeedSource.WIND_VECTOR
    )

    private fun gpsCandidate(speed: Double): AirspeedEstimate = AirspeedEstimate(
        indicatedMs = speed,
        trueMs = speed,
        source = AirspeedSource.GPS_GROUND
    )
}
