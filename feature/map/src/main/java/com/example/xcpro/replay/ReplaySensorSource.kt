package com.example.xcpro.replay

import com.example.xcpro.sensors.AccelData
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.SensorDataSource
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng

class ReplaySensorSource : SensorDataSource {
    private val _gpsFlow = MutableStateFlow<GPSData?>(null)
    override val gpsFlow: StateFlow<GPSData?> = _gpsFlow.asStateFlow()

    private val _baroFlow = MutableStateFlow<BaroData?>(null)
    override val baroFlow: StateFlow<BaroData?> = _baroFlow.asStateFlow()

    private val _compassFlow = MutableStateFlow<CompassData?>(null)
    override val compassFlow: StateFlow<CompassData?> = _compassFlow.asStateFlow()

    private val _accelFlow = MutableStateFlow<AccelData?>(null)
    override val accelFlow: StateFlow<AccelData?> = _accelFlow.asStateFlow()

    private val _attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    override val attitudeFlow: StateFlow<AttitudeData?> = _attitudeFlow.asStateFlow()

    fun emitGps(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speed: Double,
        bearing: Double,
        accuracy: Float,
        timestamp: Long
    ) {
        _gpsFlow.value = GPSData(
            latLng = LatLng(latitude, longitude),
            altitude = AltitudeM(altitude),
            speed = SpeedMs(speed),
            bearing = bearing,
            accuracy = accuracy,
            timestamp = timestamp
        )
    }

    fun emitBaro(pressureHPa: Double, timestamp: Long) {
        _baroFlow.value = BaroData(pressureHPa = PressureHpa(pressureHPa), timestamp = timestamp)
    }

    fun emitCompass(heading: Double, accuracy: Int, timestamp: Long) {
        _compassFlow.value = CompassData(heading = heading, accuracy = accuracy, timestamp = timestamp)
    }

    fun reset() {
        _gpsFlow.value = null
        _baroFlow.value = null
        _compassFlow.value = null
        _accelFlow.value = null
        _attitudeFlow.value = null
    }
}
