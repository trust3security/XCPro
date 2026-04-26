package com.trust3.xcpro.service

import com.trust3.xcpro.livesource.EffectiveLiveSource
import com.trust3.xcpro.livesource.LiveRuntimeStartResult
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.livesource.SelectedLiveRuntimeBackendPort
import com.trust3.xcpro.simulator.condor.CondorRuntimeSessionPort
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.UnifiedSensorManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidSelectedLiveRuntimeBackend @Inject constructor(
    private val liveSourceStatePort: LiveSourceStatePort,
    private val unifiedSensorManager: UnifiedSensorManager,
    private val condorRuntimeSessionPort: CondorRuntimeSessionPort
) : SelectedLiveRuntimeBackendPort {

    override suspend fun start(): LiveRuntimeStartResult =
        when (liveSourceStatePort.state.value.effectiveSource) {
            EffectiveLiveSource.PHONE -> startPhoneRuntime()
            EffectiveLiveSource.CONDOR2 -> startCondorRuntime()
        }

    override fun stop() {
        when (liveSourceStatePort.state.value.effectiveSource) {
            EffectiveLiveSource.PHONE -> unifiedSensorManager.stopAllSensors()
            EffectiveLiveSource.CONDOR2 -> condorRuntimeSessionPort.requestDisconnect()
        }
    }

    override suspend fun setGpsUpdateIntervalMs(intervalMs: Long) {
        if (liveSourceStatePort.state.value.effectiveSource != EffectiveLiveSource.PHONE) {
            return
        }
        withContext(Dispatchers.Main.immediate) {
            unifiedSensorManager.setGpsUpdateIntervalMs(intervalMs)
        }
    }

    private suspend fun startPhoneRuntime(): LiveRuntimeStartResult {
        condorRuntimeSessionPort.requestDisconnect()
        val started = withContext(Dispatchers.Main.immediate) {
            unifiedSensorManager.startAllSensors()
        }
        val sensorStatus = unifiedSensorManager.getSensorStatus()
        return if (started && isPhoneRuntimeReady(sensorStatus)) {
            LiveRuntimeStartResult.READY
        } else {
            LiveRuntimeStartResult.STARTING_MANAGER_RETRY
        }
    }

    private suspend fun startCondorRuntime(): LiveRuntimeStartResult {
        withContext(Dispatchers.Main.immediate) {
            unifiedSensorManager.stopAllSensors()
        }
        return when (liveSourceStatePort.state.value.status) {
            LiveSourceStatus.CondorReady -> LiveRuntimeStartResult.READY
            is LiveSourceStatus.CondorDegraded -> {
                condorRuntimeSessionPort.requestConnect()
                LiveRuntimeStartResult.STARTING_EXTERNAL_RETRY
            }

            else -> {
                condorRuntimeSessionPort.requestConnect()
                LiveRuntimeStartResult.STARTING_EXTERNAL_RETRY
            }
        }
    }

    private fun isPhoneRuntimeReady(status: SensorStatus): Boolean {
        val gpsReady = status.gpsStarted
        val baroReady = !status.baroAvailable || status.baroStarted
        return gpsReady && baroReady
    }
}
