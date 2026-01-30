package com.example.xcpro.replay

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.Clock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorFusionRepositoryFactory
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ReplayPipeline(
    private val flightDataRepository: FlightDataRepository,
    private val varioServiceManager: VarioServiceManager,
    private val windRepository: WindSensorFusionRepository,
    private val replaySensorSource: ReplaySensorSource,
    private val sensorFusionRepositoryFactory: SensorFusionRepositoryFactory,
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
    private val sessionState: StateFlow<SessionState>,
    private val tag: String
) {

    var scope: CoroutineScope = createScope()
        private set
    var replayFusionRepository: SensorFusionRepository? = null
        private set

    private var forwardJob: Job? = null
    private var audioSettingsJob: Job? = null
    private var lastForwardLogTime = 0L
    private var sensorsSuspended = false
    private var latestAudioSettings: VarioAudioSettings = VarioAudioSettings()

    fun ensureScope(onScopeReset: () -> Unit) {
        if (scope.isActive) return
        AppLogger.w(tag, "REPLY_SCOPE inactive; rebuilding replay scope")
        scope = createScope()
        forwardJob = null
        replayFusionRepository = null
        audioSettingsJob = null
        onScopeReset()
    }

    fun ensureActive(onScopeReset: () -> Unit) {
        ensureScope(onScopeReset)
        if (replayFusionRepository == null) {
            replayFusionRepository = createFusionRepository()
            replayFusionRepository?.updateAudioSettings(latestAudioSettings)
        }
        ensureAudioSettingsObserver()
        if (forwardJob?.isActive != true) {
            startForwardingFlightData()
        }
    }

    fun suspendSensors() {
        if (!sensorsSuspended) {
            sensorsSuspended = true
            varioServiceManager.stop()
        }
    }

    suspend fun resumeSensors() {
        if (sensorsSuspended) {
            sensorsSuspended = false
            varioServiceManager.start()
        }
    }

    fun resetReplayFusion() {
        replayFusionRepository = null
    }

    private fun createScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    private fun createFusionRepository(): SensorFusionRepository =
        sensorFusionRepositoryFactory.create(
            sensorDataSource = replaySensorSource,
            scope = scope,
            enableAudio = true,
            isReplayMode = true
        )

    private fun ensureAudioSettingsObserver() {
        if (audioSettingsJob?.isActive == true) return
        audioSettingsJob = scope.launch {
            levoVarioPreferencesRepository.config.collect { config ->
                latestAudioSettings = config.audioSettings
                replayFusionRepository?.updateAudioSettings(config.audioSettings)
            }
        }
    }

    private fun startForwardingFlightData() {
        val repo = replayFusionRepository ?: return
        forwardJob?.cancel()
        forwardJob = scope.launch {
            repo.flightDataFlow.collect { data ->
                if (sessionState.value.status == SessionStatus.PLAYING) {
                    val now = clock.nowMonoMs()
                    if (now - lastForwardLogTime >= 1_000L) {
                        lastForwardLogTime = now
                        val windState = windRepository.windState.value
                        val windSpeed = windState.vector?.speed
                        val windQuality = windState.quality
                        val gps = data?.gps
                        val verticalSpeed = data?.verticalSpeed?.value
                        val displayVario = data?.displayVario?.value
                        val baselineDisplayVario = data?.baselineDisplayVario?.value
                        val tc30 = data?.thermalAverage?.value
                        val tcAvg = data?.thermalAverageCircle?.value
                        val tAvg = data?.thermalAverageTotal?.value
                        AppLogger.d(
                            tag,
                            "REPLAY_FORWARD gps=${gps?.position?.latitude},${gps?.position?.longitude} " +
                                "gs=${gps?.speed?.value} alt=${gps?.altitude?.value} " +
                                "v=${verticalSpeed} dv=${displayVario} base=${baselineDisplayVario} " +
                                "valid=${data?.varioValid} src=${data?.varioSource} te=${data?.teAltitude?.value} " +
                                "tc30=${tc30} tcAvg=${tcAvg} tAvg=${tAvg} tValid=${data?.currentThermalValid} " +
                                "circling=${data?.isCircling} windQ=${windQuality} wind=${windSpeed}"
                        )
                    }
                    flightDataRepository.update(data, FlightDataRepository.Source.REPLAY)
                }
            }
        }
    }
}
