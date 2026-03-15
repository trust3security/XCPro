package com.example.xcpro.hawk

import com.example.xcpro.common.di.DefaultDispatcher
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.variometer.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@Singleton
class HawkVarioRepository @Inject constructor(
    private val sensorStreamPort: HawkSensorStreamPort,
    private val activeSourcePort: HawkActiveSourcePort,
    private val configRepository: HawkConfigRepository,
    private val engine: HawkVarioEngine,
    private val clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "HawkVarioRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _output = MutableStateFlow<HawkOutput?>(null)
    val output: StateFlow<HawkOutput?> = _output.asStateFlow()

    private var job: Job? = null
    private var lastLogMonoMs: Long = 0L

    fun start() {
        if (job != null) return
        job = scope.launch {
            configRepository.config
                .combine(activeSourcePort.activeSource) { config, source ->
                    config to source
                }
                .collectLatest { (config, source) ->
                    engine.reset()
                    _output.value = null
                    if (!config.enabled || source == HawkRuntimeSource.REPLAY) {
                        return@collectLatest
                    }

                    val baroEvents = sensorStreamPort.baroSamples
                        .map { HawkSensorEvent.Baro(it) }
                    val accelEvents = sensorStreamPort.accelSamples
                        .map { HawkSensorEvent.Accel(it) }

                    merge(baroEvents, accelEvents).collect { event ->
                        when (event) {
                            is HawkSensorEvent.Accel -> engine.updateAccel(event.data, config)
                            is HawkSensorEvent.Baro -> {
                                val output = engine.updateBaro(event.data, config) ?: return@collect
                                _output.value = output
                                maybeLog(output, config)
                            }
                        }
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        engine.reset()
        _output.value = null
    }

    private fun maybeLog(output: HawkOutput, config: HawkConfig) {
        if (!BuildConfig.DEBUG || !config.debugLogging) return
        val now = clock.nowMonoMs()
        if (now - lastLogMonoMs < config.debugLogIntervalMs) return
        lastLogMonoMs = now

        val vRaw = output.vRawMps?.let { String.format("%.2f", it) } ?: "NA"
        val vAudio = output.vAudioMps?.let { String.format("%.2f", it) } ?: "NA"
        val variance = output.accelVariance?.let { String.format("%.3f", it) } ?: "NA"
        val innovation = output.baroInnovationMps?.let { String.format("%.2f", it) } ?: "NA"
        val hz = output.baroHz?.let { String.format("%.1f", it) } ?: "NA"
        AppLogger.d(
            TAG,
            "hawk vRaw=$vRaw vAudio=$vAudio accelVar=$variance innov=$innovation baroHz=$hz " +
                "accepted=${output.baroSampleAccepted} rejRate=${String.format("%.2f", output.baroRejectionRate)}"
        )
    }

    private sealed class HawkSensorEvent {
        data class Baro(val data: HawkBaroSample) : HawkSensorEvent()
        data class Accel(val data: HawkAccelSample) : HawkSensorEvent()
    }
}
