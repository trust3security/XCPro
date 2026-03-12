package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEngineTest {

    private val engine = RacingNavigationEngine()

    @Test
    fun startLineExitStartsTaskWithinFixWindow() {
        val task = buildLineStartTask()
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix)
        assertNull(firstDecision.event)

        val secondFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, secondFix)
        val event = secondDecision.event
        assertNotNull(event)
        assertEquals(RacingNavigationEventType.START, event?.type)
        assertEquals(1, secondDecision.state.currentLegIndex)
        assertTrue(
            "Start time should be within fix window",
            event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis
        )
        assertNotNull(event.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.LINE_INTERSECTION,
            event.crossingEvidence?.evidenceSource
        )
    }

    @Test
    fun startLineExitWithFarFix_stillStartsWhenIntersectionEvidenceExists() {
        val task = buildLineStartTask()
        val insideFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), insideFix)

        val farOutsideFix = RacingNavigationFix(lat = 0.0, lon = 0.2, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, farOutsideFix)

        assertEquals(RacingNavigationEventType.START, secondDecision.event?.type)
        assertEquals(1, secondDecision.state.currentLegIndex)
        assertNotNull(secondDecision.event?.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.LINE_INTERSECTION,
            secondDecision.event?.crossingEvidence?.evidenceSource
        )
    }

    @Test
    fun turnpointCylinderEntryAdvancesLeg() {
        val task = buildLineStartTask()
        val turnpoint = task.waypoints[1]
        val outsideFix = RacingNavigationFix(
            lat = turnpoint.lat,
            lon = turnpoint.lon + 0.007,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = turnpoint.lat,
            lon = turnpoint.lon + 0.002,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.TURNPOINT, decision.event?.type)
        assertTrue(
            "Transition time should be within fix window",
            decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis
        )
        assertEquals(2, decision.state.currentLegIndex)
        assertNotNull(decision.event?.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.CYLINDER_INTERSECTION,
            decision.event?.crossingEvidence?.evidenceSource
        )
    }

    @Test
    fun finishCylinderEntryCompletesTask() {
        val task = buildLineStartTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.05,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertTrue(
            "Finish time should be within fix window",
            decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis
        )
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
        assertNotNull(decision.event?.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.CYLINDER_INTERSECTION,
            decision.event?.crossingEvidence?.evidenceSource
        )
        assertEquals(RacingNavigationStatus.FINISHED, decision.state.status)
    }

    @Test
    fun finishLineWrongDirection_doesNotFinishTask() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val insideEast = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = insideEast
        )
        val outsideWest = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 2_000L
        )

        val decision = engine.step(task, state, outsideWest)

        assertNull(decision.event)
        assertEquals(RacingNavigationStatus.IN_PROGRESS, decision.state.status)
    }

    @Test
    fun finishLineDirectionOverride_enablesConfiguredCrossingDirection() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val southFix = RacingNavigationFix(
            lat = finish.lat - 0.004,
            lon = finish.lon,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = southFix
        )
        val northFix = RacingNavigationFix(
            lat = finish.lat + 0.004,
            lon = finish.lon,
            timestampMillis = 2_000L
        )
        val finishRules = RacingFinishCustomParams(directionOverrideDegrees = 0.0)

        val decision = engine.step(task, state, northFix, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
        assertNotNull(decision.event?.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.LINE_INTERSECTION,
            decision.event?.crossingEvidence?.evidenceSource
        )
    }

    @Test
    fun finishClose_emitsOutlandedAtCloseFromLastFixBeforeClose() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val beforeCloseFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_400L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = beforeCloseFix
        )
        val afterCloseFix = RacingNavigationFix(
            lat = finish.lat + 0.004,
            lon = finish.lon,
            timestampMillis = 2_000L
        )
        val finishRules = RacingFinishCustomParams(closeTimeMillis = 1_500L)

        val decision = engine.step(task, state, afterCloseFix, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertEquals(RacingFinishOutcome.OUTLANDED_AT_CLOSE, decision.event?.finishOutcome)
        assertEquals(1_400L, decision.event?.timestampMillis)
        assertEquals(RacingNavigationStatus.FINISHED, decision.state.status)
    }

    @Test
    fun finishClose_whenCrossingOccursBeforeCloseWithinFixWindow_finishesNormally() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val outsideWestBeforeClose = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_400L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideWestBeforeClose
        )
        val insideEastAfterClose = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 1_600L
        )
        val finishRules = RacingFinishCustomParams(closeTimeMillis = 1_600L)

        val decision = engine.step(task, state, insideEastAfterClose, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
        assertTrue(decision.event!!.timestampMillis in 1_400L..1_600L)
        assertTrue(decision.event.timestampMillis <= 1_600L)
        assertNotNull(decision.event.crossingEvidence)
        assertEquals(
            com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource.LINE_INTERSECTION,
            decision.event.crossingEvidence?.evidenceSource
        )
        assertEquals(RacingNavigationStatus.FINISHED, decision.state.status)
    }

    @Test
    fun finishMinAltitude_withoutStraightInException_rejectsFinish() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_000L,
            altitudeMslMeters = 800.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 2_000L,
            altitudeMslMeters = 850.0
        )
        val finishRules = RacingFinishCustomParams(
            minAltitudeMeters = 900.0,
            altitudeReference = RacingAltitudeReference.MSL
        )

        val decision = engine.step(task, state, insideFix, finishRules = finishRules)

        assertNull(decision.event)
        assertEquals(RacingNavigationStatus.IN_PROGRESS, decision.state.status)
    }

    @Test
    fun finishMinAltitude_missingAltitudeEvidence_rejectsFinish() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 2_000L
        )
        val finishRules = RacingFinishCustomParams(
            minAltitudeMeters = 900.0,
            altitudeReference = RacingAltitudeReference.MSL
        )

        val decision = engine.step(task, state, insideFix, finishRules = finishRules)

        assertNull(decision.event)
        assertEquals(RacingNavigationStatus.IN_PROGRESS, decision.state.status)
    }

    @Test
    fun finishMinAltitude_withStraightInException_allowsFinish() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_000L,
            altitudeMslMeters = 800.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 2_000L,
            altitudeMslMeters = 850.0
        )
        val finishRules = RacingFinishCustomParams(
            minAltitudeMeters = 900.0,
            altitudeReference = RacingAltitudeReference.MSL,
            allowStraightInBelowMinAltitude = true
        )

        val decision = engine.step(task, state, insideFix, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertTrue(decision.event?.finishUsedStraightInException == true)
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
    }

    @Test
    fun finishMinAltitude_qnhReference_usesQnhAltitudeForValidation() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon - 0.004,
            timestampMillis = 1_000L,
            altitudeMslMeters = 850.0,
            altitudeQnhMeters = 950.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.004,
            timestampMillis = 2_000L,
            altitudeMslMeters = 850.0,
            altitudeQnhMeters = 950.0
        )
        val finishRules = RacingFinishCustomParams(
            minAltitudeMeters = 900.0,
            altitudeReference = RacingAltitudeReference.QNH
        )

        val decision = engine.step(task, state, insideFix, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, decision.event?.type)
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
    }

    @Test
    fun postFinishLanding_withoutDelay_setsLandedOutcome() {
        val task = buildLineStartTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.05,
            timestampMillis = 1_000L,
            groundSpeedMs = 30.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 2_000L,
            groundSpeedMs = 25.0
        )
        val finishRules = RacingFinishCustomParams(
            requireLandWithoutDelay = true,
            landWithoutDelayWindowSeconds = 120L,
            landingSpeedThresholdMs = 4.0,
            landingHoldSeconds = 20L
        )

        val finishedDecision = engine.step(task, state, insideFix, finishRules = finishRules)
        assertEquals(RacingFinishOutcome.LANDING_PENDING, finishedDecision.event?.finishOutcome)

        val stoppedFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 10_000L,
            groundSpeedMs = 2.0
        )
        val pendingDecision = engine.step(task, finishedDecision.state, stoppedFix, finishRules = finishRules)
        assertEquals(RacingFinishOutcome.LANDING_PENDING, pendingDecision.state.finishOutcome)

        val landedFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 35_000L,
            groundSpeedMs = 2.0
        )
        val landedDecision = engine.step(task, pendingDecision.state, landedFix, finishRules = finishRules)
        assertEquals(RacingFinishOutcome.LANDED_WITHOUT_DELAY, landedDecision.state.finishOutcome)
    }

    @Test
    fun postFinishLanding_afterWindow_setsDelayViolationOutcome() {
        val task = buildLineStartTask()
        val finish = task.waypoints.last()
        val outsideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.05,
            timestampMillis = 1_000L,
            groundSpeedMs = 30.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 2_000L,
            groundSpeedMs = 25.0
        )
        val finishRules = RacingFinishCustomParams(
            requireLandWithoutDelay = true,
            landWithoutDelayWindowSeconds = 30L,
            landingSpeedThresholdMs = 4.0,
            landingHoldSeconds = 20L
        )

        val finishedDecision = engine.step(task, state, insideFix, finishRules = finishRules)
        val lateFix = RacingNavigationFix(
            lat = finish.lat,
            lon = finish.lon + 0.005,
            timestampMillis = 40_000L,
            groundSpeedMs = 20.0
        )
        val lateDecision = engine.step(task, finishedDecision.state, lateFix, finishRules = finishRules)

        assertEquals(RacingFinishOutcome.LANDING_DELAY_VIOLATION, lateDecision.state.finishOutcome)
    }

    @Test
    fun contestBoundaryStopPlusFive_finishesWithoutLineCrossing() {
        val task = buildFinishLineTask()
        val finish = task.waypoints.last()
        val stoppedPoint = RacingNavigationFix(
            lat = finish.lat + 0.004,
            lon = finish.lon,
            timestampMillis = 1_000L,
            groundSpeedMs = 1.0
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = RacingNavigationFix(
                lat = stoppedPoint.lat,
                lon = stoppedPoint.lon,
                timestampMillis = 0L,
                groundSpeedMs = 1.0
            )
        )
        val finishRules = RacingFinishCustomParams(
            contestBoundaryRadiusMeters = 2_000.0,
            stopPlusFiveEnabled = true,
            stopPlusFiveMinutes = 5L,
            landingSpeedThresholdMs = 2.0
        )

        val firstDecision = engine.step(task, state, stoppedPoint, finishRules = finishRules)
        assertNull(firstDecision.event)

        val afterFiveMinutesFix = stoppedPoint.copy(timestampMillis = 301_000L)
        val finalDecision = engine.step(task, firstDecision.state, afterFiveMinutesFix, finishRules = finishRules)

        assertEquals(RacingNavigationEventType.FINISH, finalDecision.event?.type)
        assertEquals(RacingFinishOutcome.CONTEST_BOUNDARY_STOP_PLUS_FIVE, finalDecision.event?.finishOutcome)
        assertEquals(301_000L, finalDecision.event?.timestampMillis)
    }

    @Test
    fun keyholeSectorEntryAdvancesLeg() {
        val task = buildKeyholeTask()
        val keyhole = task.waypoints[1]
        val outsideFix = RacingNavigationFix(
            lat = keyhole.lat,
            lon = keyhole.lon - 0.02,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = outsideFix
        )
        val insideFix = RacingNavigationFix(
            lat = keyhole.lat,
            lon = keyhole.lon + 0.02,
            timestampMillis = 2_000L
        )
        val decision = engine.step(task, state, insideFix)
        assertEquals(RacingNavigationEventType.TURNPOINT, decision.event?.type)
        assertEquals(2, decision.state.currentLegIndex)
    }

    @Test
    fun startBeforeGateOpen_isRejected_andLaterToleranceStartIsAccepted() {
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
        assertEquals(RacingNavigationEventType.START, startedDecision.event?.type)
        assertEquals(RacingStartCandidateType.TOLERANCE, startedDecision.event?.startCandidate?.candidateType)
        assertEquals(2, startedDecision.state.startCandidates.size)
        assertEquals(1, startedDecision.state.selectedStartCandidateIndex)
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
        val firstFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L, altitudeMslMeters = 1_200.0)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)
        val secondFix = RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L, altitudeMslMeters = 1_150.0)
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
    fun startCylinderExitStartsTaskWithinFixWindow() {
        val task = buildCylinderStartTask()
        val start = task.waypoints.first()
        val center = RacingBoundaryPoint(start.lat, start.lon)
        val inside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 8_000.0)
        val outside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 12_000.0)

        val firstFix = RacingNavigationFix(lat = inside.lat, lon = inside.lon, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix)

        val secondFix = RacingNavigationFix(lat = outside.lat, lon = outside.lon, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, secondFix)

        assertEquals(RacingNavigationEventType.START, secondDecision.event?.type)
        assertEquals(1, secondDecision.state.currentLegIndex)
        assertTrue(
            secondDecision.event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis
        )
    }

    @Test
    fun startSectorExitStartsTaskWithinFixWindow() {
        val task = buildSectorStartTask()
        val start = task.waypoints.first()
        val center = RacingBoundaryPoint(start.lat, start.lon)
        val inside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 8_000.0)
        val outside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 12_000.0)

        val firstFix = RacingNavigationFix(lat = inside.lat, lon = inside.lon, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix)

        val secondFix = RacingNavigationFix(lat = outside.lat, lon = outside.lon, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, secondFix)

        assertEquals(RacingNavigationEventType.START, secondDecision.event?.type)
        assertEquals(1, secondDecision.state.currentLegIndex)
        assertTrue(
            secondDecision.event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis
        )
    }

    @Test
    fun startCylinderToleranceFixWithinWindow_isAcceptedAsToleranceCandidate() {
        val task = buildCylinderStartTask()
        val start = task.waypoints.first()
        val center = RacingBoundaryPoint(start.lat, start.lon)
        val farOutside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 13_000.0)
        val nearBoundaryOutside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, 10_020.0)
        val rules = RacingStartCustomParams(toleranceMeters = 500.0)

        val firstFix = RacingNavigationFix(lat = farOutside.lat, lon = farOutside.lon, timestampMillis = 1_000L)
        val firstDecision = engine.step(task, RacingNavigationState(), firstFix, rules)

        val secondFix = RacingNavigationFix(lat = nearBoundaryOutside.lat, lon = nearBoundaryOutside.lon, timestampMillis = 2_000L)
        val secondDecision = engine.step(task, firstDecision.state, secondFix, rules)

        assertEquals(RacingNavigationEventType.START, secondDecision.event?.type)
        assertEquals(
            RacingStartCandidateType.TOLERANCE,
            secondDecision.event?.startCandidate?.candidateType
        )
    }

    @Test
    fun startSelection_prefersExistingBetterCandidateOverLatestPenalizedCandidate() {
        val task = buildLineStartTask()
        val existingBest = RacingStartCandidate(
            timestampMillis = 1_000L,
            candidateType = RacingStartCandidateType.STRICT,
            isValid = true,
            penaltyFlags = emptySet()
        )
        val previousState = RacingNavigationState(
            status = RacingNavigationStatus.PENDING_START,
            lastFix = RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_500L),
            startCandidates = listOf(existingBest)
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
    }

    private fun buildLineStartTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.1,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    private fun buildFinishLineTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start-finish-line",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp-finish-line",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.1,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish-line",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_LINE,
            customGateWidthMeters = 3_000.0
        )
        return SimpleRacingTask(
            id = "task-finish-line",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    private fun buildCylinderStartTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start-cylinder",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_CYLINDER,
            customGateWidthMeters = 10_000.0
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp-cylinder",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish-cylinder",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.3,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-cylinder-start",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    private fun buildSectorStartTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start-sector",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.FAI_START_SECTOR,
            customGateWidthMeters = 10_000.0
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp-sector",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish-sector",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.3,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-sector-start",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    private fun buildKeyholeTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = -0.1,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_CYLINDER
        )
        val keyhole = RacingWaypoint.createWithStandardizedDefaults(
            id = "keyhole",
            title = "Keyhole",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.KEYHOLE,
            customGateWidthMeters = 10_000.0,
            keyholeInnerRadiusMeters = 500.0,
            keyholeAngle = 90.0
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.1,
            lon = 0.0,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-keyhole",
            waypoints = listOf(start, keyhole, finish)
        )
    }
}
