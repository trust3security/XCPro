package com.trust3.xcpro.service

import com.trust3.xcpro.livesource.EffectiveLiveSource
import com.trust3.xcpro.livesource.LiveRuntimeStartResult
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import com.trust3.xcpro.simulator.CondorLiveDegradedReason
import com.trust3.xcpro.simulator.condor.CondorRuntimeSessionPort
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.UnifiedSensorManager
import com.trust3.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSelectedLiveRuntimeBackendTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun start_phoneSource_disconnectsCondor_and_startsSensors_when_ready() = runTest {
        val sensorManager = mock<UnifiedSensorManager>()
        whenever(sensorManager.startAllSensors()).thenReturn(true)
        whenever(sensorManager.getSensorStatus()).thenReturn(readyPhoneStatus())
        val condorRuntimeSessionPort = mock<CondorRuntimeSessionPort>()
        val backend = AndroidSelectedLiveRuntimeBackend(
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.PHONE,
                    status = LiveSourceStatus.PhoneReady,
                    kind = LiveSourceKind.PHONE
                )
            ),
            unifiedSensorManager = sensorManager,
            condorRuntimeSessionPort = condorRuntimeSessionPort
        )

        val startResult = backend.start()

        assertEquals(LiveRuntimeStartResult.READY, startResult)
        verify(condorRuntimeSessionPort).requestDisconnect()
        verify(sensorManager).startAllSensors()
        verify(sensorManager).getSensorStatus()
        verify(sensorManager, never()).stopAllSensors()
    }

    @Test
    fun start_phoneSource_returns_manager_retry_when_sensor_pipeline_is_not_ready() = runTest {
        val sensorManager = mock<UnifiedSensorManager>()
        whenever(sensorManager.startAllSensors()).thenReturn(true)
        whenever(sensorManager.getSensorStatus()).thenReturn(
            readyPhoneStatus().copy(gpsStarted = false)
        )
        val condorRuntimeSessionPort = mock<CondorRuntimeSessionPort>()
        val backend = AndroidSelectedLiveRuntimeBackend(
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.PHONE,
                    status = LiveSourceStatus.PhoneReady,
                    kind = LiveSourceKind.PHONE
                )
            ),
            unifiedSensorManager = sensorManager,
            condorRuntimeSessionPort = condorRuntimeSessionPort
        )

        val startResult = backend.start()

        assertEquals(LiveRuntimeStartResult.STARTING_MANAGER_RETRY, startResult)
        verify(condorRuntimeSessionPort).requestDisconnect()
        verify(sensorManager).startAllSensors()
        verify(sensorManager).getSensorStatus()
    }

    @Test
    fun start_condorSource_stops_phone_sensors_and_requests_connect_when_degraded() = runTest {
        val sensorManager = mock<UnifiedSensorManager>()
        val condorRuntimeSessionPort = mock<CondorRuntimeSessionPort>()
        val backend = AndroidSelectedLiveRuntimeBackend(
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.CONDOR2,
                    status = LiveSourceStatus.CondorDegraded(
                        CondorLiveDegradedReason.DISCONNECTED
                    ),
                    kind = LiveSourceKind.SIMULATOR_CONDOR2
                )
            ),
            unifiedSensorManager = sensorManager,
            condorRuntimeSessionPort = condorRuntimeSessionPort
        )

        val startResult = backend.start()

        assertEquals(LiveRuntimeStartResult.STARTING_EXTERNAL_RETRY, startResult)
        verify(sensorManager).stopAllSensors()
        verify(condorRuntimeSessionPort).requestConnect()
        verify(sensorManager, never()).startAllSensors()
    }

    @Test
    fun start_condorSource_returns_ready_without_connect_when_condor_is_ready() = runTest {
        val sensorManager = mock<UnifiedSensorManager>()
        val condorRuntimeSessionPort = mock<CondorRuntimeSessionPort>()
        val backend = AndroidSelectedLiveRuntimeBackend(
            liveSourceStatePort = fixedLiveSourceStatePort(
                ResolvedLiveSourceState(
                    effectiveSource = EffectiveLiveSource.CONDOR2,
                    status = LiveSourceStatus.CondorReady,
                    kind = LiveSourceKind.SIMULATOR_CONDOR2
                )
            ),
            unifiedSensorManager = sensorManager,
            condorRuntimeSessionPort = condorRuntimeSessionPort
        )

        val startResult = backend.start()

        assertEquals(LiveRuntimeStartResult.READY, startResult)
        verify(sensorManager).stopAllSensors()
        verify(condorRuntimeSessionPort, never()).requestConnect()
        verify(sensorManager, never()).startAllSensors()
    }

    private fun fixedLiveSourceStatePort(
        state: ResolvedLiveSourceState
    ): LiveSourceStatePort = object : LiveSourceStatePort {
        private val mutableState = MutableStateFlow(state)

        override val state: StateFlow<ResolvedLiveSourceState> = mutableState.asStateFlow()

        override fun refreshAndGetState(): ResolvedLiveSourceState = mutableState.value
    }

    private fun readyPhoneStatus(): SensorStatus = SensorStatus(
        gpsAvailable = true,
        gpsStarted = true,
        baroAvailable = true,
        baroStarted = true,
        compassAvailable = false,
        compassStarted = false,
        accelAvailable = false,
        accelStarted = false,
        rotationAvailable = false,
        rotationStarted = false,
        hasLocationPermissions = true
    )
}
