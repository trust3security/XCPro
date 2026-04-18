package com.trust3.xcpro

import com.trust3.xcpro.orientation.OrientationSensorInputSource
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class MapOrientationSensorInputSource @Inject constructor(
    private val unifiedSensorManager: UnifiedSensorManager
) : OrientationSensorInputSource {
    override val compassFlow: StateFlow<CompassData?> = unifiedSensorManager.compassFlow
    override val attitudeFlow: StateFlow<AttitudeData?> = unifiedSensorManager.attitudeFlow
}
