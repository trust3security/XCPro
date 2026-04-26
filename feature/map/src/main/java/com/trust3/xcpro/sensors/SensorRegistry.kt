package com.trust3.xcpro.sensors

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
import androidx.core.location.LocationCompat
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.core.time.Clock

/**
 * Handles sensor registration/start/stop and pushes raw data via callbacks.
 * Pure wiring; no business logic or flow ownership lives here.
 */
internal class SensorRegistry(
    private val context: Context,
    private val locationManager: LocationManager,
    private val sensorManager: SensorManager,
    private val orientationProcessor: OrientationProcessor,
    private val clock: Clock,
    private val gpsCadenceDiagnostics: LiveGpsCadenceDiagnostics,
    private val updateGps: (GPSData) -> Unit,
    private val updateBaro: (BaroData) -> Unit,
    private val updateCompass: (CompassData) -> Unit,
    private val updateRawAccel: (RawAccelData) -> Unit,
    private val updateAttitude: (AttitudeData) -> Unit,
    private val updateAccel: (AccelData) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "UnifiedSensorManager"
        private const val GPS_UPDATE_INTERVAL_MS = 1000L
        private const val GPS_UPDATE_INTERVAL_MIN_MS = 200L
        private const val GPS_UPDATE_INTERVAL_MAX_MS = 2000L
        private const val GPS_MIN_DISTANCE_M = 0f
        private const val BARO_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
        private const val COMPASS_SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI
        private const val ACCEL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    }

    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rawAccelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var isGpsStarted = false
    private var isBaroStarted = false
    private var isCompassStarted = false
    private var isRawAccelStarted = false
    private var isAccelStarted = false
    private var isRotationStarted = false

    @Volatile
    private var gpsUpdateIntervalMs: Long = GPS_UPDATE_INTERVAL_MS

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val monotonicMillis = location.elapsedRealtimeNanos / 1_000_000L
            val bearingAccuracyDeg = if (LocationCompat.hasBearingAccuracy(location)) {
                LocationCompat.getBearingAccuracyDegrees(location).toDouble()
            } else null
            val speedAccuracyMs = if (LocationCompat.hasSpeedAccuracy(location)) {
                LocationCompat.getSpeedAccuracyMetersPerSecond(location).toDouble()
            } else null
            val gpsData = GPSData(
                position = GeoPoint(location.latitude, location.longitude),
                altitude = AltitudeM(if (location.hasAltitude()) location.altitude else 0.0),
                speed = SpeedMs(if (location.hasSpeed()) location.speed.toDouble() else 0.0),
                bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0,
                accuracy = location.accuracy,
                bearingAccuracyDeg = bearingAccuracyDeg,
                speedAccuracyMs = speedAccuracyMs,
                timestamp = location.time,
                monotonicTimestampMillis = monotonicMillis
            )
            gpsCadenceDiagnostics.recordGpsCallback(
                monotonicTimestampMs = monotonicMillis,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                bearingAccuracyDeg = bearingAccuracyDeg,
                speedAccuracyMs = speedAccuracyMs
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
                val monotonicMillis = event.timestamp / 1_000_000L
                val pressureHPa = event.values[0].toDouble()
                val wallTime = clock.nowWallMs()
                updateBaro(
                    BaroData(
                        pressureHPa = PressureHpa(pressureHPa),
                        timestamp = wallTime,
                        monotonicTimestampMillis = monotonicMillis
                    )
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val monotonicMillis = event.timestamp / 1_000_000L
                val x = event.values[0]
                val y = event.values[1]
                val heading = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))
                val normalizedHeading = (heading + 360) % 360
                val wallTime = clock.nowWallMs()
                updateCompass(
                    CompassData(
                        heading = normalizedHeading,
                        accuracy = event.accuracy,
                        timestamp = wallTime,
                        monotonicTimestampMillis = monotonicMillis
                    )
                )
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                val monotonicMillis = event.timestamp / 1_000_000L
                orientationProcessor.updateRotationVector(event.values)
                val wallTime = clock.nowWallMs()
                orientationProcessor.attitude()?.let { attitude ->
                    updateAttitude(
                        AttitudeData(
                            headingDeg = attitude.headingDeg,
                            pitchDeg = attitude.pitchDeg,
                            rollDeg = attitude.rollDeg,
                            timestamp = wallTime,
                            isReliable = attitude.isReliable,
                            monotonicTimestampMillis = monotonicMillis
                        )
                    )
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val monotonicMillis = event.timestamp / 1_000_000L
                val sample = orientationProcessor.projectVerticalAcceleration(event.values)
                val wallTime = clock.nowWallMs()
                updateAccel(
                    AccelData(
                        verticalAcceleration = sample.verticalAcceleration,
                        timestamp = wallTime,
                        isReliable = sample.isReliable,
                        monotonicTimestampMillis = monotonicMillis
                    )
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val monotonicMillis = event.timestamp / 1_000_000L
                val wallTime = clock.nowWallMs()
                updateRawAccel(
                    RawAccelData(
                        x = event.values[0].toDouble(),
                        y = event.values[1].toDouble(),
                        z = event.values[2].toDouble(),
                        timestamp = wallTime,
                        isReliable = event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE,
                        monotonicTimestampMillis = monotonicMillis
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
        val rawAccelStarted = startRawAccelerometer()
        val accelStarted = startAccelerometer()
        Log.d(
            TAG,
            "Sensor start status -> gps=$gpsStarted, baro=$baroStarted, compass=$compassStarted, rotation=$rotationStarted, rawAccel=$rawAccelStarted, accel=$accelStarted"
        )
        return gpsStarted
    }

    fun stopAll() {
        stopGPS()
        stopBarometer()
        stopCompass()
        stopRotationVector()
        stopRawAccelerometer()
        stopAccelerometer()
    }

    fun setGpsUpdateIntervalMs(intervalMs: Long) {
        val clamped = intervalMs.coerceIn(GPS_UPDATE_INTERVAL_MIN_MS, GPS_UPDATE_INTERVAL_MAX_MS)
        gpsCadenceDiagnostics.recordRequestedInterval(
            requestedMs = intervalMs,
            clampedMs = clamped
        )
        if (gpsUpdateIntervalMs == clamped) return
        gpsUpdateIntervalMs = clamped
        if (isGpsStarted) {
            restartGps()
        }
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
                    gpsUpdateIntervalMs,
                    GPS_MIN_DISTANCE_M,
                    gpsListener
                )
                gpsProviderStarted = true
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

    private fun restartGps() {
        stopGPS()
        startGPS()
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

    private fun startRawAccelerometer(): Boolean {
        if (isRawAccelStarted) return true
        val sensor = rawAccelerometerSensor ?: return false
        val success = sensorManager.registerListener(this, sensor, ACCEL_SENSOR_DELAY)
        if (success) isRawAccelStarted = true
        return success
    }

    private fun stopRawAccelerometer() {
        if (!isRawAccelStarted) return
        rawAccelerometerSensor?.let { sensorManager.unregisterListener(this, it) }
        isRawAccelStarted = false
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermissions()) return null
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    fun isGpsEnabled(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
