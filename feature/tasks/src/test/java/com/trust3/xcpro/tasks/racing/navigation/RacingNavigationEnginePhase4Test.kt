package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.tasks.racing.SimpleRacingTask
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RacingNavigationEnginePhase4Test {

    private val engine = RacingNavigationEngine()

    @Test
    fun faiQuadrantTurnpoint_entryUsesInterpolatedCrossingTime() {
        val task = buildFaiQuadrantTask()
        val turnpoint = task.waypoints[1]
        val previousWaypoint = task.waypoints[0]
        val nextWaypoint = task.waypoints[2]
        val sectorBearing = faiSectorBisector(turnpoint, previousWaypoint, nextWaypoint)
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val outside = RacingBoundaryGeometry.pointOnBearing(
            center,
            sectorBearing,
            turnpoint.faiQuadrantOuterRadiusMeters + 1_500.0
        )
        val inside = RacingBoundaryGeometry.pointOnBearing(
            center,
            sectorBearing,
            turnpoint.faiQuadrantOuterRadiusMeters - 1_500.0
        )
        val previousFix = RacingNavigationFix(
            lat = outside.lat,
            lon = outside.lon,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = previousFix
        )
        val currentFix = RacingNavigationFix(
            lat = inside.lat,
            lon = inside.lon,
            timestampMillis = 2_400L
        )

        val decision = engine.step(task, state, currentFix)

        assertEquals(RacingNavigationEventType.TURNPOINT, decision.event?.type)
        assertEquals(2, decision.state.currentLegIndex)
        val transitionTime = decision.event?.timestampMillis ?: 0L
        assertTrue(transitionTime in previousFix.timestampMillis..currentFix.timestampMillis)
        assertTrue(transitionTime < currentFix.timestampMillis)
    }

    @Test
    fun turnpointNearMissWithin500m_emitsNearMissEventWithoutAdvance() {
        val task = buildTwoTurnpointCylinderTask()
        val turnpoint = task.waypoints[1]
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val farOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + 900.0
        )
        val nearOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + 300.0
        )
        val previousFix = RacingNavigationFix(
            lat = farOutside.lat,
            lon = farOutside.lon,
            timestampMillis = 1_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = previousFix
        )
        val currentFix = RacingNavigationFix(
            lat = nearOutside.lat,
            lon = nearOutside.lon,
            timestampMillis = 2_000L
        )

        val decision = engine.step(task, state, currentFix)

        assertEquals(RacingNavigationEventType.TURNPOINT_NEAR_MISS, decision.event?.type)
        assertEquals(1, decision.state.currentLegIndex)
        assertEquals(1, decision.event?.fromLegIndex)
        assertEquals(1, decision.event?.toLegIndex)
        val nearMissDistance = decision.event?.turnpointNearMissDistanceMeters
        assertNotNull(nearMissDistance)
        assertTrue(nearMissDistance!! > 0.0)
        assertTrue(nearMissDistance <= TURNPOINT_NEAR_MISS_DISTANCE_METERS)
    }

    @Test
    fun turnpointNearMiss_thresholdBoundaryInside500m_emitsNearMiss() {
        val task = buildTwoTurnpointCylinderTask()
        val turnpoint = task.waypoints[1]
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val farOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + 900.0
        )
        val nearThresholdOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + (TURNPOINT_NEAR_MISS_DISTANCE_METERS - 20.0)
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = farOutside.lat,
                lon = farOutside.lon,
                timestampMillis = 1_000L
            )
        )
        val currentFix = RacingNavigationFix(
            lat = nearThresholdOutside.lat,
            lon = nearThresholdOutside.lon,
            timestampMillis = 2_000L
        )

        val decision = engine.step(task, state, currentFix)

        assertEquals(RacingNavigationEventType.TURNPOINT_NEAR_MISS, decision.event?.type)
        assertEquals(1, decision.state.currentLegIndex)
        assertTrue(
            decision.event?.turnpointNearMissDistanceMeters?.let {
                it in 0.0..TURNPOINT_NEAR_MISS_DISTANCE_METERS
            } == true
        )
    }

    @Test
    fun turnpointNearMiss_thresholdBoundaryOutside500m_doesNotEmitNearMiss() {
        val task = buildTwoTurnpointCylinderTask()
        val turnpoint = task.waypoints[1]
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val farOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + 900.0
        )
        val outsideThreshold = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + (TURNPOINT_NEAR_MISS_DISTANCE_METERS + 20.0)
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = farOutside.lat,
                lon = farOutside.lon,
                timestampMillis = 1_000L
            )
        )
        val currentFix = RacingNavigationFix(
            lat = outsideThreshold.lat,
            lon = outsideThreshold.lon,
            timestampMillis = 2_000L
        )

        val decision = engine.step(task, state, currentFix)

        assertNull(decision.event)
        assertEquals(1, decision.state.currentLegIndex)
    }

    @Test
    fun turnpointNearMiss_isEmittedOnlyOncePerLeg() {
        val task = buildTwoTurnpointCylinderTask()
        val turnpoint = task.waypoints[1]
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val firstOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters + 320.0
        )
        val secondOutside = RacingBoundaryGeometry.pointOnBearing(
            center,
            100.0,
            turnpoint.gateWidthMeters + 250.0
        )
        val startState = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = center.lat,
                lon = center.lon + 0.03,
                timestampMillis = 1_000L
            )
        )

        val firstDecision = engine.step(
            task,
            startState,
            RacingNavigationFix(
                lat = firstOutside.lat,
                lon = firstOutside.lon,
                timestampMillis = 2_000L
            )
        )
        val secondDecision = engine.step(
            task,
            firstDecision.state,
            RacingNavigationFix(
                lat = secondOutside.lat,
                lon = secondOutside.lon,
                timestampMillis = 3_000L
            )
        )

        assertEquals(RacingNavigationEventType.TURNPOINT_NEAR_MISS, firstDecision.event?.type)
        assertNull(secondDecision.event)
        assertTrue(firstDecision.state.reportedNearMissTurnpointLegIndices.contains(1))
        assertTrue(secondDecision.state.reportedNearMissTurnpointLegIndices.contains(1))
    }

    @Test
    fun turnpointSequence_insideLaterTurnpointDoesNotAdvanceActiveLeg() {
        val task = buildTwoTurnpointCylinderTask()
        val tp2 = task.waypoints[2]
        val nearTp2 = RacingNavigationFix(
            lat = tp2.lat,
            lon = tp2.lon,
            timestampMillis = 2_000L
        )
        val state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = tp2.lat,
                lon = tp2.lon + 0.03,
                timestampMillis = 1_000L
            )
        )

        val decision = engine.step(task, state, nearTp2)

        assertNull(decision.event)
        assertEquals(1, decision.state.currentLegIndex)
    }

    @Test
    fun turnpointBoundaryJitterOutsideCylinder_neverAutoAdvancesLeg() {
        val task = buildTwoTurnpointCylinderTask()
        val turnpoint = task.waypoints[1]
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val seed = 4242
        val random = Random(seed)
        var state = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = center.lat,
                lon = center.lon + 0.03,
                timestampMillis = 1_000L
            )
        )
        var nearMissCount = 0
        repeat(120) { index ->
            val bearing = random.nextDouble(0.0, 360.0)
            val gap = random.nextDouble(10.0, TURNPOINT_NEAR_MISS_DISTANCE_METERS - 10.0)
            val outside = RacingBoundaryGeometry.pointOnBearing(
                center,
                bearing,
                turnpoint.gateWidthMeters + gap
            )
            val decision = engine.step(
                task,
                state,
                RacingNavigationFix(
                    lat = outside.lat,
                    lon = outside.lon,
                    timestampMillis = 2_000L + index
                )
            )
            state = decision.state
            if (decision.event?.type == RacingNavigationEventType.TURNPOINT_NEAR_MISS) {
                nearMissCount += 1
            }
            assertEquals(1, state.currentLegIndex)
            assertTrue(decision.event?.type != RacingNavigationEventType.TURNPOINT)
        }

        assertEquals(1, nearMissCount)
        assertTrue(state.reportedNearMissTurnpointLegIndices.contains(1))
    }

    @Test
    fun faiQuadrantTurnpoint_interpolationFractionIsStableAcrossSparseAndDenseWindows() {
        val task = buildFaiQuadrantTask()
        val turnpoint = task.waypoints[1]
        val previousWaypoint = task.waypoints[0]
        val nextWaypoint = task.waypoints[2]
        val sectorBearing = faiSectorBisector(turnpoint, previousWaypoint, nextWaypoint)
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val outside = RacingBoundaryGeometry.pointOnBearing(
            center,
            sectorBearing,
            turnpoint.faiQuadrantOuterRadiusMeters + 1_500.0
        )
        val inside = RacingBoundaryGeometry.pointOnBearing(
            center,
            sectorBearing,
            turnpoint.faiQuadrantOuterRadiusMeters - 1_500.0
        )

        val sparseDecision = engine.step(
            task,
            RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 1,
                lastFix = RacingNavigationFix(outside.lat, outside.lon, 1_000L)
            ),
            RacingNavigationFix(inside.lat, inside.lon, 2_600L)
        )
        val denseDecision = engine.step(
            task,
            RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 1,
                lastFix = RacingNavigationFix(outside.lat, outside.lon, 5_000L)
            ),
            RacingNavigationFix(inside.lat, inside.lon, 6_600L)
        )

        assertEquals(RacingNavigationEventType.TURNPOINT, sparseDecision.event?.type)
        assertEquals(RacingNavigationEventType.TURNPOINT, denseDecision.event?.type)
        val sparseTime = sparseDecision.event!!.timestampMillis
        val denseTime = denseDecision.event!!.timestampMillis
        val sparseFraction = (sparseTime - 1_000L).toDouble() / (2_600L - 1_000L).toDouble()
        val denseFraction = (denseTime - 5_000L).toDouble() / (6_600L - 5_000L).toDouble()

        assertTrue(sparseTime in 1_000L..2_600L)
        assertTrue(denseTime in 5_000L..6_600L)
        assertTrue(denseTime > sparseTime)
        assertTrue(abs(sparseFraction - denseFraction) < 0.05)
    }

    private fun buildTwoTurnpointCylinderTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val tp1 = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 0.0,
            lon = 0.1,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val tp2 = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp2",
            title = "TP2",
            subtitle = "",
            lat = 0.0,
            lon = 0.2,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.0,
            lon = 0.3,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-two-tp",
            waypoints = listOf(start, tp1, tp2, finish)
        )
    }

    private fun buildFaiQuadrantTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = -0.05,
            lon = -0.03,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_CYLINDER
        )
        val quadrant = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp-quadrant",
            title = "TP Quadrant",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.FAI_QUADRANT,
            faiQuadrantOuterRadiusMeters = 5_000.0
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 0.03,
            lon = 0.07,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-fai-quadrant",
            waypoints = listOf(start, quadrant, finish)
        )
    }

    private fun faiSectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint
    ): Double {
        val inboundBearing = RacingGeometryUtils.calculateBearing(
            previousWaypoint.lat,
            previousWaypoint.lon,
            waypoint.lat,
            waypoint.lon
        )
        val outboundBearing = RacingGeometryUtils.calculateBearing(
            waypoint.lat,
            waypoint.lon,
            nextWaypoint.lat,
            nextWaypoint.lon
        )
        val trackBisector = RacingGeometryUtils.calculateAngleBisector(inboundBearing, outboundBearing)
        val turnDirection = RacingGeometryUtils.calculateTurnDirection(inboundBearing, outboundBearing)
        return if (turnDirection > 0) {
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            (trackBisector + 90.0) % 360.0
        }
    }
}
