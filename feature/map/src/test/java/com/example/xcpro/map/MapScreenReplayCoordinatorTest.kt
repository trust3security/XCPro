package com.example.xcpro.map

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.SessionState
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenReplayCoordinatorTest {

    @Test
    fun racingReplay_startFailure_should_restore_preReplay_task_nav_and_map_shell_state() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            primePreReplayState(fixture)

            fixture.mapStateStore.setTrackingLocation(false)
            fixture.mapStateStore.setShowReturnButton(true)
            fixture.mapStateStore.setHasInitiallyCentered(true)

            Mockito.doThrow(IllegalStateException("play failed"))
                .`when`(fixture.replayController)
                .play()

            fixture.coordinator.onRacingTaskReplay()
            advanceUntilIdle()

            assertEquals(2, fixture.taskManager.currentLeg)
            assertEquals(2, fixture.taskNavigationController.racingState.value.currentLegIndex)
            assertEquals(RacingNavigationStatus.IN_PROGRESS, fixture.taskNavigationController.racingState.value.status)
            assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, fixture.taskNavigationController.snapshot().armState)
            assertFalse(fixture.mapStateStore.isTrackingLocation.value)
            assertTrue(fixture.mapStateStore.showReturnButton.value)
            assertTrue(fixture.mapStateStore.hasInitiallyCentered.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun racingReplay_terminalCancel_should_restore_preReplay_task_nav_and_map_shell_state() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            primePreReplayState(fixture)

            fixture.mapStateStore.setTrackingLocation(false)
            fixture.mapStateStore.setShowReturnButton(true)
            fixture.mapStateStore.setHasInitiallyCentered(true)

            fixture.coordinator.onRacingTaskReplay()
            advanceUntilIdle()

            fixture.replayEvents.emit(ReplayEvent.Cancelled)
            advanceUntilIdle()

            assertEquals(2, fixture.taskManager.currentLeg)
            assertEquals(2, fixture.taskNavigationController.racingState.value.currentLegIndex)
            assertEquals(RacingNavigationStatus.IN_PROGRESS, fixture.taskNavigationController.racingState.value.status)
            assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, fixture.taskNavigationController.snapshot().armState)
            assertFalse(fixture.mapStateStore.isTrackingLocation.value)
            assertTrue(fixture.mapStateStore.showReturnButton.value)
            assertTrue(fixture.mapStateStore.hasInitiallyCentered.value)
        } finally {
            fixture.close()
        }
    }

    private fun createFixture(dispatcher: TestDispatcher): Fixture {
        val taskManager = TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
        )
        taskManager.initializeTask(sampleWaypoints())

        val taskNavigationController = TaskNavigationController(
            taskManager = taskManager,
            stateStore = RacingNavigationStateStore(),
            advanceState = RacingAdvanceState(),
            engine = RacingNavigationEngine(),
            featureFlags = TaskFeatureFlags()
        )

        val replayController = Mockito.mock(IgcReplayController::class.java)
        val replaySession = MutableStateFlow(SessionState(speedMultiplier = 2.0))
        val replayEvents = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 4)
        Mockito.`when`(replayController.session).thenReturn(replaySession)
        Mockito.`when`(replayController.events).thenReturn(replayEvents)
        Mockito.`when`(replayController.getReplayCadence()).thenReturn(ReplayCadenceProfile.DEFAULT)
        Mockito.`when`(replayController.getReplayMode()).thenReturn(ReplayMode.REALTIME_SIM)
        Mockito.`when`(replayController.isAutoStopAfterFinishEnabled()).thenReturn(false)

        val mapStateStore = MapStateStore(initialStyleName = "Topo")
        val mapStateActions = MapStateActionsDelegate(mapStateStore)
        val uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 8)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val coordinator = MapScreenReplayCoordinator(
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            flightDataFlow = MutableStateFlow<CompleteFlightData?>(null),
            igcReplayController = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder(),
            featureFlags = MapFeatureFlags(),
            mapStateStore = mapStateStore,
            mapStateActions = mapStateActions,
            uiEffects = uiEffects,
            replaySessionState = replaySession,
            scope = scope
        )
        coordinator.start()
        return Fixture(
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            replayController = replayController,
            replayEvents = replayEvents,
            mapStateStore = mapStateStore,
            coordinator = coordinator,
            scope = scope
        )
    }

    private fun TestScope.primePreReplayState(fixture: Fixture) {
        fixture.taskManager.setActiveLeg(2)
        advanceUntilIdle()
        assertEquals(2, fixture.taskManager.currentLeg)
        assertEquals(2, fixture.taskNavigationController.racingState.value.currentLegIndex)
        assertEquals(RacingNavigationStatus.IN_PROGRESS, fixture.taskNavigationController.racingState.value.status)
        assertEquals(RacingAdvanceState.ArmState.TURN_DISARMED, fixture.taskNavigationController.snapshot().armState)
    }

    private fun sampleWaypoints(): List<SearchWaypoint> = listOf(
        SearchWaypoint(id = "start", title = "Start", subtitle = "", lat = 0.0, lon = 0.0),
        SearchWaypoint(id = "tp1", title = "TP1", subtitle = "", lat = 0.0, lon = 0.1),
        SearchWaypoint(id = "tp2", title = "TP2", subtitle = "", lat = 0.1, lon = 0.1),
        SearchWaypoint(id = "finish", title = "Finish", subtitle = "", lat = 0.2, lon = 0.1)
    )

    private data class Fixture(
        val taskManager: TaskManagerCoordinator,
        val taskNavigationController: TaskNavigationController,
        val replayController: IgcReplayController,
        val replayEvents: MutableSharedFlow<ReplayEvent>,
        val mapStateStore: MapStateStore,
        val coordinator: MapScreenReplayCoordinator,
        val scope: CoroutineScope
    ) {
        fun close() {
            scope.cancel()
        }
    }
}
