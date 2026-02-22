package com.example.xcpro.vario

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.audio.AudioFocusManager
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
import com.example.xcpro.audio.VarioAudioSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
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
            flightStateSource = flightStateSource
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
            flightStateSource = flightStateSource
        )

        val started = manager.start()
        assertEquals(false, started)

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
}
