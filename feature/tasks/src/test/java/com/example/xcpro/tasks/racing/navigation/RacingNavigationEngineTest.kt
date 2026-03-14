package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertTrue(event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis)
        assertNotNull(event.crossingEvidence)
        assertEquals(
            RacingBoundaryEvidenceSource.LINE_INTERSECTION,
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
            RacingBoundaryEvidenceSource.LINE_INTERSECTION,
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
        assertTrue(decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis)
        assertEquals(2, decision.state.currentLegIndex)
        assertNotNull(decision.event?.crossingEvidence)
        assertEquals(
            RacingBoundaryEvidenceSource.CYLINDER_INTERSECTION,
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
        assertTrue(decision.event!!.timestampMillis in outsideFix.timestampMillis..insideFix.timestampMillis)
        assertEquals(RacingFinishOutcome.VALID, decision.event?.finishOutcome)
        assertNotNull(decision.event?.crossingEvidence)
        assertEquals(
            RacingBoundaryEvidenceSource.CYLINDER_INTERSECTION,
            decision.event?.crossingEvidence?.evidenceSource
        )
        assertEquals(RacingNavigationStatus.FINISHED, decision.state.status)
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
        assertTrue(secondDecision.event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis)
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
        assertTrue(secondDecision.event!!.timestampMillis in firstFix.timestampMillis..secondFix.timestampMillis)
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
}
