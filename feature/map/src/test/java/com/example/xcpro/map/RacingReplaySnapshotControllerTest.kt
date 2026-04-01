package com.example.xcpro.map

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.SessionState
import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class RacingReplaySnapshotControllerTest {

    @Test
    fun restore_should_restore_captured_selected_leg_and_nav_state() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        val replayController = Mockito.mock(IgcReplayController::class.java)
        val mapStateStore = MapStateStore(initialStyleName = "Topo")
        val mapStateActions = MapStateActionsDelegate(mapStateStore)
        Mockito.`when`(replayController.getReplayCadence()).thenReturn(ReplayCadenceProfile.DEFAULT)
        Mockito.`when`(replayController.getReplayMode()).thenReturn(ReplayMode.REALTIME_SIM)
        Mockito.`when`(replayController.isAutoStopAfterFinishEnabled()).thenReturn(false)
        val replaySession = MutableStateFlow(SessionState(speedMultiplier = 2.5))
        val snapshotController = RacingReplaySnapshotController(
            taskManager = coordinator,
            taskNavigationController = controller,
            igcReplayController = replayController,
            replaySessionState = replaySession,
            mapStateStore = mapStateStore,
            mapStateActions = mapStateActions
        )

        val fixes = MutableSharedFlow<com.example.xcpro.tasks.racing.navigation.RacingNavigationFix>(extraBufferCapacity = 1)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            coordinator.setActiveLeg(2)
            advanceUntilIdle()

            assertEquals(2, coordinator.currentLeg)
            assertEquals(2, controller.racingState.value.currentLegIndex)
            assertEquals(RacingNavigationStatus.IN_PROGRESS, controller.racingState.value.status)

            snapshotController.captureIfNeeded()

            controller.resetNavigationState()
            coordinator.setActiveLeg(0)
            advanceUntilIdle()

            snapshotController.restoreIfCaptured()

            assertEquals(2, coordinator.currentLeg)
            assertEquals(2, controller.racingState.value.currentLegIndex)
            assertEquals(RacingNavigationStatus.IN_PROGRESS, controller.racingState.value.status)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun restore_should_restore_full_advance_phase_not_just_is_armed() = runTest {
        val coordinator = createCoordinator()
        coordinator.initializeTask(sampleWaypoints())
        val controller = createController(coordinator)
        val replayController = Mockito.mock(IgcReplayController::class.java)
        val mapStateStore = MapStateStore(initialStyleName = "Topo")
        val mapStateActions = MapStateActionsDelegate(mapStateStore)
        Mockito.`when`(replayController.getReplayCadence()).thenReturn(ReplayCadenceProfile.DEFAULT)
        Mockito.`when`(replayController.getReplayMode()).thenReturn(ReplayMode.REALTIME_SIM)
        Mockito.`when`(replayController.isAutoStopAfterFinishEnabled()).thenReturn(false)
        val replaySession = MutableStateFlow(SessionState(speedMultiplier = 1.75))
        val snapshotController = RacingReplaySnapshotController(
            taskManager = coordinator,
            taskNavigationController = controller,
            igcReplayController = replayController,
            replaySessionState = replaySession,
            mapStateStore = mapStateStore,
            mapStateActions = mapStateActions
        )

        val fixes = MutableSharedFlow<com.example.xcpro.tasks.racing.navigation.RacingNavigationFix>(extraBufferCapacity = 1)
        val job = controller.bind(fixes, this)
        try {
            advanceUntilIdle()
            coordinator.setActiveLeg(2)
            advanceUntilIdle()
            assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, controller.snapshot().armState)

            snapshotController.captureIfNeeded()

            controller.resetNavigationState()
            advanceUntilIdle()
            assertEquals(RacingAdvanceState.ArmState.START_DISARMED, controller.snapshot().armState)

            snapshotController.restoreIfCaptured()

            assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, controller.snapshot().armState)
        } finally {
            job.cancel()
        }
    }

    private fun sampleWaypoints(): List<SearchWaypoint> = listOf(
        SearchWaypoint(id = "start", title = "Start", subtitle = "", lat = 0.0, lon = 0.0),
        SearchWaypoint(id = "tp1", title = "TP1", subtitle = "", lat = 0.0, lon = 0.1),
        SearchWaypoint(id = "tp2", title = "TP2", subtitle = "", lat = 0.1, lon = 0.1),
        SearchWaypoint(id = "finish", title = "Finish", subtitle = "", lat = 0.2, lon = 0.1)
    )

    private fun createCoordinator(): TaskManagerCoordinator =
        TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
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
}
