package com.example.xcpro.taskperformance

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.example.xcpro.tasks.navigation.NavigationRouteRepository
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import com.example.xcpro.tasks.navigation.TaskPerformanceDistanceProjector
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@ViewModelScoped
class TaskPerformanceRepository internal constructor(
    flightDataFlow: Flow<CompleteFlightData?>,
    taskSnapshotFlow: Flow<TaskRuntimeSnapshot>,
    routeFlow: Flow<NavigationRouteSnapshot>,
    racingStateFlow: Flow<RacingNavigationState>,
    private val distanceProjector: TaskPerformanceDistanceProjector
) {
    @Inject
    constructor(
        flightDataRepository: FlightDataRepository,
        taskManager: TaskManagerCoordinator,
        navigationRouteRepository: NavigationRouteRepository,
        taskNavigationController: TaskNavigationController,
        distanceProjector: TaskPerformanceDistanceProjector
    ) : this(
        flightDataFlow = flightDataRepository.flightData,
        taskSnapshotFlow = taskManager.taskSnapshotFlow,
        routeFlow = navigationRouteRepository.route,
        racingStateFlow = taskNavigationController.racingState,
        distanceProjector = distanceProjector
    )

    val taskPerformance: Flow<TaskPerformanceSnapshot> = combine(
        flightDataFlow,
        taskSnapshotFlow,
        routeFlow,
        racingStateFlow,
        ::resolveTaskPerformance
    ).distinctUntilChanged()

    private fun resolveTaskPerformance(
        completeData: CompleteFlightData?,
        taskSnapshot: TaskRuntimeSnapshot,
        route: NavigationRouteSnapshot,
        navigationState: RacingNavigationState
    ): TaskPerformanceSnapshot {
        if (taskSnapshot.taskType != TaskType.RACING || taskSnapshot.task.waypoints.size < 2) {
            return noTaskSnapshot()
        }

        if (navigationState.status == RacingNavigationStatus.INVALIDATED) {
            return invalidSnapshot(TaskPerformanceInvalidReason.INVALID)
        }

        val startRules = taskSnapshot.task.waypoints.firstOrNull()?.let { waypoint ->
            RacingStartCustomParams.from(waypoint.customParameters)
        } ?: RacingStartCustomParams()
        val acceptedStartFix = navigationState.acceptedStartFix
        val acceptedStartTimestampMillis = navigationState.acceptedStartTimestampMillis
        val acceptedStartAltitudeReference = navigationState.acceptedStartAltitudeReference
            ?: startRules.altitudeReference

        val startAltitude = resolveStartAltitude(
            navigationState = navigationState,
            acceptedStartFix = acceptedStartFix,
            altitudeReference = acceptedStartAltitudeReference
        )
        val remainingDistance = resolveRemainingDistance(
            completeData = completeData,
            route = route,
            navigationState = navigationState
        )
        val startReferenceDistanceMeters = acceptedStartFix?.let { fix ->
            distanceProjector.startReferenceDistanceMeters(taskSnapshot, fix)
        }
        val taskDistance = resolveTaskDistance(
            navigationState = navigationState,
            remainingDistanceMeters = remainingDistance.value,
            remainingDistanceValid = remainingDistance.valid,
            remainingDistanceInvalidReason = remainingDistance.reason,
            startReferenceDistanceMeters = startReferenceDistanceMeters,
            acceptedStartFix = acceptedStartFix
        )
        val taskSpeed = resolveTaskSpeed(
            completeData = completeData,
            navigationState = navigationState,
            taskDistanceMeters = taskDistance.value,
            taskDistanceValid = taskDistance.valid,
            taskDistanceInvalidReason = taskDistance.reason,
            acceptedStartTimestampMillis = acceptedStartTimestampMillis,
            acceptedStartFix = acceptedStartFix
        )
        val remainingTime = resolveRemainingTime(
            navigationState = navigationState,
            remainingDistanceMeters = remainingDistance.value,
            remainingDistanceValid = remainingDistance.valid,
            remainingDistanceInvalidReason = remainingDistance.reason,
            taskSpeedMs = taskSpeed.value,
            taskSpeedValid = taskSpeed.valid,
            taskSpeedInvalidReason = taskSpeed.reason
        )

        return TaskPerformanceSnapshot(
            taskSpeedMs = taskSpeed.value,
            taskSpeedValid = taskSpeed.valid,
            taskSpeedInvalidReason = taskSpeed.reason,
            taskDistanceMeters = taskDistance.value,
            taskDistanceValid = taskDistance.valid,
            taskDistanceInvalidReason = taskDistance.reason,
            taskRemainingDistanceMeters = remainingDistance.value,
            taskRemainingDistanceValid = remainingDistance.valid,
            taskRemainingDistanceInvalidReason = remainingDistance.reason,
            taskRemainingTimeMillis = remainingTime.value,
            taskRemainingTimeValid = remainingTime.valid,
            taskRemainingTimeBasis = remainingTime.basis,
            taskRemainingTimeInvalidReason = remainingTime.reason,
            startAltitudeMeters = startAltitude.value,
            startAltitudeValid = startAltitude.valid,
            startAltitudeInvalidReason = startAltitude.reason
        )
    }

    private fun resolveStartAltitude(
        navigationState: RacingNavigationState,
        acceptedStartFix: RacingNavigationFix?,
        altitudeReference: RacingAltitudeReference
    ): MetricValue<Double> {
        if (navigationState.status == RacingNavigationStatus.PENDING_START) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.PRESTART)
        }
        val fix = acceptedStartFix ?: return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_START)
        val altitudeMeters = when (altitudeReference) {
            RacingAltitudeReference.MSL -> fix.altitudeMslMeters
            RacingAltitudeReference.QNH -> fix.altitudeQnhMeters ?: fix.altitudeMslMeters
        }
        return if (altitudeMeters != null && altitudeMeters.isFinite()) {
            validDoubleMetric(altitudeMeters)
        } else {
            invalidDoubleMetric(TaskPerformanceInvalidReason.NO_ALTITUDE)
        }
    }

    private fun resolveRemainingDistance(
        completeData: CompleteFlightData?,
        route: NavigationRouteSnapshot,
        navigationState: RacingNavigationState
    ): MetricValue<Double> {
        if (navigationState.status == RacingNavigationStatus.PENDING_START) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.PRESTART)
        }
        if (navigationState.status == RacingNavigationStatus.FINISHED) {
            return validDoubleMetric(0.0)
        }
        if (!route.valid) {
            return invalidDoubleMetric(route.invalidReason.toTaskInvalidReason())
        }

        val gps = completeData?.gps ?: return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_POSITION)
        val latitude = gps.position.latitude
        val longitude = gps.position.longitude
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_POSITION)
        }

        val distanceMeters = distanceProjector.remainingDistanceMeters(route, latitude, longitude)
            ?: return invalidDoubleMetric(TaskPerformanceInvalidReason.INVALID_ROUTE)
        return validDoubleMetric(distanceMeters)
    }

    private fun resolveTaskDistance(
        navigationState: RacingNavigationState,
        remainingDistanceMeters: Double,
        remainingDistanceValid: Boolean,
        remainingDistanceInvalidReason: TaskPerformanceInvalidReason,
        startReferenceDistanceMeters: Double?,
        acceptedStartFix: RacingNavigationFix?
    ): MetricValue<Double> {
        if (navigationState.status == RacingNavigationStatus.PENDING_START) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.PRESTART)
        }
        if (acceptedStartFix == null) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_START)
        }
        val referenceDistanceMeters = startReferenceDistanceMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return invalidDoubleMetric(TaskPerformanceInvalidReason.INVALID_ROUTE)

        val coveredDistanceMeters = if (navigationState.status == RacingNavigationStatus.FINISHED) {
            referenceDistanceMeters
        } else if (remainingDistanceValid) {
            (referenceDistanceMeters - remainingDistanceMeters).coerceIn(0.0, referenceDistanceMeters)
        } else {
            return invalidDoubleMetric(remainingDistanceInvalidReason)
        }
        return validDoubleMetric(coveredDistanceMeters)
    }

    private fun resolveTaskSpeed(
        completeData: CompleteFlightData?,
        navigationState: RacingNavigationState,
        taskDistanceMeters: Double,
        taskDistanceValid: Boolean,
        taskDistanceInvalidReason: TaskPerformanceInvalidReason,
        acceptedStartTimestampMillis: Long?,
        acceptedStartFix: RacingNavigationFix?
    ): MetricValue<Double> {
        if (navigationState.status == RacingNavigationStatus.PENDING_START) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.PRESTART)
        }
        if (acceptedStartFix == null || acceptedStartTimestampMillis == null) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_START)
        }
        if (!taskDistanceValid) {
            return invalidDoubleMetric(taskDistanceInvalidReason)
        }

        val currentTaskTimeMillis = when (navigationState.status) {
            RacingNavigationStatus.FINISHED -> navigationState.finishCrossingTimeMillis
                ?: navigationState.lastTransitionTimeMillis.takeIf { it > 0L }
            else -> completeData?.gps?.timeForCalculationsMillis
        } ?: return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_POSITION)

        val elapsedMillis = currentTaskTimeMillis - acceptedStartTimestampMillis
        if (elapsedMillis <= 0L) {
            return invalidDoubleMetric(TaskPerformanceInvalidReason.NO_START)
        }

        val taskSpeedMs = taskDistanceMeters / (elapsedMillis / 1_000.0)
        return if (taskSpeedMs.isFinite() && taskSpeedMs >= 0.0) {
            validDoubleMetric(taskSpeedMs)
        } else {
            invalidDoubleMetric(TaskPerformanceInvalidReason.INVALID)
        }
    }

    private fun resolveRemainingTime(
        navigationState: RacingNavigationState,
        remainingDistanceMeters: Double,
        remainingDistanceValid: Boolean,
        remainingDistanceInvalidReason: TaskPerformanceInvalidReason,
        taskSpeedMs: Double,
        taskSpeedValid: Boolean,
        taskSpeedInvalidReason: TaskPerformanceInvalidReason
    ): TimeMetricValue {
        if (navigationState.status == RacingNavigationStatus.PENDING_START) {
            return invalidTimeMetric(TaskPerformanceInvalidReason.PRESTART)
        }
        if (navigationState.status == RacingNavigationStatus.FINISHED) {
            return validTimeMetric(0L)
        }
        if (!remainingDistanceValid) {
            return invalidTimeMetric(remainingDistanceInvalidReason)
        }
        if (!taskSpeedValid) {
            return invalidTimeMetric(taskSpeedInvalidReason)
        }
        if (!taskSpeedMs.isFinite() || taskSpeedMs <= MIN_CREDIBLE_SPEED_MS) {
            return invalidTimeMetric(TaskPerformanceInvalidReason.STATIC)
        }
        val remainingTimeMillis = ((remainingDistanceMeters / taskSpeedMs) * 1_000.0).roundToLong()
        return validTimeMetric(remainingTimeMillis)
    }

    private fun NavigationRouteInvalidReason.toTaskInvalidReason(): TaskPerformanceInvalidReason =
        when (this) {
            NavigationRouteInvalidReason.NO_TASK -> TaskPerformanceInvalidReason.NO_TASK
            NavigationRouteInvalidReason.PRESTART -> TaskPerformanceInvalidReason.PRESTART
            NavigationRouteInvalidReason.INVALID_ROUTE -> TaskPerformanceInvalidReason.INVALID_ROUTE
            NavigationRouteInvalidReason.FINISHED -> TaskPerformanceInvalidReason.INVALID
            NavigationRouteInvalidReason.INVALID -> TaskPerformanceInvalidReason.INVALID
        }

    private fun validDoubleMetric(value: Double): MetricValue<Double> =
        MetricValue(value = value, valid = true, reason = TaskPerformanceInvalidReason.NONE)

    private fun invalidDoubleMetric(reason: TaskPerformanceInvalidReason): MetricValue<Double> =
        MetricValue(value = Double.NaN, valid = false, reason = reason)

    private fun validTimeMetric(value: Long): TimeMetricValue =
        TimeMetricValue(
            value = value,
            valid = true,
            reason = TaskPerformanceInvalidReason.NONE,
            basis = TaskRemainingTimeBasis.ACHIEVED_TASK_SPEED
        )

    private fun invalidTimeMetric(reason: TaskPerformanceInvalidReason): TimeMetricValue =
        TimeMetricValue(value = 0L, valid = false, reason = reason, basis = null)

    private fun noTaskSnapshot(): TaskPerformanceSnapshot =
        invalidSnapshot(TaskPerformanceInvalidReason.NO_TASK)

    private fun invalidSnapshot(reason: TaskPerformanceInvalidReason): TaskPerformanceSnapshot =
        TaskPerformanceSnapshot(
            taskSpeedInvalidReason = reason,
            taskDistanceInvalidReason = reason,
            taskRemainingDistanceInvalidReason = reason,
            taskRemainingTimeInvalidReason = reason,
            startAltitudeInvalidReason = if (reason == TaskPerformanceInvalidReason.NO_TASK) {
                TaskPerformanceInvalidReason.NO_TASK
            } else {
                reason
            }
        )

    private data class MetricValue<T>(
        val value: T,
        val valid: Boolean,
        val reason: TaskPerformanceInvalidReason
    )

    private data class TimeMetricValue(
        val value: Long,
        val valid: Boolean,
        val reason: TaskPerformanceInvalidReason,
        val basis: TaskRemainingTimeBasis?
    )

    private companion object {
        const val MIN_CREDIBLE_SPEED_MS = 2.0
    }
}
