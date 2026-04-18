package com.trust3.xcpro.tasks.aat

import com.trust3.xcpro.tasks.aat.models.AATAreaShape
import com.trust3.xcpro.tasks.aat.models.AATFinishPointType
import com.trust3.xcpro.tasks.aat.models.AATStartPointType
import com.trust3.xcpro.tasks.aat.models.AATTurnPointType
import com.trust3.xcpro.tasks.core.TaskWaypoint

fun inferAATStartType(waypoint: TaskWaypoint): AATStartPointType =
    when (waypoint.customPointType) {
        AATAreaShape.CIRCLE.name -> AATStartPointType.AAT_START_CYLINDER
        AATAreaShape.SECTOR.name -> AATStartPointType.AAT_START_SECTOR
        else -> AATStartPointType.AAT_START_LINE
    }

fun inferAATFinishType(waypoint: TaskWaypoint): AATFinishPointType =
    when (waypoint.customPointType) {
        AATAreaShape.LINE.name -> AATFinishPointType.AAT_FINISH_LINE
        else -> AATFinishPointType.AAT_FINISH_CYLINDER
    }

fun inferAATTurnType(waypoint: TaskWaypoint, innerRadiusMeters: Double): AATTurnPointType =
    when {
        innerRadiusMeters > 0.0 -> AATTurnPointType.AAT_KEYHOLE
        waypoint.customPointType == AATAreaShape.SECTOR.name -> AATTurnPointType.AAT_SECTOR
        else -> AATTurnPointType.AAT_CYLINDER
    }

internal fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    else -> null
}
