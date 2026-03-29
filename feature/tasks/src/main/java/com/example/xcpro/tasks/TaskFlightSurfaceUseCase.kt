package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

data class TaskFlightSurfaceUiState(
    val task: Task = Task(id = "new-task"),
    val taskType: TaskType = TaskType.RACING,
    val displayLegIndex: Int = 0
)

class TaskFlightSurfaceUseCase private constructor(
    val uiState: Flow<TaskFlightSurfaceUiState>
) {
    @Inject constructor(
        taskManager: TaskManagerCoordinator,
        taskNavigationController: TaskNavigationController
    ) : this(
        uiState = buildUiState(
            taskSnapshotFlow = taskManager.taskSnapshotFlow,
            racingStateFlow = taskNavigationController.racingState
        )
    )

    internal constructor(
        taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
        racingStateFlow: Flow<RacingNavigationState>
    ) : this(
        uiState = buildUiState(
            taskSnapshotFlow = taskSnapshotFlow,
            racingStateFlow = racingStateFlow
        )
    )

    companion object {
        internal fun buildUiState(
            taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
            racingStateFlow: Flow<RacingNavigationState>
        ): Flow<TaskFlightSurfaceUiState> = combine(
            taskSnapshotFlow,
            racingStateFlow
        ) { snapshot, racingState ->
            TaskFlightSurfaceUiState(
                task = snapshot.task,
                taskType = snapshot.taskType,
                displayLegIndex = displayLegIndex(snapshot, racingState)
            )
        }.distinctUntilChanged()

        private fun displayLegIndex(
            snapshot: TaskRuntimeSnapshot,
            racingState: RacingNavigationState
        ): Int {
            val task = snapshot.task
            if (task.waypoints.isEmpty()) return 0

            val preferredLeg = when (snapshot.taskType) {
                TaskType.RACING -> {
                    if (racingState.status == RacingNavigationStatus.PENDING_START) {
                        snapshot.activeLeg
                    } else {
                        racingState.currentLegIndex
                    }
                }

                else -> snapshot.activeLeg
            }

            return preferredLeg.coerceIn(0, task.waypoints.lastIndex)
        }
    }
}
