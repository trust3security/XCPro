package com.example.xcpro.replay

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.buildCompleteFlightData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.VarioDiagnosticsSample
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFinishRampTest {

    @Test
    fun non_finite_display_vario_does_not_emit_ramp() = runTest {
        val repo = FakeSensorFusionRepository(Double.NaN)

        emitFinishRampIfNeeded(
            lastPoint = lastPoint(),
            session = session(),
            simConfig = simConfig(),
            sampleEmitter = emitter(),
            replayFusionRepository = repo
        )

        assertTrue(repo.replayUpdates.isEmpty())
    }

    @Test
    fun display_vario_below_min_threshold_does_not_emit_ramp() = runTest {
        val repo = FakeSensorFusionRepository(0.04)

        emitFinishRampIfNeeded(
            lastPoint = lastPoint(),
            session = session(),
            simConfig = simConfig(),
            sampleEmitter = emitter(),
            replayFusionRepository = repo
        )

        assertTrue(repo.replayUpdates.isEmpty())
    }

    @Test
    fun display_vario_within_threshold_emits_positive_ramp_and_clears_itself() = runTest {
        val repo = FakeSensorFusionRepository(0.2)

        emitFinishRampIfNeeded(
            lastPoint = lastPoint(),
            session = session(),
            simConfig = simConfig(),
            sampleEmitter = emitter(),
            replayFusionRepository = repo
        )

        val nonNullUpdates = repo.replayUpdates.mapNotNull { it.first }
        assertTrue(nonNullUpdates.isNotEmpty())
        assertTrue(nonNullUpdates.first() > 0.0)
        assertEquals(null, repo.replayUpdates.last().first)
    }

    @Test
    fun display_vario_within_threshold_emits_negative_ramp_with_matching_sign() = runTest {
        val repo = FakeSensorFusionRepository(-0.2)

        emitFinishRampIfNeeded(
            lastPoint = lastPoint(),
            session = session(),
            simConfig = simConfig(),
            sampleEmitter = emitter(),
            replayFusionRepository = repo
        )

        val nonNullUpdates = repo.replayUpdates.mapNotNull { it.first }
        assertTrue(nonNullUpdates.isNotEmpty())
        assertTrue(nonNullUpdates.first() < 0.0)
        assertEquals(null, repo.replayUpdates.last().first)
    }

    @Test
    fun display_vario_above_max_threshold_does_not_emit_ramp() = runTest {
        val repo = FakeSensorFusionRepository(1.2)

        emitFinishRampIfNeeded(
            lastPoint = lastPoint(),
            session = session(),
            simConfig = simConfig(),
            sampleEmitter = emitter(),
            replayFusionRepository = repo
        )

        assertTrue(repo.replayUpdates.isEmpty())
    }

    private fun emitter(): ReplaySampleEmitter =
        ReplaySampleEmitter(
            replaySensorSource = ReplaySensorSource(),
            replayAirspeedRepository = ReplayAirspeedRepository(),
            simConfig = simConfig()
        )

    private fun session(): SessionState =
        SessionState(
            speedMultiplier = 1.0,
            startTimestampMillis = 1_000L,
            currentTimestampMillis = 1_000L,
            durationMillis = 10_000L,
            qnhHpa = 1013.25
        )

    private fun simConfig(): ReplaySimConfig =
        ReplaySimConfig(
            mode = ReplayMode.REALTIME_SIM,
            baroStepMs = 50L,
            gpsStepMs = 1_000L
        )

    private fun lastPoint(): IgcPoint =
        IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 1_000.0,
            pressureAltitude = 1_000.0
        )

    private class FakeSensorFusionRepository(
        displayVarioMs: Double
    ) : SensorFusionRepository {
        override val flightDataFlow: StateFlow<CompleteFlightData?> =
            MutableStateFlow(buildCompleteFlightData(displayVarioMs = displayVarioMs))
        override val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = MutableStateFlow(null)
        override val audioSettings: StateFlow<VarioAudioSettings> = MutableStateFlow(VarioAudioSettings())

        val replayUpdates = mutableListOf<Pair<Double?, Long>>()

        override fun updateAudioSettings(settings: VarioAudioSettings) = Unit
        override fun setHawkAudioEnabled(enabled: Boolean) = Unit
        override fun setManualQnh(qnhHPa: Double) = Unit
        override fun resetQnhToStandard() = Unit
        override fun setMacCreadySetting(value: Double) = Unit
        override fun setMacCreadyRisk(value: Double) = Unit
        override fun setAutoMcEnabled(enabled: Boolean) = Unit
        override fun setTotalEnergyCompensationEnabled(enabled: Boolean) = Unit
        override fun setFlightMode(mode: FlightMode) = Unit

        override fun updateReplayRealVario(realVarioMs: Double?, timestampMillis: Long) {
            replayUpdates += realVarioMs to timestampMillis
        }

        override fun stop() = Unit
    }
}
