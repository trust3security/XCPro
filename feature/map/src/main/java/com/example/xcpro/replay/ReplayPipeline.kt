package com.example.xcpro.replay

import android.content.Context
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.FlightDataCalculator
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ReplayPipeline(
    private val appContext: Context,
    private val flightDataRepository: FlightDataRepository,
    private val varioServiceManager: VarioServiceManager,
    private val sinkProvider: StillAirSinkProvider,
    private val windRepository: WindSensorFusionRepository,
    private val flightStateSource: FlightStateSource,
    private val replaySensorSource: ReplaySensorSource,
    private val dispatcher: CoroutineDispatcher,
    private val sessionState: StateFlow<SessionState>,
    private val tag: String
) {

    var scope: CoroutineScope = createScope()
        private set
    var replayFusionRepository: SensorFusionRepository? = null
        private set

    private var forwardJob: Job? = null
    private var lastForwardLogTime = 0L
    private var sensorsSuspended = false

    fun ensureScope(onScopeReset: () -> Unit) {
        if (scope.isActive) return
        AppLogger.w(tag, "REPLY_SCOPE inactive; rebuilding replay scope")
        scope = createScope()
        forwardJob = null
        replayFusionRepository = null
        onScopeReset()
    }

    fun ensureActive(onScopeReset: () -> Unit) {
        ensureScope(onScopeReset)
        if (replayFusionRepository == null) {
            replayFusionRepository = createFusionRepository()
        }
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
        FlightDataCalculator(
            context = appContext,
            sensorDataSource = replaySensorSource,
            scope = scope,
            sinkProvider = sinkProvider,
            windStateFlow = windRepository.windState,
            flightStateSource = flightStateSource,
            enableAudio = true,
            isReplayMode = true
        )

    private fun startForwardingFlightData() {
        val repo = replayFusionRepository ?: return
        forwardJob?.cancel()
        forwardJob = scope.launch {
            repo.flightDataFlow.collect { data ->
                if (sessionState.value.status == SessionStatus.PLAYING) {
                    val now = System.currentTimeMillis()
                    if (now - lastForwardLogTime >= 1_000L) {
                        lastForwardLogTime = now
                        val windState = windRepository.windState.value
                        val windSpeed = windState.vector?.speed
                        val windQuality = windState.quality
                        val gps = data?.gps
                        val verticalSpeed = data?.verticalSpeed?.value
                        val displayVario = data?.displayVario?.value
                        val xcSoarDisplayVario = data?.xcSoarDisplayVario?.value
                        val tc30 = data?.thermalAverage?.value
                        val tcAvg = data?.thermalAverageCircle?.value
                        val tAvg = data?.thermalAverageTotal?.value
                        AppLogger.d(
                            tag,
                            "REPLAY_FORWARD gps=${gps?.position?.latitude},${gps?.position?.longitude} " +
                                "gs=${gps?.speed?.value} alt=${gps?.altitude?.value} " +
                                "v=${verticalSpeed} dv=${displayVario} xc=${xcSoarDisplayVario} " +
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
