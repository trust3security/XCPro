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
    val customRadius: Double? = null, // km; null means use default
    val customPointType: String? = null, // e.g., "START_LINE", "CYLINDER"
    val customParameters: Map<String, Any> = emptyMap() // Additional task-specific custom settings
) {
    fun getEffectiveRadius(taskSpecificTurnpointDefault: Double): Double {
        return customRadius ?: when (role) {
            WaypointRole.START -> 10.0
            WaypointRole.FINISH -> 3.0
            WaypointRole.TURNPOINT -> taskSpecificTurnpointDefault
            WaypointRole.OPTIONAL -> taskSpecificTurnpointDefault
        }
    }

    val hasCustomizations: Boolean get() =
        customRadius != null || customPointType != null || customParameters.isNotEmpty()
}

enum class TaskType {
    RACING,
    AAT
}

