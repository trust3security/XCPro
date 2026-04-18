package com.trust3.xcpro.orientation

import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.CompassData
import kotlinx.coroutines.flow.StateFlow

interface OrientationSensorInputSource {
    val compassFlow: StateFlow<CompassData?>
    val attitudeFlow: StateFlow<AttitudeData?>
}

interface OrientationStationaryHeadingPolicy {
    val allowHeadingWhileStationary: Boolean
}

data class OrientationHeadingDebugSnapshot(
    val inputSource: String,
    val activeSource: String,
    val activeHeading: Double?,
    val compassHeading: Double?,
    val compassAgeMs: Long?,
    val compassReliable: Boolean,
    val attitudeHeading: Double?,
    val attitudeAgeMs: Long?,
    val attitudeReliable: Boolean
)
