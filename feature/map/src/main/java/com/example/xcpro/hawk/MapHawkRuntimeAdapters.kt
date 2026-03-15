package com.example.xcpro.hawk

import com.example.xcpro.di.LiveSource
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@Singleton
class MapHawkSensorStreamAdapter @Inject constructor(
    @LiveSource private val sensorDataSource: SensorDataSource
) : HawkSensorStreamPort {
    override val baroSamples: Flow<HawkBaroSample> = sensorDataSource.baroFlow
        .filterNotNull()
        .map { sample ->
            HawkBaroSample(
                pressureHpa = sample.pressureHPa.value,
                monotonicTimestampMillis = sample.monotonicTimestampMillis.takeIf { it > 0L }
                    ?: sample.timestamp
            )
        }

    override val accelSamples: Flow<HawkAccelSample> = sensorDataSource.accelFlow
        .filterNotNull()
        .map { sample ->
            HawkAccelSample(
                verticalAcceleration = sample.verticalAcceleration,
                monotonicTimestampMillis = sample.monotonicTimestampMillis.takeIf { it > 0L }
                    ?: sample.timestamp,
                isReliable = sample.isReliable
            )
        }
}

@Singleton
class MapHawkActiveSourceAdapter @Inject constructor(
    private val flightDataRepository: FlightDataRepository
) : HawkActiveSourcePort {
    override val activeSource: Flow<HawkRuntimeSource> = flightDataRepository.activeSource
        .map { source ->
            when (source) {
                FlightDataRepository.Source.LIVE -> HawkRuntimeSource.LIVE
                FlightDataRepository.Source.REPLAY -> HawkRuntimeSource.REPLAY
            }
        }
}
