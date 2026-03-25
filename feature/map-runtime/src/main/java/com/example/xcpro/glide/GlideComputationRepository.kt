package com.example.xcpro.glide

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.navigation.NavigationRouteRepository
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.sensors.CompleteFlightData
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@ViewModelScoped
class GlideComputationRepository internal constructor(
    flightDataFlow: Flow<CompleteFlightData?>,
    windStateFlow: Flow<WindState>,
    taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
    routeFlow: Flow<NavigationRouteSnapshot>,
    private val glideTargetProjector: GlideTargetProjector,
    private val finalGlideUseCase: FinalGlideUseCase
) {
    @Inject
    constructor(
        flightDataRepository: FlightDataRepository,
        windSensorFusionRepository: WindSensorFusionRepository,
        taskManager: TaskManagerCoordinator,
        navigationRouteRepository: NavigationRouteRepository,
        glideTargetProjector: GlideTargetProjector,
        finalGlideUseCase: FinalGlideUseCase
    ) : this(
        flightDataFlow = flightDataRepository.flightData,
        windStateFlow = windSensorFusionRepository.windState,
        taskSnapshotFlow = taskManager.taskSnapshotFlow,
        routeFlow = navigationRouteRepository.route,
        glideTargetProjector = glideTargetProjector,
        finalGlideUseCase = finalGlideUseCase
    )

    val glide: Flow<GlideSolution> = combine(
        flightDataFlow,
        windStateFlow,
        taskSnapshotFlow,
        routeFlow,
        ::resolveGlideSolution
    ).distinctUntilChanged()

    private fun resolveGlideSolution(
        completeData: CompleteFlightData?,
        windState: WindState,
        taskSnapshot: TaskRuntimeSnapshot,
        route: NavigationRouteSnapshot
    ): GlideSolution {
        val data = completeData ?: return GlideSolution.invalid(GlideInvalidReason.NO_POSITION)
        val target = glideTargetProjector.project(taskSnapshot, route)
        return finalGlideUseCase.solve(
            completeData = data,
            windState = windState,
            target = target
        )
    }
}
