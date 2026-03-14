package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.SimpleRacingTask
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole

internal fun buildLineStartTask(): SimpleRacingTask {
    val start = RacingWaypoint.createWithStandardizedDefaults(
        id = "start",
        title = "Start",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = RacingWaypointRole.START,
        startPointType = RacingStartPointType.START_LINE
    )
    val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
        id = "tp1",
        title = "TP1",
        subtitle = "",
        lat = 0.0,
        lon = 0.1,
        role = RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
    )
    val finish = RacingWaypoint.createWithStandardizedDefaults(
        id = "finish",
        title = "Finish",
        subtitle = "",
        lat = 0.0,
        lon = 0.2,
        role = RacingWaypointRole.FINISH,
        finishPointType = RacingFinishPointType.FINISH_CYLINDER
    )
    return SimpleRacingTask(
        id = "task",
        waypoints = listOf(start, turnpoint, finish)
    )
}

internal fun buildFinishLineTask(): SimpleRacingTask {
    val start = RacingWaypoint.createWithStandardizedDefaults(
        id = "start-finish-line",
        title = "Start",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = RacingWaypointRole.START,
        startPointType = RacingStartPointType.START_LINE
    )
    val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
        id = "tp-finish-line",
        title = "TP1",
        subtitle = "",
        lat = 0.0,
        lon = 0.1,
        role = RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
    )
    val finish = RacingWaypoint.createWithStandardizedDefaults(
        id = "finish-line",
        title = "Finish",
        subtitle = "",
        lat = 0.0,
        lon = 0.2,
        role = RacingWaypointRole.FINISH,
        finishPointType = RacingFinishPointType.FINISH_LINE,
        customGateWidthMeters = 3_000.0
    )
    return SimpleRacingTask(
        id = "task-finish-line",
        waypoints = listOf(start, turnpoint, finish)
    )
}

internal fun buildCylinderStartTask(): SimpleRacingTask {
    val start = RacingWaypoint.createWithStandardizedDefaults(
        id = "start-cylinder",
        title = "Start",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = RacingWaypointRole.START,
        startPointType = RacingStartPointType.START_CYLINDER,
        customGateWidthMeters = 10_000.0
    )
    val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
        id = "tp-cylinder",
        title = "TP1",
        subtitle = "",
        lat = 0.0,
        lon = 0.2,
        role = RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
    )
    val finish = RacingWaypoint.createWithStandardizedDefaults(
        id = "finish-cylinder",
        title = "Finish",
        subtitle = "",
        lat = 0.0,
        lon = 0.3,
        role = RacingWaypointRole.FINISH,
        finishPointType = RacingFinishPointType.FINISH_CYLINDER
    )
    return SimpleRacingTask(
        id = "task-cylinder-start",
        waypoints = listOf(start, turnpoint, finish)
    )
}

internal fun buildSectorStartTask(): SimpleRacingTask {
    val start = RacingWaypoint.createWithStandardizedDefaults(
        id = "start-sector",
        title = "Start",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = RacingWaypointRole.START,
        startPointType = RacingStartPointType.FAI_START_SECTOR,
        customGateWidthMeters = 10_000.0
    )
    val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
        id = "tp-sector",
        title = "TP1",
        subtitle = "",
        lat = 0.0,
        lon = 0.2,
        role = RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
    )
    val finish = RacingWaypoint.createWithStandardizedDefaults(
        id = "finish-sector",
        title = "Finish",
        subtitle = "",
        lat = 0.0,
        lon = 0.3,
        role = RacingWaypointRole.FINISH,
        finishPointType = RacingFinishPointType.FINISH_CYLINDER
    )
    return SimpleRacingTask(
        id = "task-sector-start",
        waypoints = listOf(start, turnpoint, finish)
    )
}

internal fun buildKeyholeTask(): SimpleRacingTask {
    val start = RacingWaypoint.createWithStandardizedDefaults(
        id = "start",
        title = "Start",
        subtitle = "",
        lat = -0.1,
        lon = 0.0,
        role = RacingWaypointRole.START,
        startPointType = RacingStartPointType.START_CYLINDER
    )
    val keyhole = RacingWaypoint.createWithStandardizedDefaults(
        id = "keyhole",
        title = "Keyhole",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = RacingWaypointRole.TURNPOINT,
        turnPointType = RacingTurnPointType.KEYHOLE,
        customGateWidthMeters = 10_000.0,
        keyholeInnerRadiusMeters = 500.0,
        keyholeAngle = 90.0
    )
    val finish = RacingWaypoint.createWithStandardizedDefaults(
        id = "finish",
        title = "Finish",
        subtitle = "",
        lat = 0.1,
        lon = 0.0,
        role = RacingWaypointRole.FINISH,
        finishPointType = RacingFinishPointType.FINISH_CYLINDER
    )
    return SimpleRacingTask(
        id = "task-keyhole",
        waypoints = listOf(start, keyhole, finish)
    )
}
