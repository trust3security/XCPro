package com.trust3.xcpro.map

import com.trust3.xcpro.livesource.EffectiveLiveSource
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject

class MapPhoneHealthUseCase @Inject constructor(
    private val unifiedSensorManager: UnifiedSensorManager,
    private val liveSourceStatePort: LiveSourceStatePort
) {
    fun sensorStatus(): SensorStatus = unifiedSensorManager.getSensorStatus()

    fun isGpsEnabled(): Boolean = unifiedSensorManager.isGpsEnabled()

    fun isPhoneLiveSelected(): Boolean =
        liveSourceStatePort.state.value.effectiveSource == EffectiveLiveSource.PHONE
}
