package com.example.xcpro.vario

import android.util.Log
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.NoopIgcRecordingActionSink
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import com.example.xcpro.igc.usecase.IgcRecordingUseCase
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.hawk.HawkConfigRepository
import com.example.xcpro.hawk.HawkVarioRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    val flightStateSource: FlightStateSource,
    private val igcRecordingUseCase: IgcRecordingUseCase? = null,
    private val igcRecordingActionSink: IgcRecordingActionSink = NoopIgcRecordingActionSink
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
    private var igcActionJob: Job? = null
    private var lastGpsIntervalMs: Long? = null
    private val sensorRetryCoordinator = SensorRetryCoordinator(serviceScope, SENSOR_RETRY_DELAY_MS)

    open suspend fun start(): Boolean {
        // Always reset the data source to LIVE so live sensor updates are not gated out after a replay.
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)

        if (running && isPipelineReady(unifiedSensorManager.getSensorStatus())) {
            Log.d(TAG, "Sensors already running")
            return true
        }

        if (!running) {
            running = true
            Log.d(TAG, "Starting sensors + flight data collection")
            observeLevoPreferences()
            startCollection()
            observeIgcSessionActions()
            hawkVarioRepository.start()
            observeGpsCadence()
        }

        cancelSensorRetry()
        applyGpsUpdateInterval(GPS_UPDATE_INTERVAL_SLOW_MS)
        startSensorsOnMainThread()
        val startupStatus = unifiedSensorManager.getSensorStatus()
        val pipelineReady = isPipelineReady(startupStatus)
        if (!pipelineReady) {
            Log.w(
                TAG,
                "Sensors not fully ready (gpsStarted=${startupStatus.gpsStarted}, " +
                    "baroAvailable=${startupStatus.baroAvailable}, baroStarted=${startupStatus.baroStarted}); " +
                    "scheduling retry every ${SENSOR_RETRY_DELAY_MS} ms"
            )
            scheduleSensorRetry()
        }
        return pipelineReady
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
        igcActionJob?.cancel()
        igcActionJob = null
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
                sensorFusionRepository.setTotalEnergyCompensationEnabled(config.teCompensationEnabled)
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

    private fun observeIgcSessionActions() {
        val useCase = igcRecordingUseCase ?: return
        if (igcActionJob != null) return
        igcActionJob = serviceScope.launch {
            useCase.actions.collectLatest { action ->
                when (action) {
                    is IgcSessionStateMachine.Action.EnterArmed -> {
                        Log.d(
                            TAG,
                            "IGC session armed at mono=${action.monoTimeMs}"
                        )
                        igcRecordingActionSink.onSessionArmed(action.monoTimeMs)
                    }
                    is IgcSessionStateMachine.Action.StartRecording -> {
                        Log.d(
                            TAG,
                            "IGC recording started: sessionId=${action.sessionId}, preFlightWindowMs=${action.preFlightGroundWindowMs}"
                        )
                        igcRecordingActionSink.onStartRecording(
                            action.sessionId,
                            action.preFlightGroundWindowMs
                        )
                    }
                    is IgcSessionStateMachine.Action.FinalizeRecording -> {
                        Log.d(
                            TAG,
                            "IGC finalize requested: sessionId=${action.sessionId}, postFlightWindowMs=${action.postFlightGroundWindowMs}"
                        )
                        val finalizeError = runCatching {
                            igcRecordingActionSink.onFinalizeRecording(
                                action.sessionId,
                                action.postFlightGroundWindowMs
                            )
                        }.exceptionOrNull()
                        if (finalizeError == null) {
                            useCase.onFinalizeSucceeded()
                        } else {
                            useCase.onFinalizeFailed(
                                finalizeError.message ?: "IGC finalization failed"
                            )
                        }
                    }
                    is IgcSessionStateMachine.Action.MarkCompleted -> {
                        Log.d(
                            TAG,
                            "IGC session completed: sessionId=${action.sessionId}"
                        )
                        igcRecordingActionSink.onMarkCompleted(action.sessionId)
                    }
                    is IgcSessionStateMachine.Action.MarkFailed -> {
                        Log.d(
                            TAG,
                            "IGC session failed: sessionId=${action.sessionId}, reason=${action.reason}"
                        )
                        igcRecordingActionSink.onMarkFailed(action.sessionId, action.reason)
                    }
                }
            }
        }
    }

    private fun scheduleSensorRetry() {
        sensorRetryCoordinator.schedule {
            startSensorsOnMainThread()
            val status = unifiedSensorManager.getSensorStatus()
            val ready = isPipelineReady(status)
            if (ready) {
                Log.d(TAG, "Sensor retry succeeded")
            } else {
                Log.w(
                    TAG,
                    "Retry sensors not ready yet (gpsStarted=${status.gpsStarted}, " +
                        "baroAvailable=${status.baroAvailable}, baroStarted=${status.baroStarted})"
                )
            }
            ready
        }
    }

    private fun cancelSensorRetry() {
        sensorRetryCoordinator.cancel()
    }

    private suspend fun startSensorsOnMainThread(): Boolean {
        return runCatching {
            withContext(Dispatchers.Main.immediate) {
                unifiedSensorManager.startAllSensors()
            }
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            Log.e(TAG, "Failed to start sensors on main thread", error)
            false
        }
    }

    private suspend fun applyGpsUpdateInterval(intervalMs: Long) {
        if (lastGpsIntervalMs == intervalMs) return
        val applied = runCatching {
            withContext(Dispatchers.Main.immediate) {
                unifiedSensorManager.setGpsUpdateIntervalMs(intervalMs)
            }
        }.onFailure { error ->
            if (error is CancellationException) {
                throw error
            }
            Log.e(TAG, "Failed to set GPS update interval to ${intervalMs}ms", error)
        }.isSuccess
        if (!applied) {
            return
        }
        lastGpsIntervalMs = intervalMs
        Log.d(TAG, "GPS update interval set to ${intervalMs}ms")
    }

    private fun isPipelineReady(status: SensorStatus): Boolean {
        val gpsReady = status.gpsStarted
        val baroReady = !status.baroAvailable || status.baroStarted
        return gpsReady && baroReady
    }
}

