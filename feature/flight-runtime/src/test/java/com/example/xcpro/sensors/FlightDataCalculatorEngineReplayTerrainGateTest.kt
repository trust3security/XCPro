package com.example.xcpro.sensors

import com.example.dfcards.dfcards.calculations.TerrainElevationReadPort
import com.example.xcpro.audio.VarioAudioControllerPort
import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.hawk.HawkAudioVarioReadPort
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.WindState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FlightDataCalculatorEngineReplayTerrainGateTest {

    @Test
    fun updateGpsData_inReplayMode_doesNotInvokeTerrainReadPort() = runTest {
        val terrainPort = CountingTerrainElevationReadPort()
        val engine = FlightDataCalculatorEngine(
            sensorDataSource = FakeSensorDataSource(),
            airspeedDataSource = FakeAirspeedDataSource(),
            scope = backgroundScope,
            sinkProvider = NoOpStillAirSinkProvider,
            windStateFlow = MutableStateFlow(WindState()),
            flightStateSource = FakeFlightStateSource(FlyingState(isFlying = true)),
            audioController = FakeVarioAudioController(),
            clock = FakeClock(monoMs = 1_000L, wallMs = 1_000L),
            hawkAudioVarioReadPort = NoOpHawkAudioVarioReadPort,
            terrainElevationReadPort = terrainPort,
            isReplayMode = true
        )

        engine.updateGPSData(
            gps = gpsSample(timestampMillis = 12_000L),
            compass = null
        )
        advanceUntilIdle()

        assertNotNull(engine.flightDataFlow.value)
        assertEquals(0, terrainPort.callCount)
    }

    private class CountingTerrainElevationReadPort : TerrainElevationReadPort {
        var callCount: Int = 0
            private set

        override suspend fun getElevationMeters(lat: Double, lon: Double): Double? {
            callCount += 1
            return 250.0
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

    private object NoOpStillAirSinkProvider : StillAirSinkProvider {
        override fun sinkAtSpeed(airspeedMs: Double): Double? = null

        override fun iasBoundsMs(): SpeedBoundsMs? = null
    }

    private object NoOpHawkAudioVarioReadPort : HawkAudioVarioReadPort {
        override val audioVarioMps: Flow<Double?> = emptyFlow()
    }

    private fun gpsSample(timestampMillis: Long): GPSData =
        GPSData(
            position = GeoPoint(47.0, 13.0),
            altitude = AltitudeM(1_200.0),
            speed = SpeedMs(18.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = timestampMillis,
            monotonicTimestampMillis = timestampMillis
        )
}
