package com.example.xcpro.replay

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorFusionRepositoryFactory
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.vario.LevoVarioConfig
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayPipelineOwnershipTest {

    @Test
    fun ensureActive_reuses_active_runtime_handle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val replayRepo = FakeSensorFusionRepository()
        val sensorFusionFactory = mock<SensorFusionRepositoryFactory>()
        whenever(sensorFusionFactory.create(any(), any(), any(), any())).thenReturn(replayRepo)

        val pipeline = buildPipeline(
            dispatcher = dispatcher,
            sensorFusionRepositoryFactory = sensorFusionFactory
        )

        val runtime = pipeline.createRuntime()
        val activeRuntime = pipeline.ensureActive(runtime) { error("scope should not reset") }
        val reusedRuntime = pipeline.ensureActive(activeRuntime) { error("scope should not reset") }
        advanceUntilIdle()

        assertSame(activeRuntime, reusedRuntime)
        assertSame(activeRuntime.scope, reusedRuntime.scope)
        assertSame(activeRuntime.replayFusionRepository, reusedRuntime.replayFusionRepository)
        verify(sensorFusionFactory, times(1)).create(any(), any(), any(), any())
        activeRuntime.scope.cancel()
    }

    @Test
    fun ensureActive_rebuilds_inactive_runtime_and_invokes_reset_callback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstRepo = FakeSensorFusionRepository()
        val secondRepo = FakeSensorFusionRepository()
        val sensorFusionFactory = mock<SensorFusionRepositoryFactory>()
        whenever(sensorFusionFactory.create(any(), any(), any(), any())).thenReturn(firstRepo, secondRepo)

        val pipeline = buildPipeline(
            dispatcher = dispatcher,
            sensorFusionRepositoryFactory = sensorFusionFactory
        )

        val initialRuntime = pipeline.ensureActive(pipeline.createRuntime()) { error("scope should not reset") }
        initialRuntime.scope.cancel()
        advanceUntilIdle()

        var resets = 0
        val rebuiltRuntime = pipeline.ensureActive(initialRuntime) { resets++ }
        advanceUntilIdle()

        assertEquals(1, resets)
        assertNotSame(initialRuntime, rebuiltRuntime)
        assertNotSame(initialRuntime.scope, rebuiltRuntime.scope)
        assertSame(secondRepo, rebuiltRuntime.replayFusionRepository)
        verify(sensorFusionFactory, times(2)).create(any(), any(), any(), any())
        rebuiltRuntime.scope.cancel()
    }

    private fun buildPipeline(
        dispatcher: CoroutineDispatcher,
        sensorFusionRepositoryFactory: SensorFusionRepositoryFactory
    ): ReplayPipeline {
        val levoRepository = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepository.config).thenReturn(MutableStateFlow(LevoVarioConfig()))

        val windRepository = mock<WindSensorFusionRepository>()
        whenever(windRepository.windState).thenReturn(MutableStateFlow(WindState()))

        return ReplayPipeline(
            flightDataRepository = FlightDataRepository(),
            varioServiceManager = mock<VarioServiceManager>(),
            windRepository = windRepository,
            replaySensorSource = ReplaySensorSource(),
            sensorFusionRepositoryFactory = sensorFusionRepositoryFactory,
            levoVarioPreferencesRepository = levoRepository,
            clock = FakeClock(),
            dispatcher = dispatcher,
            sessionState = MutableStateFlow(SessionState()),
            tag = "ReplayPipelineOwnershipTest"
        )
    }

    private class FakeSensorFusionRepository : SensorFusionRepository {
        override val flightDataFlow: StateFlow<CompleteFlightData?> = MutableStateFlow(null)
        override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = MutableStateFlow(null)
        override val audioSettings: StateFlow<VarioAudioSettings> = MutableStateFlow(VarioAudioSettings())

        override fun updateAudioSettings(settings: VarioAudioSettings) = Unit
        override fun setHawkAudioEnabled(enabled: Boolean) = Unit
        override fun setManualQnh(qnhHPa: Double) = Unit
        override fun resetQnhToStandard() = Unit
        override fun setMacCreadySetting(value: Double) = Unit
        override fun setMacCreadyRisk(value: Double) = Unit
        override fun setAutoMcEnabled(enabled: Boolean) = Unit
        override fun setTotalEnergyCompensationEnabled(enabled: Boolean) = Unit
        override fun setFlightMode(mode: FlightMode) = Unit
        override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) = Unit
        override fun stop() = Unit
    }
}
