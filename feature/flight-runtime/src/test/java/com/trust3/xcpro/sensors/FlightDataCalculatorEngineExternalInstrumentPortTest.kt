package com.trust3.xcpro.sensors

import com.trust3.xcpro.audio.VarioAudioControllerPort
import com.trust3.xcpro.audio.VarioAudioSettings
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.external.ExternalFlightSettingsReadPort
import com.trust3.xcpro.external.ExternalFlightSettingsSnapshot
import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.ExternalInstrumentReadPort
import com.trust3.xcpro.external.TimedExternalValue
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.hawk.HawkAudioVarioReadPort
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import com.trust3.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FlightDataCalculatorEngineExternalInstrumentPortTest {

    @Test
    fun engine_collects_external_read_port_and_only_approved_fields_reach_calculation() = runTest {
        val externalPort = FakeExternalInstrumentReadPort()
        val harness = engineHarness(externalPort, StandardTestDispatcher(testScheduler))
        val engine = harness.engine

        externalPort.snapshot.value = ExternalInstrumentFlightSnapshot(
            pressureAltitudeM = TimedExternalValue(1_010.0, 4_900L),
            totalEnergyVarioMps = TimedExternalValue(1.8, 4_900L)
        )
        advanceUntilIdle()
        assertNotNull(engine.latestExternalInstrumentSnapshot.pressureAltitudeM)
        assertEquals(1_010.0, engine.latestExternalInstrumentSnapshot.pressureAltitudeM?.value ?: Double.NaN, 1e-6)

        engine.updateGPSData(gpsSample(timestampMillis = 5_000L), null)
        advanceUntilIdle()

        val flightData = engine.flightDataFlow.value!!
        assertEquals(1_010.0, flightData.pressureAltitude.value, 1e-6)
        assertEquals(1.8, flightData.teVario?.value ?: Double.NaN, 1e-6)
        assertEquals("GPS", flightData.airspeedSource)

        harness.close()
    }

    @Test
    fun disconnect_like_empty_snapshot_clears_effective_external_inputs() = runTest {
        val externalPort = FakeExternalInstrumentReadPort()
        val harness = engineHarness(externalPort, StandardTestDispatcher(testScheduler))
        val engine = harness.engine

        externalPort.snapshot.value = ExternalInstrumentFlightSnapshot(
            pressureAltitudeM = TimedExternalValue(1_010.0, 4_900L),
            totalEnergyVarioMps = TimedExternalValue(1.8, 4_900L)
        )
        advanceUntilIdle()
        assertEquals(1_010.0, engine.latestExternalInstrumentSnapshot.pressureAltitudeM?.value ?: Double.NaN, 1e-6)
        engine.updateGPSData(gpsSample(timestampMillis = 5_000L), null)
        advanceUntilIdle()
        assertEquals(1_010.0, engine.flightDataFlow.value!!.pressureAltitude.value, 1e-6)

        externalPort.snapshot.value = ExternalInstrumentFlightSnapshot()
        advanceUntilIdle()
        engine.updateGPSData(gpsSample(timestampMillis = 6_000L), null)
        advanceUntilIdle()

        val flightData = engine.flightDataFlow.value!!
        assertEquals(1_000.0, flightData.pressureAltitude.value, 1e-6)
        assertNull(flightData.teVario)

        harness.close()
    }

    @Test
    fun stale_external_inputs_stop_affecting_calculation() = runTest {
        val externalPort = FakeExternalInstrumentReadPort()
        val harness = engineHarness(externalPort, StandardTestDispatcher(testScheduler))
        val engine = harness.engine

        externalPort.snapshot.value = ExternalInstrumentFlightSnapshot(
            pressureAltitudeM = TimedExternalValue(1_010.0, 1_000L),
            totalEnergyVarioMps = TimedExternalValue(1.8, 1_000L)
        )
        advanceUntilIdle()

        engine.updateGPSData(gpsSample(timestampMillis = 5_000L), null)
        advanceUntilIdle()

        val flightData = engine.flightDataFlow.value!!
        assertEquals(1_000.0, flightData.pressureAltitude.value, 1e-6)
        assertNull(flightData.teVario)

        harness.close()
    }

    private fun engineHarness(
        externalPort: FakeExternalInstrumentReadPort,
        dispatcher: CoroutineDispatcher
    ): EngineHarness {
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val engine = FlightDataCalculatorEngine(
            sensorDataSource = FakeSensorDataSource(),
            airspeedDataSource = FakeAirspeedDataSource(),
            scope = scope,
            sinkProvider = NoOpStillAirSinkProvider,
            windStateFlow = MutableStateFlow(WindState()),
            flightStateSource = FakeFlightStateSource(FlyingState(isFlying = true)),
            audioController = FakeVarioAudioController(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            hawkAudioVarioReadPort = NoOpHawkAudioVarioReadPort,
            externalInstrumentReadPort = externalPort,
            externalFlightSettingsReadPort = NoOpExternalFlightSettingsReadPort,
            terrainElevationReadPort = NoOpTerrainElevationReadPort,
            isReplayMode = false
        )
        return EngineHarness(engine, scope)
    }

    private data class EngineHarness(
        val engine: FlightDataCalculatorEngine,
        val scope: CoroutineScope
    ) {
        fun close() {
            scope.coroutineContext.cancel()
        }
    }

    private class FakeSensorDataSource : SensorDataSource {
        override val gpsFlow: StateFlow<GPSData?> = MutableStateFlow(null)
        override val baroFlow: StateFlow<BaroData?> = MutableStateFlow(null)
        override val compassFlow: StateFlow<CompassData?> = MutableStateFlow(null)
        override val rawAccelFlow: StateFlow<RawAccelData?> = MutableStateFlow(null)
        override val accelFlow: StateFlow<AccelData?> = MutableStateFlow(null)
        override val attitudeFlow: StateFlow<AttitudeData?> = MutableStateFlow(null)
    }

    private class FakeAirspeedDataSource : AirspeedDataSource {
        override val airspeedFlow: StateFlow<AirspeedSample?> = MutableStateFlow(null)
    }

    private class FakeFlightStateSource(state: FlyingState) : FlightStateSource {
        override val flightState: StateFlow<FlyingState> = MutableStateFlow(state)
    }

    private class FakeVarioAudioController : VarioAudioControllerPort {
        override val settings: StateFlow<VarioAudioSettings> = MutableStateFlow(VarioAudioSettings())

        override fun update(teSample: Double?, rawVario: Double, currentTime: Long, validUntil: Long): Double? =
            rawVario

        override fun updateSettings(settings: VarioAudioSettings) = Unit

        override fun silence() = Unit

        override fun stop() = Unit
    }

    private class FakeExternalInstrumentReadPort : ExternalInstrumentReadPort {
        val snapshot = MutableStateFlow(ExternalInstrumentFlightSnapshot())
        override val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot> = snapshot
    }

    private object NoOpExternalFlightSettingsReadPort : ExternalFlightSettingsReadPort {
        override val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot> =
            MutableStateFlow(ExternalFlightSettingsSnapshot())
    }

    private object NoOpStillAirSinkProvider : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double? = null

        override fun iasBoundsMs(): SpeedBoundsMs? = null
    }

    private object NoOpHawkAudioVarioReadPort : HawkAudioVarioReadPort {
        override val audioVarioMps: Flow<Double?> = emptyFlow()
    }

    private object NoOpTerrainElevationReadPort : TerrainElevationReadPort {
        override suspend fun getElevationMeters(lat: Double, lon: Double): Double? = null
    }

    private fun gpsSample(timestampMillis: Long): GPSData =
        GPSData(
            position = GeoPoint(47.0, 13.0),
            altitude = AltitudeM(1_000.0),
            speed = SpeedMs(18.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = timestampMillis,
            monotonicTimestampMillis = timestampMillis
        )
}
