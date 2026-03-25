package com.example.xcpro.glide

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.navigation.NavigationRouteRepository
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import javax.inject.Inject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

// Compatibility shim only: this delegates to the shared glide policy projector
// and the canonical task-owned route seam. It is not an authoritative owner.
@ViewModelScoped
class GlideTargetRepository internal constructor(
    taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
    routeFlow: Flow<NavigationRouteSnapshot>,
    glideTargetProjector: GlideTargetProjector
) {
    @Inject
    constructor(
        taskManager: TaskManagerCoordinator,
        navigationRouteRepository: NavigationRouteRepository,
        glideTargetProjector: GlideTargetProjector
    ) : this(
        taskSnapshotFlow = taskManager.taskSnapshotFlow,
        routeFlow = navigationRouteRepository.route,
        glideTargetProjector = glideTargetProjector
    )

    internal val finishTarget: Flow<GlideTargetSnapshot> = combine(
        taskSnapshotFlow,
        routeFlow,
        glideTargetProjector::project
    ).distinctUntilChanged()
}
