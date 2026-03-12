package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
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
            assertEquals(1, coordinator.currentLeg)
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

                assertEquals(0, coordinator.currentLeg)
            } finally {
                job.cancel()
            }
        } finally {
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
        advanceState = RacingAdvanceState(),
        engine = RacingNavigationEngine(),
        featureFlags = featureFlags
    )

    private fun createCoordinator(): TaskManagerCoordinator =
        TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(null),
            aatTaskManager = AATTaskManager(null)
        )
}
