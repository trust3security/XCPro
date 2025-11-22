package com.example.xcpro.sensors

import android.content.Context
import android.hardware.SensorManager
import android.location.LocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Public facade exposing raw sensor flows.
 * Delegates registration and updates to [SensorRegistry].
 */
class UnifiedSensorManager(private val context: Context) : SensorDataSource {

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

    private val orientationProcessor = OrientationProcessor()

    private val registry = SensorRegistry(
        context = context,
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
        orientationProcessor = orientationProcessor,
        updateGps = { _gpsFlow.value = it },
        updateBaro = { _baroFlow.value = it },
        updateCompass = { _compassFlow.value = it },
        updateAttitude = { _attitudeFlow.value = it },
        updateAccel = { _accelFlow.value = it }
    )

    /**
     * Start all sensors. Call after permissions are granted.
     */
    fun startAllSensors(): Boolean = registry.startAll()

    /**
     * Stop all sensors. Call when app backgrounds or shuts down.
     */
    fun stopAllSensors() = registry.stopAll()

    fun isGpsEnabled(): Boolean = registry.isGpsEnabled()

    fun hasLocationPermissions(): Boolean = registry.hasLocationPermissions()

    fun getSensorStatus(): SensorStatus = registry.status()
}
