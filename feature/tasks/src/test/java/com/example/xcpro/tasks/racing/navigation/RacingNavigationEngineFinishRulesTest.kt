package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingNavigationEngineFinishRulesTest {

    private val engine = RacingNavigationEngine()

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
            RacingBoundaryEvidenceSource.LINE_INTERSECTION,
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
            RacingBoundaryEvidenceSource.LINE_INTERSECTION,
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
        assertEquals(
            RacingFinishOutcome.CONTEST_BOUNDARY_STOP_PLUS_FIVE,
            finalDecision.event?.finishOutcome
        )
        assertEquals(301_000L, finalDecision.event?.timestampMillis)
    }
}
