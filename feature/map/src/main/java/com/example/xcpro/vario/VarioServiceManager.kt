package com.example.xcpro.vario

import android.util.Log
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.hawk.HawkConfigRepository
import com.example.xcpro.hawk.HawkVarioRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-wide manager that keeps the sensor/vario pipeline alive even when no UI is visible.
 * Owned by VarioForegroundService and exposed to UI components via dependency injection.
 */
@Singleton
open class VarioServiceManager @Inject constructor(
    val audioFocusManager: AudioFocusManager,
    val unifiedSensorManager: UnifiedSensorManager,
    val sensorFusionRepository: SensorFusionRepository,
    private val flightDataRepository: FlightDataRepository,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    private val hawkConfigRepository: HawkConfigRepository,
    private val hawkVarioRepository: HawkVarioRepository,
    val flightStateSource: FlightStateSource
) {

    companion object {
        private const val TAG = "VarioServiceManager"
        private const val SENSOR_RETRY_DELAY_MS = 5_000L
        private const val GPS_UPDATE_INTERVAL_SLOW_MS = 1_000L
        private const val GPS_UPDATE_INTERVAL_FAST_MS = 200L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var running = false
    private var collectionJob: Job? = null
    private var configJob: Job? = null
    private var gpsCadenceJob: Job? = null
    private var lastGpsIntervalMs: Long? = null
    private val sensorRetryCoordinator = SensorRetryCoordinator(serviceScope, SENSOR_RETRY_DELAY_MS)

    open suspend fun start(): Boolean {
        // Always reset the data source to LIVE so live sensor updates are not gated out after a replay.
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)

        if (running && unifiedSensorManager.getSensorStatus().gpsStarted) {
            Log.d(TAG, "Sensors already running")
            return true
        }

        if (!running) {
            running = true
            Log.d(TAG, "Starting sensors + flight data collection")
            observeLevoPreferences()
            startCollection()
            hawkVarioRepository.start()
            observeGpsCadence()
        }

        cancelSensorRetry()
        applyGpsUpdateInterval(GPS_UPDATE_INTERVAL_SLOW_MS)
        val sensorsStarted = startSensorsOnMainThread()
        if (!sensorsStarted) {
            Log.w(TAG, "GPS listener not registered; scheduling retry every ${SENSOR_RETRY_DELAY_MS} ms")
            scheduleSensorRetry()
        }
        return sensorsStarted
    }

    open fun stop() {
        if (!running) return
        running = false
        Log.d(TAG, "Stopping sensors + flight data collection")
        cancelSensorRetry()
        unifiedSensorManager.stopAllSensors()
        sensorFusionRepository.stop()
        hawkVarioRepository.stop()
        configJob?.cancel()
        configJob = null
        gpsCadenceJob?.cancel()
        gpsCadenceJob = null
        lastGpsIntervalMs = null
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        collectionJob?.cancel()
        collectionJob = null
    }

    private fun startCollection() {
        if (collectionJob != null) return
        collectionJob = serviceScope.launch {
            sensorFusionRepository.flightDataFlow.collectLatest { data ->
                flightDataRepository.update(data, FlightDataRepository.Source.LIVE)
            }
        }
    }

    private fun observeLevoPreferences() {
        if (configJob != null) return
        configJob = serviceScope.launch {
            levoVarioPreferencesRepository.config.collectLatest { config ->
                sensorFusionRepository.setMacCreadySetting(config.macCready)
                sensorFusionRepository.setMacCreadyRisk(config.macCreadyRisk)
                sensorFusionRepository.setAutoMcEnabled(config.autoMcEnabled)
                sensorFusionRepository.updateAudioSettings(config.audioSettings)
                sensorFusionRepository.setHawkAudioEnabled(config.enableHawkUi)
                hawkConfigRepository.setEnabled(config.showHawkCard || config.enableHawkUi)
            }
        }
    }

    fun setFlightMode(mode: FlightMode) {
        sensorFusionRepository.setFlightMode(mode)
    }

    private fun observeGpsCadence() {
        if (gpsCadenceJob != null) return
        gpsCadenceJob = serviceScope.launch {
            combine(flightDataRepository.activeSource, flightStateSource.flightState) { source, state ->
                GpsCadencePolicy.select(source, state)
            }
                .distinctUntilChanged()
                .collectLatest { cadence ->
                    val interval = when (cadence) {
                        GpsCadenceMode.FAST -> GPS_UPDATE_INTERVAL_FAST_MS
                        GpsCadenceMode.SLOW -> GPS_UPDATE_INTERVAL_SLOW_MS
                    }
                    applyGpsUpdateInterval(interval)
                }
        }
    }

    private fun scheduleSensorRetry() {
        sensorRetryCoordinator.schedule {
            val started = startSensorsOnMainThread()
            if (started) {
                Log.d(TAG, "Sensor retry succeeded")
            } else {
                Log.w(TAG, "Retry attempt could not start GPS listener; waiting for permissions")
            }
            started
        }
    }

    private fun cancelSensorRetry() {
        sensorRetryCoordinator.cancel()
    }

    private suspend fun startSensorsOnMainThread(): Boolean {
        return try {
            withContext(Dispatchers.Main.immediate) {
                unifiedSensorManager.startAllSensors()
            }
        } catch (t: Throwable) {
            throw RuntimeException("Failed to start sensors", t)
        }
    }

    private suspend fun applyGpsUpdateInterval(intervalMs: Long) {
        if (lastGpsIntervalMs == intervalMs) return
        lastGpsIntervalMs = intervalMs
        withContext(Dispatchers.Main.immediate) {
            unifiedSensorManager.setGpsUpdateIntervalMs(intervalMs)
        }
        Log.d(TAG, "GPS update interval set to ${intervalMs}ms")
    }
}

