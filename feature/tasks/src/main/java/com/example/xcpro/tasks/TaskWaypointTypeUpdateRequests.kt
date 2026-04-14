package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType

/**
 * Internal edit payloads for task waypoint type mutations.
 *
 * These keep the task shell -> coordinator -> owner chain stable when the
 * waypoint type/edit surface grows, without changing task ownership.
 */
data class RacingWaypointTypeUpdate(
    val index: Int,
    val startType: RacingStartPointType? = null,
    val finishType: RacingFinishPointType? = null,
    val turnType: RacingTurnPointType? = null,
    val gateWidthMeters: Double? = null,
    val keyholeInnerRadiusMeters: Double? = null,
    val keyholeAngle: Double? = null,
    val faiQuadrantOuterRadiusMeters: Double? = null
)

data class AATWaypointTypeUpdate(
    val index: Int,
    val startType: AATStartPointType? = null,
    val finishType: AATFinishPointType? = null,
    val turnType: AATTurnPointType? = null,
    val gateWidthMeters: Double? = null,
    val keyholeInnerRadiusMeters: Double? = null,
    val keyholeAngle: Double? = null,
    val sectorOuterRadiusMeters: Double? = null
)
