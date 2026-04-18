package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskFlightSurfaceUseCaseTest {

    @Test
    fun racingPendingStart_usesSelectedLegFromCoordinator() = runTest {
        val taskSnapshotFlow = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = TaskType.RACING,
                task = sampleTask(),
                activeLeg = 2
            )
        )
        val racingStateFlow = MutableStateFlow(
            RacingNavigationState(
                status = RacingNavigationStatus.PENDING_START,
                currentLegIndex = 0
            )
        )

        val useCase = TaskFlightSurfaceUseCase(taskSnapshotFlow, racingStateFlow)

        assertEquals(2, useCase.uiState.firstValue().displayLegIndex)
    }

    @Test
    fun racingInProgress_usesNavigationLegFromRacingState() = runTest {
        val taskSnapshotFlow = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = TaskType.RACING,
                task = sampleTask(),
                activeLeg = 0
            )
        )
        val racingStateFlow = MutableStateFlow(
            RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 1
            )
        )

        val useCase = TaskFlightSurfaceUseCase(taskSnapshotFlow, racingStateFlow)

        assertEquals(1, useCase.uiState.firstValue().displayLegIndex)
    }

    @Test
    fun nonRacing_ignoresRacingStateAndUsesSelectedLeg() = runTest {
        val taskSnapshotFlow = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = TaskType.AAT,
                task = sampleTask(),
                activeLeg = 1
            )
        )
        val racingStateFlow = MutableStateFlow(
            RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 2
            )
        )

        val useCase = TaskFlightSurfaceUseCase(taskSnapshotFlow, racingStateFlow)

        assertEquals(1, useCase.uiState.firstValue().displayLegIndex)
    }

    private suspend fun kotlinx.coroutines.flow.Flow<TaskFlightSurfaceUiState>.firstValue(): TaskFlightSurfaceUiState =
        first()

    private fun sampleTask(): Task = Task(
        id = "task",
        waypoints = listOf(
            waypoint("start", "Start", WaypointRole.START, 0.0, 0.0),
            waypoint("tp1", "TP1", WaypointRole.TURNPOINT, 0.0, 0.1),
            waypoint("finish", "Finish", WaypointRole.FINISH, 0.1, 0.1)
        )
    )

    private fun waypoint(
        id: String,
        title: String,
        role: WaypointRole,
        lat: Double,
        lon: Double
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = title,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role
    )
}
