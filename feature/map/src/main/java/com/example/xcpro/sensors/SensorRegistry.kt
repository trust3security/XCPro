package com.example.xcpro.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import org.maplibre.android.geometry.LatLng

/**
 * Handles sensor registration/start/stop and pushes raw data via callbacks.
 * Pure wiring; no business logic or flow ownership lives here.
 */
internal class SensorRegistry(
    private val context: Context,
    private val locationManager: LocationManager,
    private val sensorManager: SensorManager,
    private val orientationProcessor: OrientationProcessor,
    private val updateGps: (GPSData) -> Unit,
    private val updateBaro: (BaroData) -> Unit,
    private val updateCompass: (CompassData) -> Unit,
    private val updateAttitude: (AttitudeData) -> Unit,
    private val updateAccel: (AccelData) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "UnifiedSensorManager"
        private const val GPS_UPDATE_INTERVAL_MS = 1000L
        private const val GPS_MIN_DISTANCE_M = 0f
        private const val BARO_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
        private const val COMPASS_SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI
        private const val ACCEL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    }

    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val linearAccelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var isGpsStarted = false
    private var isBaroStarted = false
    private var isCompassStarted = false
    private var isAccelStarted = false
    private var isRotationStarted = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val gpsData = GPSData(
                latLng = LatLng(location.latitude, location.longitude),
                altitude = AltitudeM(if (location.hasAltitude()) location.altitude else 0.0),
                speed = SpeedMs(if (location.hasSpeed()) location.speed.toDouble() else 0.0),
                bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
                accuracy = location.accuracy,
                timestamp = location.time
            )
            updateGps(gpsData)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val pressureHPa = event.values[0].toDouble()
                updateBaro(
                    BaroData(
                        pressureHPa = PressureHpa(pressureHPa),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0]
                val y = event.values[1]
                val heading = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))
                val normalizedHeading = (heading + 360) % 360
                updateCompass(
                    CompassData(
                        heading = normalizedHeading,
                        accuracy = event.accuracy,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                orientationProcessor.updateRotationVector(event.values)
                orientationProcessor.attitude()?.let { attitude ->
                    updateAttitude(
                        AttitudeData(
                            headingDeg = attitude.headingDeg,
                            pitchDeg = attitude.pitchDeg,
                            rollDeg = attitude.rollDeg,
                            timestamp = System.currentTimeMillis(),
                            isReliable = attitude.isReliable
                        )
                    )
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val sample = orientationProcessor.projectVerticalAcceleration(event.values)
                updateAccel(
                    AccelData(
                        verticalAcceleration = sample.verticalAcceleration,
                        timestamp = System.currentTimeMillis(),
                        isReliable = sample.isReliable
                    )
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor?.let { Log.d(TAG, "Sensor accuracy changed: ${it.name}, accuracy=$accuracy") }
    }

    fun startAll(): Boolean {
        val gpsStarted = startGPS()
        val baroStarted = startBarometer()
        val compassStarted = startCompass()
        val rotationStarted = startRotationVector()
        val accelStarted = startAccelerometer()
        Log.d(TAG, "Sensor start status -> gps=$gpsStarted, baro=$baroStarted, compass=$compassStarted, rotation=$rotationStarted, accel=$accelStarted")
        return gpsStarted
    }

    fun stopAll() {
        stopGPS()
        stopBarometer()
        stopCompass()
        stopRotationVector()
        stopAccelerometer()
    }

    @SuppressLint("MissingPermission")
    private fun startGPS(): Boolean {
        if (isGpsStarted) return true
        if (!hasLocationPermissions()) return false

        var gpsProviderStarted = false
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                gpsProviderStarted = true
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_UPDATE_INTERVAL_MS,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
            }
            if (gpsProviderStarted) {
                getLastKnownLocation()?.let { gpsListener.onLocationChanged(it) }
                isGpsStarted = true
            }
        } catch (_: SecurityException) {
            gpsProviderStarted = false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GPS: ${e.message}", e)
            gpsProviderStarted = false
        }
        return gpsProviderStarted
    }

    private fun stopGPS() {
        if (!isGpsStarted) return
        try {
            locationManager.removeUpdates(gpsListener)
            isGpsStarted = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping GPS: ${e.message}")
        }
    }

    private fun startBarometer(): Boolean {
        if (isBaroStarted) return true
        val sensor = pressureSensor ?: return false
        val success = sensorManager.registerListener(this, sensor, BARO_SENSOR_DELAY)
        if (success) isBaroStarted = true
        return success
    }

    private fun stopBarometer() {
        if (!isBaroStarted) return
        pressureSensor?.let { sensorManager.unregisterListener(this, it) }
        isBaroStarted = false
    }

    private fun startCompass(): Boolean {
        if (isCompassStarted) return true
        val sensor = magneticSensor ?: return false
        val success = sensorManager.registerListener(this, sensor, COMPASS_SENSOR_DELAY)
        if (success) isCompassStarted = true
        return success
    }

    private fun stopCompass() {
        if (!isCompassStarted) return
        magneticSensor?.let { sensorManager.unregisterListener(this, it) }
        isCompassStarted = false
    }

    private fun startRotationVector(): Boolean {
        if (isRotationStarted) return true
        val sensor = rotationVectorSensor ?: return false
        val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (success) isRotationStarted = true
        return success
    }

    private fun stopRotationVector() {
        if (!isRotationStarted) return
        rotationVectorSensor?.let { sensorManager.unregisterListener(this, it) }
        isRotationStarted = false
        orientationProcessor.reset()
    }

    private fun startAccelerometer(): Boolean {
        if (isAccelStarted) return true
        val sensor = linearAccelerometerSensor ?: return false
        val success = sensorManager.registerListener(this, sensor, ACCEL_SENSOR_DELAY)
        if (success) isAccelStarted = true
        return success
    }

    private fun stopAccelerometer() {
        if (!isAccelStarted) return
        linearAccelerometerSensor?.let { sensorManager.unregisterListener(this, it) }
        isAccelStarted = false
    }

    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) return null
        return try {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gpsLocation != null && networkLocation != null -> if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun status(): SensorStatus {
        return SensorStatus(
            gpsAvailable = isGpsEnabled(),
            gpsStarted = isGpsStarted,
            baroAvailable = pressureSensor != null,
            baroStarted = isBaroStarted,
            compassAvailable = magneticSensor != null,
            compassStarted = isCompassStarted,
            accelAvailable = linearAccelerometerSensor != null,
            accelStarted = isAccelStarted,
            rotationAvailable = rotationVectorSensor != null,
            rotationStarted = isRotationStarted,
            hasLocationPermissions = hasLocationPermissions()
        )
    }
}
