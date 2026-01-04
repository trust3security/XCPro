package com.example.xcpro.weather.wind.data

import android.hardware.SensorManager
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.pow

class WindSensorInputAdapter @Inject constructor(
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun adapt(source: SensorDataSource): WindSensorInputs {
        val gpsFlow: StateFlow<GpsSample?> = source.gpsFlow
            .map { gps ->
                gps?.let {
                    GpsSample(
                        latitude = it.latLng.latitude,
                        longitude = it.latLng.longitude,
                        altitudeMeters = it.altitude.value,
                        groundSpeedMs = it.speed.value,
                        trackRad = Math.toRadians(it.bearing),
                        timestampMillis = it.timestamp
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

        val pressureFlow: StateFlow<PressureSample?> = source.baroFlow
            .map { baro ->
                baro?.let {
                    val altitude = pressureToAltitudeMeters(it.pressureHPa.value)
                    PressureSample(
                        pressureHpa = it.pressureHPa.value,
                        altitudeMeters = altitude,
                        timestampMillis = it.timestamp
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

        val headingFlow: StateFlow<HeadingSample?> = combine(
            source.compassFlow,
            source.attitudeFlow
        ) { compass, attitude ->
            when {
                attitude?.isReliable == true -> HeadingSample(
                    headingDeg = attitude.headingDeg,
                    timestampMillis = attitude.timestamp
                )
                compass != null && compass.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE -> HeadingSample(
                    headingDeg = compass.heading,
                    timestampMillis = compass.timestamp
                )
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

        // Airspeed is optional and not wired yet (no independent TAS/IAS source).
        val airspeedFlow: StateFlow<AirspeedSample?> = MutableStateFlow(null)

        return WindSensorInputs(
            gps = gpsFlow,
            pressure = pressureFlow,
            airspeed = airspeedFlow,
            heading = headingFlow
        )
    }

    private fun pressureToAltitudeMeters(pressureHpa: Double): Double {
        if (!pressureHpa.isFinite() || pressureHpa <= 0.0) return Double.NaN
        // AI-NOTE: Standard-atmosphere approximation (QNH 1013.25) for pressure altitude.
        return 44330.0 * (1.0 - (pressureHpa / SEA_LEVEL_PRESSURE_HPA).pow(0.1903))
    }

    private companion object {
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
    }
}
