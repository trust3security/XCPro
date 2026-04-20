package com.trust3.xcpro.vario

import com.trust3.xcpro.audio.AudioFocusManager
import com.trust3.xcpro.common.di.DefaultDispatcher
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.igc.IgcRecordingActionSink
import com.trust3.xcpro.igc.data.IgcFinalizeResult
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import com.trust3.xcpro.igc.usecase.IgcRecordingUseCase
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.hawk.HawkConfigRepository
import com.trust3.xcpro.hawk.HawkVarioRepository
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveRuntimeStartResult
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.SelectedLiveRuntimeBackendPort
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.weglide.domain.EvaluateWeGlidePostFlightPromptUseCase
import com.trust3.xcpro.weglide.domain.WeGlideFinalizedFlightUploadRequest
import com.trust3.xcpro.weglide.domain.WeGlidePostFlightPromptCoordinator
import com.trust3.xcpro.weglide.notifications.WeGlidePostFlightPromptNotificationController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Application-wide manager that keeps the sensor/vario pipeline alive even when no UI is visible.
 * Owned by VarioForegroundService and exposed to UI components via dependency injection.
 */
@Singleton
open class VarioServiceManager @Inject constructor(
    val audioFocusManager: AudioFocusManager,
    private val selectedLiveRuntimeBackendPort: SelectedLiveRuntimeBackendPort,
    private val liveSourceStatePort: LiveSourceStatePort,
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
        return when (val startResult = selectedLiveRuntimeBackendPort.start()) {
            LiveRuntimeStartResult.READY -> true
            LiveRuntimeStartResult.STARTING_MANAGER_RETRY -> {
                AppLogger.w(
                    TAG,
                    "Selected live runtime not ready yet; scheduling manager retry every ${SENSOR_RETRY_DELAY_MS} ms"
                )
                scheduleSensorRetry()
                false
            }

            LiveRuntimeStartResult.STARTING_EXTERNAL_RETRY -> {
                AppLogger.w(
                    TAG,
                    "Selected external runtime is connecting or degraded; waiting on external owner recovery"
                )
                false
            }
        }
    }

    open fun stop() {
        if (!running) return
        running = false
        cancelSensorRetry()
        selectedLiveRuntimeBackendPort.stop()
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
            val startResult = selectedLiveRuntimeBackendPort.start()
            val ready = startResult == LiveRuntimeStartResult.READY
            if (!ready && AppLogger.rateLimit(TAG, "sensor_retry_not_ready", SENSOR_RETRY_WARNING_INTERVAL_MS)) {
                AppLogger.w(
                    TAG,
                    "Retry selected live runtime not ready yet (result=$startResult)"
                )
            }
            ready
        }
    }

    private fun cancelSensorRetry() {
        sensorRetryCoordinator?.cancel()
    }

    private suspend fun applyGpsUpdateInterval(intervalMs: Long) {
        if (lastGpsIntervalMs == intervalMs) return
        val applied = runCatching {
            selectedLiveRuntimeBackendPort.setGpsUpdateIntervalMs(intervalMs)
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

    private suspend fun publishWeGlideUploadPromptIfEligible(
        sessionId: Long,
        finalizeResult: IgcFinalizeResult
    ) {
        if (liveSourceStatePort.state.value.kind == LiveSourceKind.SIMULATOR_CONDOR2) {
            AppLogger.d(TAG, "Skipping WeGlide prompt for simulator-finalized flight")
            return
        }
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

