package com.example.xcpro.tasks.domain.logic

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Domain policy for task proximity checks used by auto-advance logic.
 */
data class TaskProximityDecision(
    val hasEnteredObservationZone: Boolean,
    val isCloseToTarget: Boolean,
    val distanceMeters: Double,
    val zoneRadiusMeters: Double
)

class TaskProximityEvaluator @Inject constructor() {

    fun evaluate(
        taskType: TaskType,
        waypointRole: WaypointRole,
        aircraftLat: Double,
        aircraftLon: Double,
        targetLat: Double,
        targetLon: Double
    ): TaskProximityDecision {
        val distanceMeters = haversineMeters(
            lat1 = aircraftLat,
            lon1 = aircraftLon,
            lat2 = targetLat,
            lon2 = targetLon
        )
        val zoneRadiusMeters = effectiveRadiusMeters(taskType, waypointRole)
        return TaskProximityDecision(
            hasEnteredObservationZone = distanceMeters <= zoneRadiusMeters + ENTRY_BUFFER_METERS,
            isCloseToTarget = distanceMeters <= CLOSE_TO_TARGET_METERS,
            distanceMeters = distanceMeters,
            zoneRadiusMeters = zoneRadiusMeters
        )
    }

    fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double = haversineMeters(fromLat, fromLon, toLat, toLon)

    private fun effectiveRadiusMeters(taskType: TaskType, role: WaypointRole): Double =
        when (role) {
            WaypointRole.START -> 100.0
            WaypointRole.FINISH -> 3000.0
            WaypointRole.TURNPOINT,
            WaypointRole.OPTIONAL -> if (taskType == TaskType.AAT) 5000.0 else 500.0
        }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val radiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * radiusMeters * asin(sqrt(a))
    }

    private companion object {
        const val ENTRY_BUFFER_METERS = 30.0
        const val CLOSE_TO_TARGET_METERS = 200.0
    }
}
