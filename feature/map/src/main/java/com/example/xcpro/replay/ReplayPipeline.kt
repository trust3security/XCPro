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

internal class ReplayPipelineRuntime(
    val scope: CoroutineScope,
    var replayFusionRepository: SensorFusionRepository? = null,
    var forwardJob: Job? = null,
    var audioSettingsJob: Job? = null,
    var lastForwardLogTime: Long = 0L,
    var latestAudioSettings: VarioAudioSettings = VarioAudioSettings(),
    var latestTeCompensationEnabled: Boolean = true
)

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
    private var sensorsSuspended = false

    internal fun createRuntime(): ReplayPipelineRuntime = createRuntime(
        latestAudioSettings = VarioAudioSettings(),
        latestTeCompensationEnabled = true
    )

    internal fun ensureScope(
        runtime: ReplayPipelineRuntime,
        onScopeReset: () -> Unit
    ): ReplayPipelineRuntime {
        if (runtime.scope.isActive) return runtime
        AppLogger.w(tag, "REPLY_SCOPE inactive; rebuilding replay scope")
        onScopeReset()
        return createRuntime(
            latestAudioSettings = runtime.latestAudioSettings,
            latestTeCompensationEnabled = runtime.latestTeCompensationEnabled
        )
    }

    internal fun ensureActive(
        runtime: ReplayPipelineRuntime,
        onScopeReset: () -> Unit
    ): ReplayPipelineRuntime {
        val activeRuntime = ensureScope(runtime, onScopeReset)
        if (activeRuntime.replayFusionRepository == null) {
            activeRuntime.replayFusionRepository = createFusionRepository(activeRuntime.scope)
            activeRuntime.replayFusionRepository?.updateAudioSettings(activeRuntime.latestAudioSettings)
            activeRuntime.replayFusionRepository
                ?.setTotalEnergyCompensationEnabled(activeRuntime.latestTeCompensationEnabled)
        }
        ensureAudioSettingsObserver(activeRuntime)
        if (activeRuntime.forwardJob?.isActive != true) {
            startForwardingFlightData(activeRuntime)
        }
        return activeRuntime
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

    internal fun resetReplayFusion(runtime: ReplayPipelineRuntime) {
        runtime.replayFusionRepository = null
    }

    private fun createScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    private fun createRuntime(
        latestAudioSettings: VarioAudioSettings,
        latestTeCompensationEnabled: Boolean
    ): ReplayPipelineRuntime =
        ReplayPipelineRuntime(
            scope = createScope(),
            latestAudioSettings = latestAudioSettings,
            latestTeCompensationEnabled = latestTeCompensationEnabled
        )

    private fun createFusionRepository(scope: CoroutineScope): SensorFusionRepository =
        sensorFusionRepositoryFactory.create(
            sensorDataSource = replaySensorSource,
            scope = scope,
            enableAudio = true,
            isReplayMode = true
        )

    private fun ensureAudioSettingsObserver(runtime: ReplayPipelineRuntime) {
        if (runtime.audioSettingsJob?.isActive == true) return
        runtime.audioSettingsJob = runtime.scope.launch {
            levoVarioPreferencesRepository.config.collect { config ->
                runtime.latestAudioSettings = config.audioSettings
                runtime.latestTeCompensationEnabled = config.teCompensationEnabled
                runtime.replayFusionRepository?.updateAudioSettings(config.audioSettings)
                runtime.replayFusionRepository?.setTotalEnergyCompensationEnabled(config.teCompensationEnabled)
            }
        }
    }

    private fun startForwardingFlightData(runtime: ReplayPipelineRuntime) {
        val repo = runtime.replayFusionRepository ?: return
        runtime.forwardJob?.cancel()
        runtime.forwardJob = runtime.scope.launch {
            repo.flightDataFlow.collect { data ->
                if (sessionState.value.status == SessionStatus.PLAYING) {
                    val now = clock.nowMonoMs()
                    if (now - runtime.lastForwardLogTime >= 1_000L) {
                        runtime.lastForwardLogTime = now
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
