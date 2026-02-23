package com.example.xcpro.tasks.core

/**
 * Core task models used to decouple feature modules (Racing/AAT) from app coordination.
 * Keep these simple and immutable. No feature-specific types here.
 */
data class Task(
    val id: String,
    val waypoints: List<TaskWaypoint> = emptyList()
)

enum class WaypointRole {
    START,
    TURNPOINT,
    OPTIONAL,
    FINISH
}

data class TaskWaypoint(
    // Basic waypoint data
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,

    // Preservation fields for user customizations
    val role: WaypointRole,
    val customRadius: Double? = null, // Legacy compatibility field (km)
    val customRadiusMeters: Double? = null, // Canonical internal field (meters)
    val customPointType: String? = null, // e.g., "START_LINE", "CYLINDER"
    val customParameters: Map<String, Any> = emptyMap() // Additional task-specific custom settings
) {
    fun resolvedCustomRadiusMeters(): Double? {
        return customRadiusMeters?.takeIf { it > 0.0 }
            ?: customRadius?.takeIf { it > 0.0 }?.times(METERS_PER_KILOMETER)
    }

    fun withCustomRadiusMeters(radiusMeters: Double?): TaskWaypoint {
        val normalized = radiusMeters?.takeIf { it > 0.0 }
        return copy(
            customRadiusMeters = normalized,
            // Keep km compatibility as boundary-only state; internal normalization should not
            // propagate/update legacy km mirrors.
            customRadius = null
        )
    }

    fun getEffectiveRadiusMeters(taskSpecificTurnpointDefaultMeters: Double): Double {
        return resolvedCustomRadiusMeters() ?: when (role) {
            WaypointRole.START -> 10_000.0
            WaypointRole.FINISH -> 3_000.0
            WaypointRole.TURNPOINT -> taskSpecificTurnpointDefaultMeters
            WaypointRole.OPTIONAL -> taskSpecificTurnpointDefaultMeters
        }
    }

    val hasCustomizations: Boolean get() =
        customRadius != null ||
            customRadiusMeters != null ||
            customPointType != null ||
            customParameters.isNotEmpty()

    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }
}

enum class TaskType {
    RACING,
    AAT
}

