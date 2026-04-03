package com.example.xcpro.vario

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.audio.AudioFocusManager
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.igc.IgcRecordingActionSink
import com.example.xcpro.igc.data.IgcFinalizeResult
import com.example.xcpro.igc.data.IgcLogEntry
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import com.example.xcpro.igc.usecase.IgcRecordingUseCase
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.hawk.HawkConfigRepository
import com.example.xcpro.hawk.HawkVarioRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class VarioServiceManagerConstructionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `start and stop use injected repository`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val audioFocusManager = AudioFocusManager(context)
        val serviceDispatcher = UnconfinedTestDispatcher(testScheduler)
        val unifiedSensorManager = mock<UnifiedSensorManager>()
        whenever(unifiedSensorManager.startAllSensors()).thenReturn(true)
        whenever(unifiedSensorManager.getSensorStatus()).thenReturn(
            SensorStatus(
                gpsAvailable = true,
                gpsStarted = true,
                baroAvailable = true,
                baroStarted = true,
                compassAvailable = true,
                compassStarted = true,
                accelAvailable = true,
                accelStarted = true,
                rotationAvailable = true,
                rotationStarted = true,
                hasLocationPermissions = true
            )
        )

        val flightDataRepository = FlightDataRepository()
        val levoRepo = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepo.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val hawkConfigRepository = HawkConfigRepository()
        val hawkVarioRepository = mock<HawkVarioRepository>()

        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }

        val fakeRepository = FakeSensorFusionRepository()
        val manager = VarioServiceManager(
            audioFocusManager = audioFocusManager,
            unifiedSensorManager = unifiedSensorManager,
            sensorFusionRepository = fakeRepository,
            flightDataRepository = flightDataRepository,
            levoVarioPreferencesRepository = levoRepo,
            hawkConfigRepository = hawkConfigRepository,
            hawkVarioRepository = hawkVarioRepository,
            flightStateSource = flightStateSource,
            defaultDispatcher = serviceDispatcher
        )

        val started = manager.start()
        assertTrue(started)

        manager.stop()

        verify(unifiedSensorManager).startAllSensors()
        verify(hawkVarioRepository).start()
        verify(hawkVarioRepository).stop()
        assertEquals(1, fakeRepository.stopCalls)
    }

    @Test
    fun `start returns false when barometer is available but not started`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val audioFocusManager = AudioFocusManager(context)
        val serviceDispatcher = UnconfinedTestDispatcher(testScheduler)
        val unifiedSensorManager = mock<UnifiedSensorManager>()
        whenever(unifiedSensorManager.startAllSensors()).thenReturn(true)
        whenever(unifiedSensorManager.getSensorStatus()).thenReturn(
            SensorStatus(
                gpsAvailable = true,
                gpsStarted = true,
                baroAvailable = true,
                baroStarted = false,
                compassAvailable = true,
                compassStarted = true,
                accelAvailable = true,
                accelStarted = true,
                rotationAvailable = true,
                rotationStarted = true,
                hasLocationPermissions = true
            )
        )

        val flightDataRepository = FlightDataRepository()
        val levoRepo = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepo.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val hawkConfigRepository = HawkConfigRepository()
        val hawkVarioRepository = mock<HawkVarioRepository>()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }
        val fakeRepository = FakeSensorFusionRepository()

        val manager = VarioServiceManager(
            audioFocusManager = audioFocusManager,
            unifiedSensorManager = unifiedSensorManager,
            sensorFusionRepository = fakeRepository,
            flightDataRepository = flightDataRepository,
            levoVarioPreferencesRepository = levoRepo,
            hawkConfigRepository = hawkConfigRepository,
            hawkVarioRepository = hawkVarioRepository,
            flightStateSource = flightStateSource,
            defaultDispatcher = serviceDispatcher
        )

        val started = manager.start()
        assertEquals(false, started)

        manager.stop()
    }

    @Test
    fun `igc session actions are dispatched to action sink`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val audioFocusManager = AudioFocusManager(context)
        val serviceDispatcher = UnconfinedTestDispatcher(testScheduler)
        val unifiedSensorManager = mock<UnifiedSensorManager>()
        whenever(unifiedSensorManager.startAllSensors()).thenReturn(true)
        whenever(unifiedSensorManager.getSensorStatus()).thenReturn(
            SensorStatus(
                gpsAvailable = true,
                gpsStarted = true,
                baroAvailable = true,
                baroStarted = true,
                compassAvailable = true,
                compassStarted = true,
                accelAvailable = true,
                accelStarted = true,
                rotationAvailable = true,
                rotationStarted = true,
                hasLocationPermissions = true
            )
        )

        val flightDataRepository = FlightDataRepository()
        val levoRepo = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepo.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val hawkConfigRepository = HawkConfigRepository()
        val hawkVarioRepository = mock<HawkVarioRepository>()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }

        val actionFlow = MutableSharedFlow<IgcSessionStateMachine.Action>(extraBufferCapacity = 16)
        val recordingUseCase = mock<IgcRecordingUseCase>()
        whenever(recordingUseCase.actions).thenReturn(actionFlow.asSharedFlow())
        val actionSink = mock<IgcRecordingActionSink>()
        whenever(actionSink.onFinalizeRecording(11L, 5_000L)).thenReturn(successfulFinalizeResult())

        val manager = VarioServiceManager(
            audioFocusManager = audioFocusManager,
            unifiedSensorManager = unifiedSensorManager,
            sensorFusionRepository = FakeSensorFusionRepository(),
            flightDataRepository = flightDataRepository,
            levoVarioPreferencesRepository = levoRepo,
            hawkConfigRepository = hawkConfigRepository,
            hawkVarioRepository = hawkVarioRepository,
            flightStateSource = flightStateSource,
            defaultDispatcher = serviceDispatcher,
            igcRecordingUseCase = recordingUseCase,
            igcRecordingActionSink = actionSink
        )

        val started = manager.start()
        assertTrue(started)

        withTimeout(1_000L) {
            actionFlow.subscriptionCount.filter { it > 0 }.first()
        }

        actionFlow.emit(IgcSessionStateMachine.Action.EnterArmed(1_000L))
        actionFlow.emit(
            IgcSessionStateMachine.Action.StartRecording(
                sessionId = 11L,
                preFlightGroundWindowMs = 20_000L
            )
        )
        actionFlow.emit(
            IgcSessionStateMachine.Action.FinalizeRecording(
                sessionId = 11L,
                postFlightGroundWindowMs = 5_000L
            )
        )
        actionFlow.emit(IgcSessionStateMachine.Action.MarkCompleted(11L))
        actionFlow.emit(IgcSessionStateMachine.Action.MarkFailed(11L, "manual-test"))

        advanceUntilIdle()

        verify(actionSink, timeout(1_000L)).onSessionArmed(1_000L)
        verify(actionSink, timeout(1_000L)).onStartRecording(11L, 20_000L)
        verify(actionSink, timeout(1_000L)).onFinalizeRecording(11L, 5_000L)
        verify(recordingUseCase, timeout(1_000L)).onFinalizeSucceeded()
        verify(recordingUseCase, never()).onFinalizeFailed(org.mockito.kotlin.any())
        verify(actionSink, timeout(1_000L)).onMarkCompleted(11L)
        verify(actionSink, timeout(1_000L)).onMarkFailed(11L, "manual-test")

        manager.stop()
    }

    @Test
    fun `igc finalize failure is routed back to recording use case`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val audioFocusManager = AudioFocusManager(context)
        val serviceDispatcher = UnconfinedTestDispatcher(testScheduler)
        val unifiedSensorManager = mock<UnifiedSensorManager>()
        whenever(unifiedSensorManager.startAllSensors()).thenReturn(true)
        whenever(unifiedSensorManager.getSensorStatus()).thenReturn(
            SensorStatus(
                gpsAvailable = true,
                gpsStarted = true,
                baroAvailable = true,
                baroStarted = true,
                compassAvailable = true,
                compassStarted = true,
                accelAvailable = true,
                accelStarted = true,
                rotationAvailable = true,
                rotationStarted = true,
                hasLocationPermissions = true
            )
        )

        val flightDataRepository = FlightDataRepository()
        val levoRepo = mock<LevoVarioPreferencesRepository>()
        whenever(levoRepo.config).thenReturn(MutableStateFlow(LevoVarioConfig()))
        val hawkConfigRepository = HawkConfigRepository()
        val hawkVarioRepository = mock<HawkVarioRepository>()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState())
        }

        val actionFlow = MutableSharedFlow<IgcSessionStateMachine.Action>(extraBufferCapacity = 16)
        val recordingUseCase = mock<IgcRecordingUseCase>()
        whenever(recordingUseCase.actions).thenReturn(actionFlow.asSharedFlow())
        val actionSink = mock<IgcRecordingActionSink>()
        whenever(actionSink.onFinalizeRecording(44L, 5_000L)).thenReturn(
            IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                message = "write failed"
            )
        )

        val manager = VarioServiceManager(
            audioFocusManager = audioFocusManager,
            unifiedSensorManager = unifiedSensorManager,
            sensorFusionRepository = FakeSensorFusionRepository(),
            flightDataRepository = flightDataRepository,
            levoVarioPreferencesRepository = levoRepo,
            hawkConfigRepository = hawkConfigRepository,
            hawkVarioRepository = hawkVarioRepository,
            flightStateSource = flightStateSource,
            defaultDispatcher = serviceDispatcher,
            igcRecordingUseCase = recordingUseCase,
            igcRecordingActionSink = actionSink
        )

        assertTrue(manager.start())
        withTimeout(1_000L) {
            actionFlow.subscriptionCount.filter { it > 0 }.first()
        }
        actionFlow.emit(
            IgcSessionStateMachine.Action.FinalizeRecording(
                sessionId = 44L,
                postFlightGroundWindowMs = 5_000L
            )
        )
        advanceUntilIdle()

        verify(recordingUseCase, timeout(1_000L)).onFinalizeFailed(org.mockito.kotlin.any())
        verify(recordingUseCase, never()).onFinalizeSucceeded()
        manager.stop()
    }

    private class FakeSensorFusionRepository : SensorFusionRepository {
        override val flightDataFlow = MutableStateFlow<CompleteFlightData?>(null)
        override val diagnosticsFlow = MutableStateFlow<VarioDiagnosticsSample?>(null)
        override val audioSettings = MutableStateFlow(VarioAudioSettings())

        var stopCalls = 0
        var lastTeCompensationEnabled: Boolean? = null

        override fun updateAudioSettings(settings: VarioAudioSettings) = Unit
        override fun setHawkAudioEnabled(enabled: Boolean) = Unit
        override fun setManualQnh(qnhHPa: Double) = Unit
        override fun resetQnhToStandard() = Unit
        override fun setMacCreadySetting(value: Double) = Unit
        override fun setMacCreadyRisk(value: Double) = Unit
        override fun setAutoMcEnabled(enabled: Boolean) = Unit
        override fun setTotalEnergyCompensationEnabled(enabled: Boolean) {
            lastTeCompensationEnabled = enabled
        }
        override fun setFlightMode(mode: FlightMode) = Unit
        override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) = Unit

        override fun stop() {
            stopCalls += 1
        }
    }

    private fun successfulFinalizeResult(): IgcFinalizeResult {
        return IgcFinalizeResult.Published(
            entry = IgcLogEntry(
                document = com.example.xcpro.common.documents.DocumentRef(
                    uri = "content://downloads/public_downloads/11",
                    displayName = "2026-03-09-XCP-000011-01.IGC"
                ),
                displayName = "2026-03-09-XCP-000011-01.IGC",
                sizeBytes = 128L,
                lastModifiedEpochMillis = 0L,
                utcDate = java.time.LocalDate.of(2026, 3, 9),
                durationSeconds = null
            ),
            fileName = "2026-03-09-XCP-000011-01.IGC"
        )
    }
}
