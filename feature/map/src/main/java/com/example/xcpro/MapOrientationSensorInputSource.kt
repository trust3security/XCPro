package com.example.xcpro

import com.example.xcpro.orientation.OrientationSensorInputSource
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class MapOrientationSensorInputSource @Inject constructor(
    private val unifiedSensorManager: UnifiedSensorManager
) : OrientationSensorInputSource {
    override val compassFlow: StateFlow<CompassData?> = unifiedSensorManager.compassFlow
    override val attitudeFlow: StateFlow<AttitudeData?> = unifiedSensorManager.attitudeFlow
}
