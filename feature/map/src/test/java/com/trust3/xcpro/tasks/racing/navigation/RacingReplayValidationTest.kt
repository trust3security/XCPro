package com.trust3.xcpro.tasks.racing.navigation

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.replay.IgcParser
import com.trust3.xcpro.replay.IgcPoint
import com.trust3.xcpro.tasks.core.RacingFinishCustomParams
import com.trust3.xcpro.tasks.core.RacingPevCustomParams
import com.trust3.xcpro.tasks.core.RacingStartCustomParams
import com.trust3.xcpro.tasks.racing.SimpleRacingTask
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RacingReplayValidationTest {

    private val engine = RacingNavigationEngine()

    data class ReplayStepTrace(
        val index: Int,
        val status: RacingNavigationStatus,
        val currentLegIndex: Int,
        val eventType: RacingNavigationEventType?
    )

    @Test
    fun replayPathTriggersStartTurnFinishInOrder() {
        val task = buildSimpleTask()
        val resource = javaClass.classLoader?.getResourceAsStream(REPLAY_RESOURCE)
            ?: error("Missing replay fixture: $REPLAY_RESOURCE")
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(resource)
        assertTrue("Expected IGC points", log.points.isNotEmpty())

        val firstRunEvents = replayEvents(task, log.points)
        val secondRunEvents = replayEvents(task, log.points)
        assertEquals(firstRunEvents, secondRunEvents)

        val startMillis = log.points.first().timestampMillis
        val scoredEvents = firstRunEvents.filter { it.type != RacingNavigationEventType.TURNPOINT_NEAR_MISS }
        assertEquals(3, scoredEvents.size)
        assertEquals(RacingNavigationEventType.START, scoredEvents[0].type)
        assertEquals(RacingNavigationEventType.TURNPOINT, scoredEvents[1].type)
        assertEquals(RacingNavigationEventType.FINISH, scoredEvents[2].type)
        assertTrue(scoredEvents[0].timestampMillis >= startMillis)
        assertTrue(scoredEvents[1].timestampMillis >= scoredEvents[0].timestampMillis)
        assertTrue(scoredEvents[2].timestampMillis >= scoredEvents[1].timestampMillis)
    }

    @Test
    fun replayStateTrace_isDeterministicAcrossRepeatedRuns() {
        val task = buildSimpleTask()
        val resource = javaClass.classLoader?.getResourceAsStream(REPLAY_RESOURCE)
            ?: error("Missing replay fixture: $REPLAY_RESOURCE")
        val parser = IgcParser(FakeClock(wallMs = 0L))
        val log = parser.parse(resource)
        assertTrue("Expected IGC points", log.points.isNotEmpty())

        val firstTrace = replayStateTrace(task, log.points)
        val secondTrace = replayStateTrace(task, log.points)

        assertEquals(firstTrace, secondTrace)
        assertTrue(firstTrace.any { it.eventType != null })
    }

    @Test
    fun replayStartOutcomes_areDeterministicForRejectedAndNonAdvancingToleranceCases() {
        val task = buildSimpleTask()
        val rules = RacingStartCustomParams(
            gateOpenTimeMillis = 2_000L,
            toleranceMeters = 500.0,
            pev = RacingPevCustomParams(
                enabled = true,
                waitTimeMinutes = 5,
                startWindowMinutes = 5
            )
        )
        val fixes = listOf(
            RacingNavigationFix(lat = 37.0, lon = -122.001, timestampMillis = 1_000L),
            RacingNavigationFix(lat = 37.0, lon = -121.999, timestampMillis = 1_200L),
            RacingNavigationFix(lat = 37.0, lon = -122.0, timestampMillis = 2_500L)
        )

        val firstRun = replayStartOutcomeWithRules(task, fixes, rules)
        val secondRun = replayStartOutcomeWithRules(task, fixes, rules)

        assertEquals(firstRun, secondRun)
        assertEquals(1, firstRun.first.size)
        assertEquals(RacingNavigationEventType.START_REJECTED, firstRun.first[0].type)
        assertEquals(RacingStartRejectionReason.GATE_NOT_OPEN, firstRun.first[0].startCandidate?.rejectionReason)
        assertEquals(RacingStartCandidateType.TOLERANCE, firstRun.second.startCandidates.last().candidateType)
        assertEquals(RacingNavigationStatus.PENDING_START, firstRun.second.status)
        assertTrue(firstRun.second.creditedStart == null)
        assertTrue(
            firstRun.second.startCandidates.last().penaltyFlags.contains(RacingStartPenaltyFlag.PEV_MISSING)
        )
    }

    @Test
    fun replayNearMissPath_isDeterministicAcrossRepeatedRuns() {
        val task = buildSimpleTask()
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
            turnpoint.gateWidthMeters + 250.0
        )
        val inside = RacingBoundaryGeometry.pointOnBearing(
            center,
            90.0,
            turnpoint.gateWidthMeters - 100.0
        )
        val initialState = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = 1,
            lastFix = RacingNavigationFix(
                lat = farOutside.lat,
                lon = farOutside.lon,
                timestampMillis = 1_000L
            )
        )
        val fixes = listOf(
            RacingNavigationFix(
                lat = nearOutside.lat,
                lon = nearOutside.lon,
                timestampMillis = 2_000L
            ),
            RacingNavigationFix(
                lat = inside.lat,
                lon = inside.lon,
                timestampMillis = 3_000L
            )
        )

        val firstRun = replayEvents(task, fixes, initialState)
        val secondRun = replayEvents(task, fixes, initialState)

        assertEquals(firstRun, secondRun)
        assertEquals(2, firstRun.size)
        assertEquals(RacingNavigationEventType.TURNPOINT_NEAR_MISS, firstRun[0].type)
        assertEquals(RacingNavigationEventType.TURNPOINT, firstRun[1].type)
        assertTrue(firstRun[1].timestampMillis >= firstRun[0].timestampMillis)
    }

    @Test
    fun replayFinishLandingOutcome_isDeterministicAcrossRepeatedRuns() {
        val task = buildSimpleTask()
        val finish = task.waypoints.last()
        val finishRules = RacingFinishCustomParams(
            requireLandWithoutDelay = true,
            landWithoutDelayWindowSeconds = 120L,
            landingSpeedThresholdMs = 4.0,
            landingHoldSeconds = 20L
        )
        val initialState = RacingNavigationState(
            status = RacingNavigationStatus.IN_PROGRESS,
            currentLegIndex = task.waypoints.lastIndex,
            lastFix = RacingNavigationFix(
                lat = finish.lat,
                lon = finish.lon + 0.05,
                timestampMillis = 1_000L,
                groundSpeedMs = 30.0
            )
        )
        val fixes = listOf(
            RacingNavigationFix(
                lat = finish.lat,
                lon = finish.lon + 0.005,
                timestampMillis = 2_000L,
                groundSpeedMs = 25.0
            ),
            RacingNavigationFix(
                lat = finish.lat,
                lon = finish.lon + 0.005,
                timestampMillis = 10_000L,
                groundSpeedMs = 2.0
            ),
            RacingNavigationFix(
                lat = finish.lat,
                lon = finish.lon + 0.005,
                timestampMillis = 35_000L,
                groundSpeedMs = 2.0
            )
        )

        val firstRun = replayEvents(task, fixes, initialState, finishRules)
        val secondRun = replayEvents(task, fixes, initialState, finishRules)

        assertEquals(firstRun, secondRun)
        assertEquals(1, firstRun.size)
        assertEquals(RacingNavigationEventType.FINISH, firstRun[0].type)
        assertEquals(RacingFinishOutcome.LANDING_PENDING, firstRun[0].finishOutcome)
    }

    private fun replayEvents(
        task: SimpleRacingTask,
        points: List<IgcPoint>
    ): List<RacingNavigationEvent> {
        val events = mutableListOf<RacingNavigationEvent>()
        var state = RacingNavigationState()
        points.forEach { point ->
            val fix = RacingNavigationFix(
                lat = point.latitude,
                lon = point.longitude,
                timestampMillis = point.timestampMillis
            )
            val decision = engine.step(task, state, fix)
            state = decision.state
            decision.event?.let(events::add)
        }
        return events
    }

    private fun replayStateTrace(
        task: SimpleRacingTask,
        points: List<IgcPoint>
    ): List<ReplayStepTrace> {
        val trace = mutableListOf<ReplayStepTrace>()
        var state = RacingNavigationState()
        points.forEachIndexed { index, point ->
            val fix = RacingNavigationFix(
                lat = point.latitude,
                lon = point.longitude,
                timestampMillis = point.timestampMillis
            )
            val decision = engine.step(task, state, fix)
            state = decision.state
            trace += ReplayStepTrace(
                index = index,
                status = state.status,
                currentLegIndex = state.currentLegIndex,
                eventType = decision.event?.type
            )
        }
        return trace
    }

    private fun replayStartOutcomeWithRules(
        task: SimpleRacingTask,
        fixes: List<RacingNavigationFix>,
        rules: RacingStartCustomParams
    ): Pair<List<RacingNavigationEvent>, RacingNavigationState> {
        val events = mutableListOf<RacingNavigationEvent>()
        var state = RacingNavigationState()
        fixes.forEach { fix ->
            val decision = engine.step(task, state, fix, rules)
            state = decision.state
            decision.event?.let(events::add)
        }
        return events to state
    }

    private fun replayEvents(
        task: SimpleRacingTask,
        fixes: List<RacingNavigationFix>,
        initialState: RacingNavigationState
    ): List<RacingNavigationEvent> {
        return replayEvents(task, fixes, initialState, RacingFinishCustomParams())
    }

    private fun replayEvents(
        task: SimpleRacingTask,
        fixes: List<RacingNavigationFix>,
        initialState: RacingNavigationState,
        finishRules: RacingFinishCustomParams
    ): List<RacingNavigationEvent> {
        val events = mutableListOf<RacingNavigationEvent>()
        var state = initialState
        fixes.forEach { fix ->
            val decision = engine.step(task, state, fix, finishRules = finishRules)
            state = decision.state
            decision.event?.let(events::add)
        }
        return events
    }

    private fun buildSimpleTask(): SimpleRacingTask {
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start",
            title = "Start",
            subtitle = "",
            lat = 37.0,
            lon = -122.0,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_LINE
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp1",
            title = "TP1",
            subtitle = "",
            lat = 37.0,
            lon = -121.9,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish",
            title = "Finish",
            subtitle = "",
            lat = 37.0,
            lon = -121.8,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    companion object {
        private const val REPLAY_RESOURCE = "replay/racing-task-basic.igc"
    }
}
