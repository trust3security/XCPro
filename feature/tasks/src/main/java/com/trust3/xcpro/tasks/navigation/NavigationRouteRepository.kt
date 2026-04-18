package com.trust3.xcpro.tasks.navigation

import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationState
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@ViewModelScoped
class NavigationRouteRepository internal constructor(
    taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
    racingStateFlow: Flow<RacingNavigationState>
) {
    @Inject
    constructor(
        taskManager: TaskManagerCoordinator,
        taskNavigationController: TaskNavigationController
    ) : this(taskManager.taskSnapshotFlow, taskNavigationController.racingState)

    val route: Flow<NavigationRouteSnapshot> = combine(
        taskSnapshotFlow,
        racingStateFlow,
        ::projectNavigationRoute
    ).distinctUntilChanged()
}
