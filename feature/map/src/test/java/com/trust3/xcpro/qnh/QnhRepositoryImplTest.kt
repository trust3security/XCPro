package com.trust3.xcpro.qnh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.audio.VarioAudioSettings
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.map.QnhPreferencesRepository
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.VarioDiagnosticsSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class QnhRepositoryImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearStore() = runBlocking(Dispatchers.IO) {
        QnhPreferencesRepository(context).clearManualQnh()
    }

    @Test
    fun external_override_applies_live_without_persisting_and_restores_base_state() = runTest {
        val preferencesRepository = QnhPreferencesRepository(context)
        val sensorFusionRepository = FakeSensorFusionRepository()
        val externalSnapshotFlow = MutableStateFlow(ExternalFlightSettingsSnapshot())
        val repository = QnhRepositoryImpl(
            qnhPreferencesRepository = preferencesRepository,
            sensorFusionRepository = sensorFusionRepository,
            externalFlightSettingsReadPort = object : ExternalFlightSettingsReadPort {
                override val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot> =
                    externalSnapshotFlow
            },
            scope = backgroundScope,
            clock = FakeClock(wallMs = 12_345L)
        )

        runCurrent()

        repository.setManualQnh(1009.2)
        runCurrent()

        assertEquals(1009.2, repository.qnhState.value.hpa, 0.0)
        assertEquals(QnhSource.MANUAL, repository.qnhState.value.source)

        externalSnapshotFlow.value = ExternalFlightSettingsSnapshot(qnhHpa = 1007.4)
        runCurrent()

        assertEquals(1007.4, repository.qnhState.value.hpa, 0.0)
        assertEquals(QnhSource.EXTERNAL, repository.qnhState.value.source)
        assertEquals(1009.2, preferencesRepository.readActiveManualQnh()!!.qnhHpa, 0.0)

        externalSnapshotFlow.value = ExternalFlightSettingsSnapshot()
        runCurrent()

        assertEquals(1009.2, repository.qnhState.value.hpa, 0.0)
        assertEquals(QnhSource.MANUAL, repository.qnhState.value.source)
        assertEquals(listOf(1009.2, 1007.4, 1009.2), sensorFusionRepository.manualQnhUpdates)
        assertEquals(2, sensorFusionRepository.resetToStandardCalls)
    }

    private class FakeSensorFusionRepository : SensorFusionRepository {
        override val flightDataFlow: StateFlow<CompleteFlightData?> = MutableStateFlow(null)
        override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = MutableStateFlow(null)
        override val audioSettings: StateFlow<VarioAudioSettings> = MutableStateFlow(VarioAudioSettings())

        val manualQnhUpdates = mutableListOf<Double>()
        var resetToStandardCalls: Int = 0

        override fun updateAudioSettings(settings: VarioAudioSettings) = Unit

        override fun setHawkAudioEnabled(enabled: Boolean) = Unit

        override fun setManualQnh(qnhHPa: Double) {
            manualQnhUpdates += qnhHPa
        }

        override fun resetQnhToStandard() {
            resetToStandardCalls += 1
        }

        override fun setMacCreadySetting(value: Double) = Unit

        override fun setMacCreadyRisk(value: Double) = Unit

        override fun setAutoMcEnabled(enabled: Boolean) = Unit

        override fun setTotalEnergyCompensationEnabled(enabled: Boolean) = Unit

        override fun setFlightMode(mode: FlightMode) = Unit

        override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) = Unit

        override fun stop() = Unit
    }
}
