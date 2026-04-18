package com.trust3.xcpro.map

import android.Manifest
import android.content.Context
import com.trust3.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class LocationSensorsController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sensorsUseCase: MapSensorsUseCase
) : MapLocationSensorsPort {
    companion object {
        private const val TAG = "LocationManager"
    }

    private var startRequested = false
    private var restartJob: Job? = null

    override fun onLocationPermissionsResult(fineLocationGranted: Boolean) {
        if (fineLocationGranted) {
            debugRateLimited(
                key = "permissions_granted",
                message = "Location permissions granted; ensuring background sensors are running"
            )
            scope.launch {
                runCatching { ensureSensorsRunning() }
                    .onFailure { error ->
                        if (error is CancellationException) {
                            throw error
                        }
                        errorRateLimited(
                            key = "ensure_after_permission_failure",
                            message = "Failed to ensure sensors running after permission grant",
                            error = error
                        )
                    }
            }
        } else {
            warnRateLimited(
                key = "permissions_denied",
                message = "Location permissions denied"
            )
        }
    }

    override fun requestLocationPermissions(permissionRequester: MapLocationPermissionRequester) {
        val fineLocationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineLocationGranted) {
            debugRateLimited(
                key = "permissions_already_granted",
                message = "Location permissions already granted; ensuring background sensors are running"
            )
            scope.launch {
                runCatching { ensureSensorsRunning() }
                    .onFailure { error ->
                        if (error is CancellationException) {
                            throw error
                        }
                        errorRateLimited(
                            key = "ensure_while_checking_failure",
                            message = "Failed to ensure sensors running while checking permissions",
                            error = error
                        )
                    }
            }
        } else {
            debugRateLimited(
                key = "request_permissions",
                message = "Requesting location permissions"
            )
            permissionRequester.requestLocationPermissions()
        }
    }

    override fun stopLocationTracking(force: Boolean) {
        if (!force) {
            return
        }
        AppLogger.d(TAG, "Force stopping background sensors")
        stopSensors()
    }

    /**
     * Restart sensors after returning from sleep mode
     * This ensures GPS and other sensors resume properly when screen turns back on
     */
    override fun restartSensorsIfNeeded() {
        restartJob?.cancel()
        restartJob = scope.launch {
            runCatching {
                val sensorStatus = sensorsUseCase.sensorStatus()

                // Restart if any critical sensor is not running (common after doze/background)
                val needsRestart = (
                    (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) ||
                        (!sensorStatus.baroStarted && sensorStatus.baroAvailable) ||
                        (!sensorStatus.accelStarted && sensorStatus.accelAvailable)
                    )
                if (needsRestart) {
                    warnRateLimited(
                        key = "restart_required",
                        message = "One or more sensors stopped (gpsStarted=${sensorStatus.gpsStarted}, baroStarted=${sensorStatus.baroStarted}, accelStarted=${sensorStatus.accelStarted}); restarting all sensors"
                    )

                    // Stop everything first to clean up any stale listeners
                    stopSensors()

                    // Short delay to ensure clean shutdown
                    delay(100)

                    // Restart all sensors
                    ensureSensorsRunning()

                    // Flight data fusion starts automatically with sensor data flow (no explicit start)
                    debugRateLimited(
                        key = "restart_success",
                        intervalMs = 5_000L,
                        message = "Sensors restarted successfully after sleep/doze"
                    )
                    return@runCatching
                }

                // If GPS was started but is no longer receiving updates, restart all sensors
                if (!sensorStatus.gpsStarted && sensorStatus.hasLocationPermissions) {
                    warnRateLimited(
                        key = "restart_stopped_after_resume",
                        message = "Sensors appear stopped after resume; restarting"
                    )

                    // Stop everything first to clean up any stale listeners
                    stopSensors()

                    // Short delay to ensure clean shutdown
                    delay(100)

                    // Restart all sensors
                    ensureSensorsRunning()

                    // Flight data fusion starts automatically with sensor data flow
                    // No explicit start() method needed

                    debugRateLimited(
                        key = "restart_success_after_resume",
                        intervalMs = 5_000L,
                        message = "Sensors restarted successfully after sleep mode"
                    )
                } else if (!sensorStatus.gpsStarted) {
                    debugRateLimited(
                        key = "restart_skipped_no_permission",
                        intervalMs = 5_000L,
                        message = "No location permissions; sensor restart skipped"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                errorRateLimited(
                    key = "restart_check_failure",
                    message = "Sensor restart check failed",
                    error = error
                )
            }
        }
    }

    override fun isGpsEnabled(): Boolean = sensorsUseCase.isGpsEnabled()

    private fun safeStartSensors(): Boolean {
        return runCatching { sensorsUseCase.startSensors() }
            .onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                errorRateLimited(
                    key = "start_sensors_failure",
                    message = "Failed to start sensors through use case",
                    error = error
                )
            }
            .getOrDefault(false)
    }

    private suspend fun ensureSensorsRunning() {
        val status = sensorsUseCase.sensorStatus()
        if (startRequested && status.gpsStarted) {
            return
        }
        if (!startRequested && status.gpsStarted) {
            // GPS may already be active from a prior runtime session. Reissue the
            // service-owned start request so the background collectors are live.
            startRequested = safeStartSensors()
            return
        }

        val requestAccepted = safeStartSensors()
        startRequested = requestAccepted
        if (!requestAccepted) {
            warnRateLimited(
                key = "start_deferred",
                message = "Sensor start deferred (likely waiting on location permission)"
            )
        }
    }

    private fun debugRateLimited(
        key: String,
        message: String,
        intervalMs: Long = 2_000L
    ) {
        if (AppLogger.rateLimit(TAG, key, intervalMs)) {
            AppLogger.d(TAG, message)
        }
    }

    private fun warnRateLimited(
        key: String,
        message: String,
        intervalMs: Long = 5_000L
    ) {
        if (AppLogger.rateLimit(TAG, key, intervalMs)) {
            AppLogger.w(TAG, message)
        }
    }

    private fun errorRateLimited(
        key: String,
        message: String,
        error: Throwable,
        intervalMs: Long = 5_000L
    ) {
        if (AppLogger.rateLimit(TAG, key, intervalMs)) {
            AppLogger.e(TAG, message, error)
        }
    }

    private fun stopSensors() {
        val status = sensorsUseCase.sensorStatus()
        val runtimeActive = status.gpsStarted ||
            status.baroStarted ||
            status.accelStarted ||
            status.compassStarted ||
            status.rotationStarted
        if (!startRequested && !runtimeActive) return
        sensorsUseCase.stopSensors()
        startRequested = false
    }
}
