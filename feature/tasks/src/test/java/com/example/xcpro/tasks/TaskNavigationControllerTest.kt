package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingBoundaryCrossingEvidence
import com.example.xcpro.tasks.racing.navigation.RacingCreditedBoundaryHit
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryEvidenceSource
import com.example.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskNavigationControllerTest {

    @Test
    fun controllerAdvancesLegWhenAutoAdvanceArmed() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        controller.setAdvanceArmed(true)

        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 2)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L))
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L))
            advanceUntilIdle()

            // Start should advance to turnpoint index 1
            assertEquals(1, coordinator.currentSnapshot().activeLeg)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun controllerDoesNotAdvanceWhenAutoAdvanceDisabled() = runTest {
        val featureFlags = TaskFeatureFlags().apply { enableRacingAutoAdvance = false }
        try {
            val coordinator = createCoordinator()
            coordinator.initializeTask(sampleWaypoints())
            val controller = createController(coordinator, featureFlags)
            controller.setAdvanceArmed(true)

            val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 2)
            val job = controller.bind(fixes, this)
            try {
                advanceUntilIdle()
                fixes.emit(RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L))
                fixes.emit(RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L))
                advanceUntilIdle()

                assertEquals(0, coordinator.currentSnapshot().activeLeg)
            } finally {
                job.cancel()
            }
        } finally {
        }
    }

    @Test
    fun controllerDoesNotStartTaskWhenDisarmed() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)

        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 2)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = -0.001, timestampMillis = 1_000L))
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L))
            advanceUntilIdle()

            assertEquals(0, coordinator.currentSnapshot().activeLeg)
            assertEquals(RacingNavigationStatus.PENDING_START, controller.racingState.value.status)
            assertEquals(null, controller.racingState.value.creditedStart)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun controllerDoesNotAdvanceFromBoundaryFixWithoutInteriorStartEvidence() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        controller.setAdvanceArmed(true)

        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 2)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = 0.0, timestampMillis = 1_000L))
            fixes.emit(RacingNavigationFix(lat = 0.0, lon = 0.001, timestampMillis = 2_000L))
            advanceUntilIdle()

            assertEquals(0, coordinator.currentSnapshot().activeLeg)
            assertEquals(RacingNavigationStatus.PENDING_START, controller.racingState.value.status)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun manualLegChangeDisarmsAndSyncsState() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        controller.setAdvanceArmed(true)

        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 1)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            coordinator.advanceToNextLeg()
            advanceUntilIdle()

            val snapshot = controller.snapshot()
            assertEquals(false, snapshot.isArmed)
            assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, snapshot.armState)

            val state = controller.racingState.value
            assertEquals(1, state.currentLegIndex)
            assertEquals(RacingNavigationStatus.IN_PROGRESS, state.status)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun cancelledBindReleasesLegChangeListener() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        controller.setAdvanceArmed(true)

        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 1)
        val job = controller.bind(fixes, this)
        advanceUntilIdle()

        job.cancel()
        advanceUntilIdle()
        coordinator.advanceToNextLeg()
        advanceUntilIdle()

        val snapshot = controller.snapshot()
        assertEquals(true, snapshot.isArmed)
        assertEquals(0, controller.racingState.value.currentLegIndex)
    }

    @Test
    fun restoreReplaySnapshot_restores_selected_leg_nav_state_and_advance_phase() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        val fixes = MutableSharedFlow<RacingNavigationFix>(extraBufferCapacity = 1)
        val job = controller.bind(fixes, this)

        try {
            advanceUntilIdle()

            val restoredState = RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 1,
                lastFix = RacingNavigationFix(
                    lat = 0.0,
                    lon = 0.1,
                    timestampMillis = 12_345L
                ),
                lastTransitionTimeMillis = 12_345L,
                taskSignature = "replay-restore",
                creditedStart = RacingCreditedBoundaryHit(
                    legIndex = 0,
                    waypointRole = RacingWaypointRole.START,
                    timestampMillis = 10_000L,
                    crossingEvidence = RacingBoundaryCrossingEvidence(
                        crossingPoint = RacingBoundaryPoint(0.0, 0.0),
                        insideAnchor = RacingBoundaryPoint(0.0, -0.0005),
                        outsideAnchor = RacingBoundaryPoint(0.0, 0.0005),
                        evidenceSource = RacingBoundaryEvidenceSource.LINE_INTERSECTION
                    )
                ),
                preStartAltitudeSatisfied = true
            )
            val restoredAdvance = RacingAdvanceState.Snapshot(
                mode = RacingAdvanceState.Mode.MANUAL,
                armState = RacingAdvanceState.ArmState.TURN_ARMED,
                isArmed = true
            )

            controller.restoreReplaySnapshot(
                selectedLeg = 1,
                navigationState = restoredState,
                advanceSnapshot = restoredAdvance
            )
            advanceUntilIdle()

            assertEquals(1, coordinator.currentSnapshot().activeLeg)
            assertEquals(restoredState, controller.racingState.value)
            assertEquals(restoredAdvance, controller.snapshot())
        } finally {
            job.cancel()
        }
    }

    private fun sampleWaypoints(): List<SearchWaypoint> = listOf(
        SearchWaypoint(id = "start", title = "Start", subtitle = "", lat = 0.0, lon = 0.0),
        SearchWaypoint(id = "tp1", title = "TP1", subtitle = "", lat = 0.0, lon = 0.1),
        SearchWaypoint(id = "finish", title = "Finish", subtitle = "", lat = 0.0, lon = 0.2)
    )

    private fun createController(
        coordinator: TaskManagerCoordinator,
        featureFlags: TaskFeatureFlags = TaskFeatureFlags()
    ): TaskNavigationController = TaskNavigationController(
        taskManager = coordinator,
        stateStore = RacingNavigationStateStore(),
        engine = RacingNavigationEngine(),
        featureFlags = featureFlags
    )

    private fun createCoordinator(): TaskManagerCoordinator =
        TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager(),
            coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
}
