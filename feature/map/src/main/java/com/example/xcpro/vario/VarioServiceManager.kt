package com.example.xcpro.vario

import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.data.IgcFinalizeResult
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
import com.example.xcpro.weglide.domain.EvaluateWeGlidePostFlightPromptUseCase
import com.example.xcpro.weglide.domain.WeGlideFinalizedFlightUploadRequest
import com.example.xcpro.weglide.domain.WeGlidePostFlightPromptCoordinator
import com.example.xcpro.weglide.notifications.WeGlidePostFlightPromptNotificationController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
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
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val igcRecordingUseCase: IgcRecordingUseCase,
    private val igcRecordingActionSink: IgcRecordingActionSink,
    private val evaluateWeGlidePostFlightPromptUseCase: EvaluateWeGlidePostFlightPromptUseCase,
    private val weGlidePostFlightPromptCoordinator: WeGlidePostFlightPromptCoordinator,
    private val weGlidePostFlightPromptNotificationController: WeGlidePostFlightPromptNotificationController
) {

    companion object {
        private const val TAG = "VarioServiceManager"
        private const val SENSOR_RETRY_DELAY_MS = 5_000L
        private const val SENSOR_RETRY_WARNING_INTERVAL_MS = 30_000L
        private const val GPS_UPDATE_INTERVAL_SLOW_MS = 1_000L
        private const val GPS_UPDATE_INTERVAL_FAST_MS = 200L
    }

    private var running = false
    private var collectionJob: Job? = null
    private var configJob: Job? = null
    private var gpsCadenceJob: Job? = null
    private var igcActionJob: Job? = null
    private var lastGpsIntervalMs: Long? = null
    private var sensorRetryCoordinator: SensorRetryCoordinator? = null

    open suspend fun start(ownerScope: CoroutineScope): Boolean {
        // Always reset the data source to LIVE so live sensor updates are not gated out after a replay.
        flightDataRepository.setActiveSource(FlightDataRepository.Source.LIVE)

        if (running && isPipelineReady(unifiedSensorManager.getSensorStatus())) {
            return true
        }

        if (!running) {
            running = true
            sensorRetryCoordinator = SensorRetryCoordinator(ownerScope, defaultDispatcher, SENSOR_RETRY_DELAY_MS)
            observeLevoPreferences(ownerScope)
            startCollection(ownerScope)
            observeIgcSessionActions(ownerScope)
            hawkVarioRepository.start()
            observeGpsCadence(ownerScope)
        }

        cancelSensorRetry()
        applyGpsUpdateInterval(GPS_UPDATE_INTERVAL_SLOW_MS)
        startSensorsOnMainThread()
        val startupStatus = unifiedSensorManager.getSensorStatus()
        val pipelineReady = isPipelineReady(startupStatus)
        if (!pipelineReady) {
            AppLogger.w(
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
        sensorRetryCoordinator = null
        lastGpsIntervalMs = null
        flightDataRepository.update(null, FlightDataRepository.Source.LIVE)
        collectionJob?.cancel()
        collectionJob = null
    }

    private fun startCollection(ownerScope: CoroutineScope) {
        if (collectionJob != null) return
        collectionJob = ownerScope.launch(defaultDispatcher) {
            sensorFusionRepository.flightDataFlow.collectLatest { data ->
                flightDataRepository.update(data, FlightDataRepository.Source.LIVE)
            }
        }
    }

    private fun observeLevoPreferences(ownerScope: CoroutineScope) {
        if (configJob != null) return
        configJob = ownerScope.launch(defaultDispatcher) {
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

    private fun observeGpsCadence(ownerScope: CoroutineScope) {
        if (gpsCadenceJob != null) return
        gpsCadenceJob = ownerScope.launch(defaultDispatcher) {
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

    private fun observeIgcSessionActions(ownerScope: CoroutineScope) {
        if (igcActionJob != null) return
        igcActionJob = ownerScope.launch(defaultDispatcher) {
            // IGC actions are ordered runtime side effects. Keep sequential
            // collection so later terminal actions cannot cancel in-flight
            // finalize work before callbacks complete.
            igcRecordingUseCase.actions.collect { action ->
                when (action) {
                    is IgcSessionStateMachine.Action.EnterArmed -> {
                        igcRecordingActionSink.onSessionArmed(action.monoTimeMs)
                    }
                    is IgcSessionStateMachine.Action.StartRecording -> {
                        igcRecordingActionSink.onStartRecording(
                            action.sessionId,
                            action.preFlightGroundWindowMs
                        )
                    }
                    is IgcSessionStateMachine.Action.FinalizeRecording -> {
                        when (
                            val finalizeResult = igcRecordingActionSink.onFinalizeRecording(
                                action.sessionId,
                                action.postFlightGroundWindowMs
                            )
                        ) {
                            is IgcFinalizeResult.Published,
                            is IgcFinalizeResult.AlreadyPublished -> {
                                publishWeGlideUploadPromptIfEligible(
                                    sessionId = action.sessionId,
                                    finalizeResult = finalizeResult
                                )
                                igcRecordingUseCase.onFinalizeSucceeded()
                            }
                            is IgcFinalizeResult.Failure -> {
                                igcRecordingUseCase.onFinalizeFailed(finalizeResult.message)
                            }
                        }
                    }
                    is IgcSessionStateMachine.Action.MarkCompleted -> {
                        igcRecordingActionSink.onMarkCompleted(action.sessionId)
                    }
                    is IgcSessionStateMachine.Action.MarkFailed -> {
                        igcRecordingActionSink.onMarkFailed(action.sessionId, action.reason)
                    }
                }
            }
        }
    }

    private fun scheduleSensorRetry() {
        sensorRetryCoordinator?.schedule {
            startSensorsOnMainThread()
            val status = unifiedSensorManager.getSensorStatus()
            val ready = isPipelineReady(status)
            if (!ready && AppLogger.rateLimit(TAG, "sensor_retry_not_ready", SENSOR_RETRY_WARNING_INTERVAL_MS)) {
                AppLogger.w(
                    TAG,
                    "Retry sensors not ready yet (gpsStarted=${status.gpsStarted}, " +
                        "baroAvailable=${status.baroAvailable}, baroStarted=${status.baroStarted})"
                )
            }
            ready
        }
    }

    private fun cancelSensorRetry() {
        sensorRetryCoordinator?.cancel()
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
            AppLogger.e(TAG, "Failed to start sensors on main thread", error)
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
            AppLogger.e(TAG, "Failed to set GPS update interval to ${intervalMs}ms", error)
        }.isSuccess
        if (!applied) {
            return
        }
        lastGpsIntervalMs = intervalMs
    }

    private fun isPipelineReady(status: SensorStatus): Boolean {
        val gpsReady = status.gpsStarted
        val baroReady = !status.baroAvailable || status.baroStarted
        return gpsReady && baroReady
    }

    private suspend fun publishWeGlideUploadPromptIfEligible(
        sessionId: Long,
        finalizeResult: IgcFinalizeResult
    ) {
        val entry = when (finalizeResult) {
            is IgcFinalizeResult.Published -> finalizeResult.entry
            is IgcFinalizeResult.AlreadyPublished -> finalizeResult.entry
            is IgcFinalizeResult.Failure -> return
        }
        val prompt = evaluateWeGlidePostFlightPromptUseCase(
            WeGlideFinalizedFlightUploadRequest(
                localFlightId = sessionId.toString(),
                document = entry.document,
                scoringDate = entry.utcDate?.toString()
            )
        )
        if (prompt != null) {
            weGlidePostFlightPromptCoordinator.show(prompt)
            weGlidePostFlightPromptNotificationController.onPromptPublished(prompt)
            AppLogger.d(TAG, "WeGlide prompt published")
        }
    }
}

