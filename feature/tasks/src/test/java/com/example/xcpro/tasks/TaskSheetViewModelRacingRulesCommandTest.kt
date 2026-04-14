package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingPevCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import com.example.xcpro.tasks.racing.UpdateRacingFinishRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingStartRulesCommand
import com.example.xcpro.tasks.racing.UpdateRacingValidationRulesCommand
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class TaskSheetViewModelRacingRulesCommandTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onUpdateRacingStartRules_forwardsTypedCommandToCoordinator() {
        val taskManager = mockTaskManager()
        val viewModel = createViewModel(taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val command = UpdateRacingStartRulesCommand(
            rules = RacingStartCustomParams(
                gateOpenTimeMillis = 1_000L,
                gateCloseTimeMillis = 2_000L,
                toleranceMeters = 450.0,
                preStartAltitudeMeters = 1_250.0,
                altitudeReference = RacingAltitudeReference.QNH,
                directionOverrideDegrees = 178.5,
                maxStartAltitudeMeters = 1_900.0,
                maxStartGroundspeedMs = 45.0,
                pev = RacingPevCustomParams(
                    enabled = true,
                    waitTimeMinutes = 6,
                    startWindowMinutes = 8,
                    maxPressesPerLaunch = 2,
                    dedupeSeconds = 20L,
                    minIntervalMinutes = 5,
                    pressTimestampsMillis = listOf(1_000L, 1_500L)
                )
            )
        )

        viewModel.onUpdateRacingStartRules(command)

        Mockito.verify(taskManager).updateRacingStartRules(command)
    }

    @Test
    fun onUpdateRacingFinishRules_forwardsTypedCommandToCoordinator() {
        val taskManager = mockTaskManager()
        val viewModel = createViewModel(taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val command = UpdateRacingFinishRulesCommand(
            rules = RacingFinishCustomParams(
                closeTimeMillis = 3_000L,
                minAltitudeMeters = 600.0,
                altitudeReference = RacingAltitudeReference.QNH,
                directionOverrideDegrees = 92.0,
                allowStraightInBelowMinAltitude = true,
                requireLandWithoutDelay = true,
                landWithoutDelayWindowSeconds = 300L,
                landingSpeedThresholdMs = 6.5,
                landingHoldSeconds = 18L,
                contestBoundaryRadiusMeters = 9_500.0,
                stopPlusFiveEnabled = true,
                stopPlusFiveMinutes = 7L
            )
        )

        viewModel.onUpdateRacingFinishRules(command)

        Mockito.verify(taskManager).updateRacingFinishRules(command)
    }

    @Test
    fun onUpdateRacingValidationRules_forwardsTypedCommandToCoordinator() {
        val taskManager = mockTaskManager()
        val viewModel = createViewModel(taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val command = UpdateRacingValidationRulesCommand(
            profile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )

        viewModel.onUpdateRacingValidationRules(command)

        Mockito.verify(taskManager).updateRacingValidationRules(command)
    }

    @Test
    fun onAdvanceMode_forRacing_forwardsToCoordinatorRacingAdvanceOwner() {
        val taskManager = mockTaskManager()
        val viewModel = createViewModel(taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAdvanceMode(TaskAdvanceUiSnapshot.Mode.MANUAL)

        Mockito.verify(taskManager).setRacingAdvanceMode(RacingAdvanceState.Mode.MANUAL)
    }

    @Test
    fun onAdvanceArmToggle_forRacing_forwardsToCoordinatorRacingAdvanceOwner() {
        val taskManager = mockTaskManager()
        val viewModel = createViewModel(taskManager)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAdvanceArmToggle()

        Mockito.verify(taskManager).toggleRacingAdvanceArmed()
    }

    private fun createViewModel(taskManager: TaskManagerCoordinator): TaskSheetViewModel {
        val useCase = TaskSheetUseCase(
            taskManager = taskManager,
            repository = TaskRepository(validator = TaskValidator()),
            proximityEvaluator = TaskProximityEvaluator(),
            persistedTaskImporter = TaskSheetPersistedTaskImporter()
        )
        return TaskSheetViewModel(useCase = useCase)
    }

    private fun mockTaskManager(): TaskManagerCoordinator {
        val taskManager = Mockito.mock(TaskManagerCoordinator::class.java)
        Mockito.`when`(taskManager.taskSnapshotFlow).thenReturn(
            MutableStateFlow(
                TaskRuntimeSnapshot(
                    taskType = TaskType.RACING,
                    task = Task(id = "snapshot-task"),
                    activeLeg = 0
                )
            )
        )
        Mockito.`when`(taskManager.racingAdvanceSnapshotFlow).thenReturn(
            MutableStateFlow(RacingAdvanceState().snapshot())
        )
        Mockito.`when`(taskManager.getRacingValidationProfile()).thenReturn(RacingTaskStructureRules.Profile.FAI_STRICT)
        return taskManager
    }
}
