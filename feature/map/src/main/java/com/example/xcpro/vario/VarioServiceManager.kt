package com.example.xcpro.vario

import android.content.Context
import android.util.Log
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.UnifiedSensorManager
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
    private val sinkProvider: StillAirSinkProvider,
    private val flightDataRepository: FlightDataRepository
) {

    companion object {
        private const val TAG = "VarioServiceManager"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val unifiedSensorManager: UnifiedSensorManager = UnifiedSensorManager(context)
    val flightDataCalculator: FlightDataCalculator =
        FlightDataCalculator(context, unifiedSensorManager, serviceScope, sinkProvider)

    private var running = false
    private var collectionJob: Job? = null

    open fun start() {
        if (running) {
            Log.d(TAG, "Sensors already running")
            return
        }
        running = true
        Log.d(TAG, "Starting sensors + flight data collection")
        unifiedSensorManager.startAllSensors()
        startCollection()
    }

    open fun stop() {
        if (!running) return
        running = false
        Log.d(TAG, "Stopping sensors + flight data collection")
        unifiedSensorManager.stopAllSensors()
        flightDataCalculator.stop()
        flightDataRepository.update(null)
        collectionJob?.cancel()
        collectionJob = null
    }

    private fun startCollection() {
        if (collectionJob != null) return
        collectionJob = serviceScope.launch {
            flightDataCalculator.flightDataFlow.collectLatest { data ->
                flightDataRepository.update(data)
            }
        }
    }
}
