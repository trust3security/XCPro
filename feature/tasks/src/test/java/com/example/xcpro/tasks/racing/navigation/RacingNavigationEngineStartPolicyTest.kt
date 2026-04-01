package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEngineStartPolicyTest {

    private val engine = RacingNavigationEngine()

    @Test
    fun startBeforeGateOpen_isRejected_andLaterToleranceStartStaysPending() {
        val task = buildLineStartTask()
        val rules = RacingStartCustomParams(
            gateOpenTimeMillis = 1_500L,
            toleranceMeters = 500.0
        )
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)
        assertNull(firstDecision.event)

        val earlyCrossFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 1_200L)
        val rejectedDecision = engine.step(task, firstDecision.state, earlyCrossFix, rules)
        assertEquals(RacingNavigationEventType.START_REJECTED, rejectedDecision.event?.type)
        assertEquals(
            RacingStartRejectionReason.GATE_NOT_OPEN,
            rejectedDecision.event?.startCandidate?.rejectionReason
        )
        assertEquals(1, rejectedDecision.state.startCandidates.size)
        assertEquals(RacingNavigationStatus.PENDING_START, rejectedDecision.state.status)

        val toleranceFix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 2_000L)
        val startedDecision = engine.step(task, rejectedDecision.state, toleranceFix, rules)
        assertNull(startedDecision.event)
        assertEquals(
            RacingStartCandidateType.TOLERANCE,
            startedDecision.state.startCandidates.last().candidateType
        )
        assertEquals(2, startedDecision.state.startCandidates.size)
        assertEquals(RacingNavigationStatus.PENDING_START, startedDecision.state.status)
        assertNull(startedDecision.state.selectedStartCandidateIndex)
        assertNull(startedDecision.state.creditedStart)
    }

    @Test
    fun startBeforeGateOpen_whileDisarmed_isIgnoredWithoutRejectedEventOrCandidate() {
        val task = buildLineStartTask()
        val rules = RacingStartCustomParams(gateOpenTimeMillis = 1_500L)
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules, startArmed = false)

        val earlyCrossFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 1_200L)
        val ignoredDecision = engine.step(task, firstDecision.state, earlyCrossFix, rules, startArmed = false)

        assertNull(ignoredDecision.event)
        assertEquals(RacingNavigationStatus.PENDING_START, ignoredDecision.state.status)
        assertTrue(ignoredDecision.state.startCandidates.isEmpty())
        assertNull(ignoredDecision.state.creditedStart)
    }

    @Test
    fun lineStartWrongDirection_emitsRejectedStart() {
        val task = buildLineStartTask()
        val outsideFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), outsideFix)

        val insideFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 2_000L)
        val rejectedDecision = engine.step(task, firstDecision.state, insideFix)

        assertEquals(RacingNavigationEventType.START_REJECTED, rejectedDecision.event?.type)
        assertEquals(
            RacingStartRejectionReason.WRONG_DIRECTION,
            rejectedDecision.event?.startCandidate?.rejectionReason
        )
    }

    @Test
    fun strictStartWithoutPreStartAltitudeEvidence_marksPenalty() {
        val task = buildLineStartTask()
        val rules = RacingStartCustomParams(preStartAltitudeMeters = 1_000.0)
        val firstFix = RacingNavigationFix(
            lat = 0.0,
            lon = -0.001,
            timestampMillis = 1_000L,
            altitudeMslMeters = 1_200.0
        )
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)
        val secondFix = RacingNavigationFix(
            lat = 0.0,
            lon = 0.001,
            timestampMillis = 2_000L,
            altitudeMslMeters = 1_150.0
        )

        val secondDecision = engine.step(task, firstDecision.state, secondFix, rules)
        val penalties = secondDecision.event?.startCandidate?.penaltyFlags ?: emptySet()

        assertTrue(penalties.contains(RacingStartPenaltyFlag.PRE_START_ALTITUDE_NOT_SATISFIED))
    }

    @Test
    fun strictStartWithValidPevWindow_hasNoPevPenalty() {
        val task = buildLineStartTask()
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 5,
                startWindowMinutes = 5,
                pressTimestampsMillis = listOf(0L)
            )
        )
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 299_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)
        val secondFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 301_000L)

        val secondDecision = engine.step(task, firstDecision.state, secondFix, rules)
        val penalties = secondDecision.event?.startCandidate?.penaltyFlags ?: emptySet()

        assertFalse(penalties.contains(RacingStartPenaltyFlag.PEV_MISSING))
        assertFalse(penalties.contains(RacingStartPenaltyFlag.PEV_OUTSIDE_WINDOW))
    }

    @Test
    fun strictStartWithoutPevPresses_marksPevMissingPenalty() {
        val task = buildLineStartTask()
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 5,
                startWindowMinutes = 5
            )
        )
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)
        val secondFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L)

        val secondDecision = engine.step(task, firstDecision.state, secondFix, rules)
        val penalties = secondDecision.event?.startCandidate?.penaltyFlags ?: emptySet()

        assertTrue(penalties.contains(RacingStartPenaltyFlag.PEV_MISSING))
    }

    @Test
    fun boundaryFixAfterTaskActivation_doesNotStartUntilInteriorEvidenceExists() {
        val task = buildLineStartTask()
        val boundaryFix = RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 1_000L)

        val firstDecision = engine.step(task, RacingNavigationState(), boundaryFix)

        assertNull(firstDecision.event)
        assertFalse(firstDecision.state.hasObservedRequiredApproachSideForActiveLeg)

        val outsideFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, outsideFix)

        assertNull(secondDecision.event)
        assertEquals(RacingNavigationStatus.PENDING_START, secondDecision.state.status)
        assertEquals(0, secondDecision.state.currentLegIndex)
    }

    @Test
    fun startSelection_prefersExistingBetterCandidateOverLatestPenalizedCandidate() {
        val task = buildLineStartTask()
        val existingBest = RacingStartCandidate(
            timestampMillis = 1_000L,
            candidateType = RacingStartCandidateType.STRICT,
            isValid = true,
            penaltyFlags = emptySet(),
            crossingEvidence = strictCrossingEvidence()
        )
        val previousState = RacingNavigationState(
            status = RacingNavigationStatus.PENDING_START,
            lastFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_500L),
            startCandidates = listOf(existingBest),
            hasObservedRequiredApproachSideForActiveLeg = true
        )
        val rules = RacingStartCustomParams(
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 5,
                startWindowMinutes = 5
            )
        )

        val crossingFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L)
        val decision = engine.step(task, previousState, crossingFix, rules)

        assertEquals(RacingNavigationEventType.START, decision.event?.type)
        assertEquals(0, decision.state.selectedStartCandidateIndex)
        assertEquals(existingBest, decision.event?.startCandidate)
        assertEquals(existingBest.timestampMillis, decision.event?.timestampMillis)
        assertEquals(existingBest.timestampMillis, decision.state.creditedStart?.timestampMillis)
    }

    private fun strictCrossingEvidence(): RacingBoundaryCrossingEvidence =
        RacingBoundaryCrossingEvidence(
            crossingPoint = RacingBoundaryPoint(0.0, 0.0),
            insideAnchor = RacingBoundaryPoint(0.0, -0.0005),
            outsideAnchor = RacingBoundaryPoint(0.0, 0.0005),
            evidenceSource = RacingBoundaryEvidenceSource.LINE_INTERSECTION
        )
}
