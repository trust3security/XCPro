package com.example.xcpro

import java.time.LocalDateTime
import java.util.UUID

data class Waypoint(
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: String
)

// ================================
// TASK DATA MODELS
// ================================

data class SoaringTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: TaskType = TaskType.RACING,
    val waypoints: List<TaskWaypoint> = emptyList(),
    val distance: Double = 0.0,
    val created: LocalDateTime = LocalDateTime.now()
)

enum class TaskType(val displayName: String) {
    RACING("Racing Task"),
    AAT("Assigned Area Task")
}

data class TaskWaypoint(
    val waypoint: Waypoint,
    val role: WaypointRole,
    val radius: Double = 500.0
)

enum class WaypointRole(val displayName: String) {
    START("Start"),
    TURNPOINT("Turnpoint"),
    FINISH("Finish")
}

