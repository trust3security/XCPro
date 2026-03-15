package com.example.xcpro.weather.wind.data

import android.hardware.SensorManager
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlin.math.pow
import kotlin.math.sqrt

class WindSensorInputAdapter @Inject constructor() {
    fun adapt(source: SensorDataSource, airspeedSource: AirspeedDataSource): WindSensorInputs {
        val gpsFlow: Flow<GpsSample?> = source.gpsFlow
            .map { gps ->
                gps?.let {
                    val clockMillis = it.timeForCalculationsMillis
                    GpsSample(
                        latitude = it.position.latitude,
                        longitude = it.position.longitude,
                        altitudeMeters = it.altitude.value,
                        groundSpeedMs = it.speed.value,
                        trackRad = Math.toRadians(it.bearing),
                        timestampMillis = it.timestamp,
                        clockMillis = clockMillis
                    )
                }
            }

        val pressureFlow: Flow<PressureSample?> = source.baroFlow
            .map { baro ->
                baro?.let {
                    val altitude = pressureToAltitudeMeters(it.pressureHPa.value)
                    val clockMillis = resolveClockMillis(it.timestamp, it.monotonicTimestampMillis)
                    PressureSample(
                        pressureHpa = it.pressureHPa.value,
                        altitudeMeters = altitude,
                        timestampMillis = it.timestamp,
                        clockMillis = clockMillis
                    )
                }
            }

        val headingFlow: Flow<HeadingSample?> = combine(
            source.compassFlow,
            source.attitudeFlow
        ) { compass, attitude ->
            when {
                attitude?.isReliable == true -> {
                    val clockMillis = resolveClockMillis(attitude.timestamp, attitude.monotonicTimestampMillis)
                    HeadingSample(
                        headingDeg = attitude.headingDeg,
                        timestampMillis = attitude.timestamp,
                        clockMillis = clockMillis
                    )
                }
                compass != null && compass.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                    val clockMillis = resolveClockMillis(compass.timestamp, compass.monotonicTimestampMillis)
                    HeadingSample(
                        headingDeg = compass.heading,
                        timestampMillis = compass.timestamp,
                        clockMillis = clockMillis
                    )
                }
                else -> null
            }
        }

        val gLoadFlow: Flow<GLoadSample?> = source.rawAccelFlow
            .map { raw -> raw?.let { toGLoadSample(it) } }
            .scan(null as GLoadSample?) { previous, current -> smoothGLoad(previous, current) }

        // Airspeed is optional; provide a real source when available (e.g., external vario).
        val airspeedFlow: Flow<AirspeedSample?> = airspeedSource.airspeedFlow

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
            clockMillis = resolveClockMillis(raw.timestamp, raw.monotonicTimestampMillis),
            isReliable = raw.isReliable
        )
    }

    private fun smoothGLoad(previous: GLoadSample?, current: GLoadSample?): GLoadSample? {
        if (current == null) return null
        if (previous == null || !current.isReliable || !previous.isReliable) return current
        val dtMs = (current.clockMillis - previous.clockMillis).coerceAtLeast(0L)
        if (dtMs == 0L || dtMs > MAX_SMOOTH_GAP_MS) return current
        val alpha = dtMs / (GLOAD_SMOOTH_TAU_MS + dtMs)
        val smoothed = previous.gLoad + alpha * (current.gLoad - previous.gLoad)
        return current.copy(gLoad = smoothed)
    }

    private fun resolveClockMillis(timestampMillis: Long, monotonicMillis: Long): Long {
        return if (monotonicMillis > 0L) monotonicMillis else timestampMillis
    }

    private companion object {
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25
        private const val GRAVITY_MS2 = 9.80665
        private const val GLOAD_SMOOTH_TAU_MS = 200.0
        private const val MAX_SMOOTH_GAP_MS = 1_000L
    }
}
