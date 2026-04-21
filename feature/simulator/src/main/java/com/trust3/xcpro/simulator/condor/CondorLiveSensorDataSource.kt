package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.sensors.AccelData
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.BaroData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.RawAccelData
import com.trust3.xcpro.sensors.SensorDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class CondorLiveSensorDataSource @Inject constructor(
    sampleRepository: CondorLiveSampleRepository
) : SensorDataSource {

    override val gpsFlow: StateFlow<GPSData?> = sampleRepository.gpsFlow
    override val baroFlow: StateFlow<BaroData?> = MutableStateFlow(null)
    override val compassFlow: StateFlow<CompassData?> = MutableStateFlow(null)
    override val rawAccelFlow: StateFlow<RawAccelData?> = MutableStateFlow(null)
    override val accelFlow: StateFlow<AccelData?> = MutableStateFlow(null)
    override val attitudeFlow: StateFlow<AttitudeData?> = MutableStateFlow(null)
}
