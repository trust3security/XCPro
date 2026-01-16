package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.models.RacingWaypointRole

enum class RacingNavigationEventType {
    START,
    TURNPOINT,
    FINISH
}

data class RacingNavigationEvent(
    val type: RacingNavigationEventType,
    val fromLegIndex: Int,
    val toLegIndex: Int,
    val waypointRole: RacingWaypointRole,
    val timestampMillis: Long
)
