package com.trust3.xcpro.tasks.navigation

enum class NavigationRouteKind {
    TASK_FINISH
}

enum class NavigationRouteInvalidReason {
    NO_TASK,
    PRESTART,
    INVALID_ROUTE,
    FINISHED,
    INVALID
}

data class NavigationRoutePoint(
    val lat: Double,
    val lon: Double,
    val label: String
)

data class NavigationRouteSnapshot(
    val kind: NavigationRouteKind? = null,
    val label: String = "",
    val remainingWaypoints: List<NavigationRoutePoint> = emptyList(),
    val valid: Boolean = false,
    val invalidReason: NavigationRouteInvalidReason = NavigationRouteInvalidReason.NO_TASK
)
