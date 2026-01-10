package com.example.xcpro.vario

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import java.util.concurrent.CountDownLatch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Application-wide manager that keeps the sensor/vario pipeline alive even when no UI is visible.
 * Owned by VarioForegroundService and exposed to UI components via dependency injection.
 */
@Singleton
open class VarioServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val unifiedSensorManager: UnifiedSensorManager,
    private val sinkProvider: StillAirSinkProvider,
    private val flightDataRepository: FlightDataRepository,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    val flightStateSource: FlightStateSource,
    private val windRepository: WindSensorFusionRepository
) {

    companion object {
        private const val TAG = "VarioServiceManager"
        private const val SENSOR_RETRY_DELAY_MS = 5_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    val sensorFusionRepository: SensorFusionRepository =
        FlightDataCalculator(
            context = context,
            sensorDataSource = unifiedSensorManager,
            scope = serviceScope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            flightStateSource = flightStateSource
        )

    private var running = false
    private var collectionJob: Job? = null
    private var configJob: Job? = null
    private val sensorRetryCoordinator = SensorRetryCoordinator(serviceScope, SENSOR_RETRY_DELAY_MS)

    open fun start(): Boolean {
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
        }

        cancelSensorRetry()
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
        configJob?.cancel()
        configJob = null
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

    private fun startSensorsOnMainThread(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return unifiedSensorManager.startAllSensors()
        }

        var result = false
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result = unifiedSensorManager.startAllSensors()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }

        try {
            latch.await()
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }

        if (error != null) {
            throw RuntimeException("Failed to start sensors", error)
        }
        return result
    }
}

