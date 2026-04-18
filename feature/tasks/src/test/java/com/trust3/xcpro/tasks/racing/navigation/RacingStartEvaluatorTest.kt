package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.tasks.core.RacingPevCustomParams
import com.trust3.xcpro.tasks.core.RacingAltitudeReference
import com.trust3.xcpro.tasks.core.RacingStartCustomParams
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingStartEvaluatorTest {

    private val evaluator = RacingStartEvaluator()

    @Test
    fun updatePreStartAltitudeSatisfied_requiresGateOpenAndAltitudeBelowThreshold() {
        val rules = RacingStartCustomParams(
            gateOpenTimeMillis = 2_000L,
            preStartAltitudeMeters = 1_000.0
        )
        val beforeGateFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.0,
            timestampMillis = 1_500L,
            altitudeMslMeters = 900.0
        )
        val afterGateFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.0,
            timestampMillis = 2_500L,
            altitudeMslMeters = 900.0
        )

        val before = evaluator.updatePreStartAltitudeSatisfied(
            state = RacingNavigationState(),
            fix = beforeGateFix,
            rules = rules
        )
        val after = evaluator.updatePreStartAltitudeSatisfied(
            state = RacingNavigationState(),
            fix = afterGateFix,
            rules = rules
        )

        assertFalse(before)
        assertTrue(after)
    }

    @Test
    fun evaluateStrictCandidate_rejectsWhenGateClosed() {
        val rules = RacingStartCustomParams(gateCloseTimeMillis = 2_000L)
        val candidate = evaluator.evaluateStrictCandidate(
            state = RacingNavigationState(preStartAltitudeSatisfied = true),
            fix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 2_500L),
            timestampMillis = 2_500L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )

        assertFalse(candidate.isValid)
        assertTrue(candidate.rejectionReason == RacingStartRejectionReason.GATE_CLOSED)
    }

    @Test
    fun evaluateStrictCandidate_addsMaxAltitudeAndGroundspeedPenalties() {
        val rules = RacingStartCustomParams(
            maxStartAltitudeMeters = 2_000.0,
            maxStartGroundspeedMs = 50.0
        )
        val candidate = evaluator.evaluateStrictCandidate(
            state = RacingNavigationState(preStartAltitudeSatisfied = true),
            fix = RacingNavigationFix(
                lat = 0.0,
                lon = 0.0,
                timestampMillis = 2_500L,
                altitudeMslMeters = 2_200.0,
                groundSpeedMs = 60.0
            ),
            timestampMillis = 2_500L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )

        assertTrue(candidate.isValid)
        assertTrue(candidate.penaltyFlags.contains(RacingStartPenaltyFlag.MAX_START_ALTITUDE_EXCEEDED))
        assertTrue(candidate.penaltyFlags.contains(RacingStartPenaltyFlag.MAX_START_GROUNDSPEED_EXCEEDED))
    }

    @Test
    fun evaluateStrictCandidate_pevMaxPressesLimitIsEnforced() {
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 0,
                startWindowMinutes = 1,
                maxPressesPerLaunch = 1,
                pressTimestampsMillis = listOf(0L, 60_000L)
            )
        )
        val candidate = evaluator.evaluateStrictCandidate(
            state = RacingNavigationState(preStartAltitudeSatisfied = true),
            fix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 61_000L),
            timestampMillis = 61_000L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )

        assertTrue(candidate.penaltyFlags.contains(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW))
    }

    @Test
    fun evaluateStrictCandidate_pevDedupeWindowIsEnforced() {
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 0,
                startWindowMinutes = 1,
                dedupeSeconds = 30L,
                pressTimestampsMillis = listOf(0L, 10_000L)
            )
        )
        val candidate = evaluator.evaluateStrictCandidate(
            state = RacingNavigationState(preStartAltitudeSatisfied = true),
            fix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 65_000L),
            timestampMillis = 65_000L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )

        assertTrue(candidate.penaltyFlags.contains(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW))
    }

    @Test
    fun evaluateStrictCandidate_cylinderPevMinIntervalAppliedButLineIgnoresIt() {
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 0,
                startWindowMinutes = 6,
                dedupeSeconds = 0L,
                minIntervalMinutes = 10,
                pressTimestampsMillis = listOf(0L, 300_000L)
            )
        )
        val state = RacingNavigationState(preStartAltitudeSatisfied = true)
        val fix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 540_000L)

        val lineCandidate = evaluator.evaluateStrictCandidate(
            state = state,
            fix = fix,
            timestampMillis = 540_000L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )
        val cylinderCandidate = evaluator.evaluateStrictCandidate(
            state = state,
            fix = fix,
            timestampMillis = 540_000L,
            startType = RacingStartPointType.START_CYLINDER,
            rules = rules
        )

        assertFalse(lineCandidate.penaltyFlags.contains(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW))
        assertTrue(cylinderCandidate.penaltyFlags.contains(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW))
    }

    @Test
    fun updatePreStartAltitudeSatisfied_usesQnhReferenceWhenConfigured() {
        val rules = RacingStartCustomParams(
            preStartAltitudeMeters = 1_000.0,
            altitudeReference = RacingAltitudeReference.QNH
        )
        val fix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.0,
            timestampMillis = 2_000L,
            altitudeMslMeters = 1_200.0,
            altitudeQnhMeters = 900.0
        )

        val satisfied = evaluator.updatePreStartAltitudeSatisfied(
            state = RacingNavigationState(),
            fix = fix,
            rules = rules
        )

        assertTrue(satisfied)
    }

    @Test
    fun evaluateStrictCandidate_qnhReferenceFallbackAddsExplicitPenalty() {
        val rules = RacingStartCustomParams(
            altitudeReference = RacingAltitudeReference.QNH,
            maxStartAltitudeMeters = 2_500.0
        )
        val candidate = evaluator.evaluateStrictCandidate(
            state = RacingNavigationState(preStartAltitudeSatisfied = true),
            fix = RacingNavigationFix(
                lat = 0.0,
                lon = 0.0,
                timestampMillis = 2_500L,
                altitudeMslMeters = 2_000.0,
                altitudeQnhMeters = null
            ),
            timestampMillis = 2_500L,
            startType = RacingStartPointType.START_LINE,
            rules = rules
        )

        assertTrue(candidate.penaltyFlags.contains(RacingStartPenaltyFlag.ALTITUDE_REFERENCE_FALLBACK_TO_MSL))
    }
}
