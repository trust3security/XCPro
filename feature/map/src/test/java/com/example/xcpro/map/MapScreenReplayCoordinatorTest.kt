package com.example.xcpro.map

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.SyntheticThermalReplayMode
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.map.replay.SyntheticThermalReplayLogBuilder
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.IgcLog
import com.example.xcpro.replay.ReplayCadenceProfile
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.ReplayInterpolation
import com.example.xcpro.replay.ReplayMode
import com.example.xcpro.replay.ReplayNoiseProfile
import com.example.xcpro.replay.Selection
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
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

            assertEquals(2, fixture.taskManager.currentSnapshot().activeLeg)
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

            assertEquals(2, fixture.taskManager.currentSnapshot().activeLeg)
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
    fun racingReplay_terminalCleanupFence_blocks_liveFixes_before_restore() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.coordinator.onRacingTaskReplay()
            advanceUntilIdle()

            fixture.replaySession.value = SessionState(
                selection = Selection(DocumentRef(uri = "asset:///replay/racing.igc")),
                status = SessionStatus.PLAYING,
                speedMultiplier = 2.0
            )
            advanceUntilIdle()

            fixture.replaySession.value = SessionState(speedMultiplier = 2.0)
            fixture.flightDataFlow.value = buildCompleteFlightData(
                gps = defaultGps(longitude = -0.001, timestampMillis = 1_000L)
            )
            fixture.flightDataFlow.value = buildCompleteFlightData(
                gps = defaultGps(longitude = 0.001, timestampMillis = 2_000L)
            )
            advanceUntilIdle()

            assertEquals(0, fixture.taskManager.currentSnapshot().activeLeg)
            assertEquals(0, fixture.taskNavigationController.racingState.value.currentLegIndex)
            assertEquals(
                RacingNavigationStatus.PENDING_START,
                fixture.taskNavigationController.racingState.value.status
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun syntheticThermalReplay_setsLiveCadenceAndLoadsCleanSyntheticLog() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.coordinator.onSyntheticThermalReplay()
            advanceUntilIdle()

            Mockito.verify(fixture.replayController).setReplayMode(ReplayMode.REFERENCE, true)
            Mockito.verify(fixture.replayController).setReplayCadence(ReplayCadenceProfile.LIVE_100MS)
            Mockito.verify(fixture.replayController).setAutoStopAfterFinish(false)
            val loadLogInvocation = Mockito.mockingDetails(fixture.replayController).invocations.single {
                it.method.name == "loadLog"
            }
            val log = loadLogInvocation.arguments[0] as IgcLog
            val displayName = loadLogInvocation.arguments[1] as String
            assertEquals("Synthetic thermal (clean)", displayName)
            assertEquals(601, log.points.size)
            assertTrue(log.points.first().trueAirspeedKmh!! > 0.0)
            assertTrue(log.points.last().latitude > log.points.first().latitude)
            assertEquals(SyntheticThermalReplayMode.CLEAN, fixture.syntheticReplayMode.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun syntheticThermalReplayWindNoisy_loadsDistinctSyntheticVariant() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.coordinator.onSyntheticThermalReplayWindNoisy()
            advanceUntilIdle()

            val loadLogInvocation = Mockito.mockingDetails(fixture.replayController).invocations.single {
                it.method.name == "loadLog"
            }
            val log = loadLogInvocation.arguments[0] as IgcLog
            val displayName = loadLogInvocation.arguments[1] as String
            assertEquals("Synthetic thermal (wind-noisy)", displayName)
            assertEquals(601, log.points.size)
            assertTrue(log.points.last().longitude != log.points.first().longitude)
            assertTrue(log.points.last().latitude > log.points.first().latitude)
            assertEquals(SyntheticThermalReplayMode.WIND_NOISY, fixture.syntheticReplayMode.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun syntheticThermalReplay_completionSeeksFinalFrameWithoutRestoringSnapshot() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.coordinator.onSyntheticThermalReplay()
            advanceUntilIdle()

            fixture.mapStateStore.setTrackingLocation(false)
            fixture.replayEvents.emit(ReplayEvent.Completed(samples = 601))
            advanceUntilIdle()

            Mockito.verify(fixture.replayController).seekTo(1f)
            assertEquals(SyntheticThermalReplayMode.CLEAN, fixture.syntheticReplayMode.value)
            assertFalse(fixture.mapStateStore.isTrackingLocation.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun syntheticThermalReplay_cancelClearsSyntheticModeAndRestoresSnapshot() = runTest {
        val fixture = createFixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.mapStateStore.setTrackingLocation(false)
            fixture.mapStateStore.setShowReturnButton(true)

            fixture.coordinator.onSyntheticThermalReplay()
            advanceUntilIdle()

            fixture.mapStateStore.setTrackingLocation(true)
            fixture.mapStateStore.setShowReturnButton(false)
            fixture.replayEvents.emit(ReplayEvent.Cancelled)
            advanceUntilIdle()

            assertEquals(SyntheticThermalReplayMode.NONE, fixture.syntheticReplayMode.value)
            assertFalse(fixture.mapStateStore.isTrackingLocation.value)
            assertTrue(fixture.mapStateStore.showReturnButton.value)
        } finally {
            fixture.close()
        }
    }

    private fun createFixture(dispatcher: TestDispatcher): Fixture {
        val taskScope = CoroutineScope(SupervisorJob() + dispatcher)
        val taskManager = TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager(),
            coordinatorScope = taskScope
        )
        taskManager.initializeTask(sampleWaypoints())

        val taskNavigationController = TaskNavigationController(
            taskManager = taskManager,
            stateStore = RacingNavigationStateStore(),
            engine = RacingNavigationEngine(),
            featureFlags = TaskFeatureFlags()
        )

        val replayController = Mockito.mock(IgcReplayController::class.java)
        val replaySession = MutableStateFlow(SessionState(speedMultiplier = 2.0))
        val replayEvents = MutableSharedFlow<ReplayEvent>(extraBufferCapacity = 4)
        val flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
        val syntheticReplayMode = MutableStateFlow(SyntheticThermalReplayMode.NONE)
        Mockito.`when`(replayController.session).thenReturn(replaySession)
        Mockito.`when`(replayController.events).thenReturn(replayEvents)
        Mockito.`when`(replayController.getReplayCadence()).thenReturn(ReplayCadenceProfile.DEFAULT)
        Mockito.`when`(replayController.getReplayMode()).thenReturn(ReplayMode.REALTIME_SIM)
        Mockito.`when`(replayController.getReplayBaroStepMs()).thenReturn(20L)
        Mockito.`when`(replayController.getReplayNoiseProfile()).thenReturn(
            ReplayNoiseProfile(
                pressureNoiseSigmaHpa = 0.04,
                gpsAltitudeNoiseSigmaM = 1.5,
                jitterMs = 8L
            )
        )
        Mockito.`when`(replayController.getReplayGpsAccuracyMeters()).thenReturn(5f)
        Mockito.`when`(replayController.getReplayInterpolation()).thenReturn(ReplayInterpolation.LINEAR)
        Mockito.`when`(replayController.isAutoStopAfterFinishEnabled()).thenReturn(false)

        val mapStateStore = MapStateStore(initialStyleName = "Topo")
        val mapStateActions = MapStateActionsDelegate(mapStateStore)
        val uiEffects = MutableSharedFlow<MapUiEffect>(extraBufferCapacity = 8)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val coordinator = MapScreenReplayCoordinator(
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            flightDataFlow = flightDataFlow,
            igcReplayController = replayController,
            racingReplayLogBuilder = RacingReplayLogBuilder(),
            syntheticThermalReplayLogBuilder = SyntheticThermalReplayLogBuilder(),
            featureFlags = MapFeatureFlags(),
            mapStateStore = mapStateStore,
            mapStateActions = mapStateActions,
            syntheticReplayMode = syntheticReplayMode,
            uiEffects = uiEffects,
            replaySessionState = replaySession,
            scope = scope
        )
        coordinator.start()
        return Fixture(
            taskManager = taskManager,
            taskNavigationController = taskNavigationController,
            replayController = replayController,
            replaySession = replaySession,
            replayEvents = replayEvents,
            flightDataFlow = flightDataFlow,
            mapStateStore = mapStateStore,
            syntheticReplayMode = syntheticReplayMode,
            coordinator = coordinator,
            scope = scope
        )
    }

    private fun TestScope.primePreReplayState(fixture: Fixture) {
        fixture.taskManager.setActiveLeg(2)
        advanceUntilIdle()
        assertEquals(2, fixture.taskManager.currentSnapshot().activeLeg)
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
        val replaySession: MutableStateFlow<SessionState>,
        val replayEvents: MutableSharedFlow<ReplayEvent>,
        val flightDataFlow: MutableStateFlow<CompleteFlightData?>,
        val mapStateStore: MapStateStore,
        val syntheticReplayMode: MutableStateFlow<SyntheticThermalReplayMode>,
        val coordinator: MapScreenReplayCoordinator,
        val scope: CoroutineScope
    ) {
        fun close() {
            scope.cancel()
        }
    }
}
