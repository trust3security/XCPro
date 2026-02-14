package com.example.xcpro.map

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LocationSensorsController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sensorsUseCase: MapSensorsUseCase
) {
    companion object {
        private const val TAG = "LocationManager"
    }

    private var sensorsStarted = false
    private var restartJob: Job? = null

    fun onLocationPermissionsResult(fineLocationGranted: Boolean) {
        if (fineLocationGranted) {
            Log.d(TAG, "Location permissions granted, starting background sensors")
            scope.launch { ensureSensorsRunning() }
        } else {
            Log.e(TAG, "Location permissions denied")
        }
    }

    fun checkAndRequestLocationPermissions(
        locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        Log.d(TAG, "Checking location permissions...")

        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineLocationGranted) {
            Log.d(TAG, "Location permissions already granted, starting background sensors")
            scope.launch { ensureSensorsRunning() }
        } else {
            Log.d(TAG, "Requesting location permissions...")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    fun stopLocationTracking(force: Boolean = false) {
        if (!force) {
            Log.d(TAG, "Background service keeps sensors alive (force=false)")
            return
        }
        Log.d(TAG, "Force stopping background sensors")
        stopSensors()
    }

    /**
     * Restart sensors after returning from sleep mode
     * This ensures GPS and other sensors resume properly when screen turns back on
     */
    fun restartSensorsIfNeeded() {
        restartJob?.cancel()
        restartJob = scope.launch {
            Log.d(TAG, "Checking if sensors need restart after sleep mode...")

            val sensorStatus = sensorsUseCase.sensorStatus()

            // Restart if any critical sensor is not running (common after doze/background)
            val needsRestart = (
                (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) ||
                    (!sensorStatus.baroStarted && sensorStatus.baroAvailable) ||
                    (!sensorStatus.accelStarted && sensorStatus.accelAvailable)
                )
            if (needsRestart) {
                Log.d(
                    TAG,
                    "One or more sensors stopped (gpsStarted=${sensorStatus.gpsStarted}, baroStarted=${sensorStatus.baroStarted}, accelStarted=${sensorStatus.accelStarted}) - restarting all sensors"
                )

                // Stop everything first to clean up any stale listeners
                stopSensors()

                // Short delay to ensure clean shutdown
                delay(100)

                // Restart all sensors
                ensureSensorsRunning()

                // Flight data fusion starts automatically with sensor data flow (no explicit start)
                Log.d(TAG, "Sensors restarted successfully after sleep/doze")
                return@launch
            }

            // If GPS was started but is no longer receiving updates, restart all sensors
            if (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) {
                Log.d(TAG, "Sensors appear to be stopped (likely due to sleep mode), restarting...")

                // Stop everything first to clean up any stale listeners
                stopSensors()

                // Short delay to ensure clean shutdown
                delay(100)

                // Restart all sensors
                ensureSensorsRunning()

                // Flight data fusion starts automatically with sensor data flow
                // No explicit start() method needed

                Log.d(TAG, "Sensors restarted successfully after sleep mode")
            } else if (sensorStatus.gpsStarted) {
                Log.d(TAG, "Sensors already running, no restart needed")
            } else {
                Log.d(TAG, "No location permissions, cannot restart sensors")
            }
        }
    }

    private suspend fun ensureSensorsRunning() {
        val status = sensorsUseCase.sensorStatus()
        if (sensorsStarted && status.gpsStarted) {
            return
        }
        if (!sensorsStarted && status.gpsStarted) {
            // Sensors might still be producing data from a previous session, but
            // the vario service (flight data collection, MacCready observers, etc.)
            // is not running. Make sure we spin it up so cards receive data.
            val startedNow = sensorsUseCase.startSensors()
            val statusAfterStart = sensorsUseCase.sensorStatus()
            sensorsStarted = startedNow || statusAfterStart.gpsStarted
            if (sensorsStarted) {
                return
            }
        }

        val started = sensorsUseCase.startSensors()
        val statusAfterStart = sensorsUseCase.sensorStatus()
        sensorsStarted = started || statusAfterStart.gpsStarted
        if (!sensorsStarted) {
            Log.w(TAG, "Sensor start deferred (likely waiting on location permission)")
        }
    }

    private fun stopSensors() {
        val status = sensorsUseCase.sensorStatus()
        if (!sensorsStarted && !status.gpsStarted) return
        sensorsUseCase.stopSensors()
        sensorsStarted = false
    }
}
