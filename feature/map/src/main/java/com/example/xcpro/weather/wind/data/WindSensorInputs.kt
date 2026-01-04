package com.example.xcpro.weather.wind.data

import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface WindSensorInputs {
    val gps: StateFlow<GpsSample?>
    val pressure: StateFlow<PressureSample?>
    val airspeed: StateFlow<AirspeedSample?>
    val heading: StateFlow<HeadingSample?>
}

class WindSensorInputAdapter(
    sensorDataSource: SensorDataSource,
    scope: CoroutineScope,
    airspeedFlow: StateFlow<AirspeedSample?> = MutableStateFlow(null)
) : WindSensorInputs {

    override val gps: StateFlow<GpsSample?> = sensorDataSource.gpsFlow
        .map { it?.toGpsSample() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val pressure: StateFlow<PressureSample?> = sensorDataSource.baroFlow
        .map { it?.toPressureSample() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val airspeed: StateFlow<AirspeedSample?> = airspeedFlow

    override val heading: StateFlow<HeadingSample?> = sensorDataSource.compassFlow
        .map { it?.toHeadingSample() }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

private fun GPSData.toGpsSample(): GpsSample = GpsSample(
    latitude = latLng.latitude,
    longitude = latLng.longitude,
    altitudeMeters = altitude.value,
    groundSpeedMs = speed.value,
    trackDeg = bearing,
    timestampMillis = timestamp,
    accuracyMeters = accuracy.toDouble()
)

private fun BaroData.toPressureSample(): PressureSample {
    val pressureHpa = pressureHPa.value
    return PressureSample(
        pressureHpa = pressureHpa,
        pressureAltitudeMeters = pressureToAltitudeMeters(pressureHpa, STANDARD_QNH_HPA),
        timestampMillis = timestamp
    )
}

private fun CompassData.toHeadingSample(): HeadingSample = HeadingSample(
    headingDeg = heading,
    timestampMillis = timestamp,
    isReliable = accuracy >= MIN_COMPASS_ACCURACY
)

// Standard-atmosphere pressure altitude for QNH 1013.25 hPa.
private fun pressureToAltitudeMeters(pressureHpa: Double, qnhHpa: Double): Double {
    if (!pressureHpa.isFinite() || pressureHpa <= 0.0) return Double.NaN
    val ratio = (pressureHpa / qnhHpa).coerceIn(0.01, 1.5)
    return (1 - ratio.pow(1.0 / EXPONENT)) * (SEA_LEVEL_TEMP_K / LAPSE_RATE_K_PER_M)
}

private const val MIN_COMPASS_ACCURACY = 2
private const val STANDARD_QNH_HPA = 1013.25
private const val SEA_LEVEL_TEMP_K = 288.15
private const val LAPSE_RATE_K_PER_M = 0.0065
private const val EXPONENT = 5.255
