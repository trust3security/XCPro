package com.example.xcpro.weather.wind.data

import android.hardware.SensorManager
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlin.math.pow
import kotlin.math.sqrt

class WindSensorInputAdapter @Inject constructor(
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun adapt(source: SensorDataSource, airspeedSource: AirspeedDataSource): WindSensorInputs {
        val gpsFlow: StateFlow<GpsSample?> = source.gpsFlow
            .map { gps ->
                gps?.let {
                    GpsSample(
                        latitude = it.position.latitude,
                        longitude = it.position.longitude,
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

        val gLoadFlow: StateFlow<GLoadSample?> = source.rawAccelFlow
            .map { raw -> raw?.let { toGLoadSample(it) } }
            .scan(null as GLoadSample?) { previous, current -> smoothGLoad(previous, current) }
            .stateIn(scope, SharingStarted.Eagerly, null)

        // Airspeed is optional; provide a real source when available (e.g., external vario).
        val airspeedFlow: StateFlow<AirspeedSample?> = airspeedSource.airspeedFlow

        return WindSensorInputs(
            gps = gpsFlow,
            pressure = pressureFlow,
            airspeed = airspeedFlow,
            heading = headingFlow,
            gLoad = gLoadFlow
        )
    }

    private fun pressureToAltitudeMeters(pressureHpa: Double): Double {
        if (!pressureHpa.isFinite() || pressureHpa <= 0.0) return Double.NaN
        // AI-NOTE: Standard-atmosphere approximation (QNH 1013.25) for pressure altitude.
        return 44330.0 * (1.0 - (pressureHpa / SEA_LEVEL_PRESSURE_HPA).pow(0.1903))
    }

    private fun toGLoadSample(raw: com.example.xcpro.sensors.RawAccelData): GLoadSample? {
        val magnitude = sqrt(raw.x * raw.x + raw.y * raw.y + raw.z * raw.z)
        if (!magnitude.isFinite()) return null
        val gLoad = magnitude / GRAVITY_MS2
        if (!gLoad.isFinite()) return null
        return GLoadSample(
            gLoad = gLoad,
            timestampMillis = raw.timestamp,
            isReliable = raw.isReliable
        )
    }

    private fun smoothGLoad(previous: GLoadSample?, current: GLoadSample?): GLoadSample? {
        if (current == null) return null
        if (previous == null || !current.isReliable || !previous.isReliable) return current
        val dtMs = (current.timestampMillis - previous.timestampMillis).coerceAtLeast(0L)
        if (dtMs == 0L || dtMs > MAX_SMOOTH_GAP_MS) return current
        val alpha = dtMs / (GLOAD_SMOOTH_TAU_MS + dtMs)
        val smoothed = previous.gLoad + alpha * (current.gLoad - previous.gLoad)
        return current.copy(gLoad = smoothed)
    }

    private companion object {
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
        private const val GRAVITY_MS2 = 9.80665
        private const val GLOAD_SMOOTH_TAU_MS = 200.0
        private const val MAX_SMOOTH_GAP_MS = 1_000L
    }
}
