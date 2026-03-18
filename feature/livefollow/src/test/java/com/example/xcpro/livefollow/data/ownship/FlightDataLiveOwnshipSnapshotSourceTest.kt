package com.example.xcpro.livefollow.data.ownship

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowValueState
import com.example.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlightDataLiveOwnshipSnapshotSourceTest {

    @Test
    fun snapshot_mapsLiveOwnshipFieldsFromFlightDataRepository() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.update(
            data = sampleFlightData(
                fixMonoMs = 12_345L,
                wallMs = 98_765L,
                gpsAccuracyMeters = 6f
            )
        )
        advanceUntilIdle()

        val snapshot = source.snapshot.value
        assertNotNull(snapshot)
        assertEquals(LiveFollowRuntimeMode.LIVE, source.runtimeMode.value)
        assertEquals(LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME, snapshot?.sourceLabel)
        assertEquals(12_345L, snapshot?.fixMonoMs)
        assertEquals(98_765L, snapshot?.fixWallMs)
        assertEquals(151.25, snapshot?.pressureAltitudeMslMeters)
        assertEquals(152.5, snapshot?.gpsAltitudeMslMeters)
        assertEquals(13.2, snapshot?.groundSpeedMs)
        assertEquals(182.0, snapshot?.trackDeg)
        assertEquals(1.8, snapshot?.verticalSpeedMs)
        assertEquals(LiveFollowValueState.VALID, snapshot?.positionQuality?.state)
        assertEquals(LiveFollowConfidence.HIGH, snapshot?.positionQuality?.confidence)
        assertEquals(LiveFollowAircraftIdentityType.FLARM, snapshot?.canonicalIdentity?.type)
        assertEquals("FLARM:AB12CD", snapshot?.canonicalIdentity?.canonicalKey)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshot_usesReplayLabelWhenReplaySourceIsActive() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.setActiveSource(FlightDataRepository.Source.REPLAY)
        repository.update(
            data = sampleFlightData(fixMonoMs = 4_321L),
            source = FlightDataRepository.Source.REPLAY
        )
        advanceUntilIdle()

        assertEquals(LiveFollowRuntimeMode.REPLAY, source.runtimeMode.value)
        assertEquals(LiveOwnshipSourceLabel.REPLAY_RUNTIME, source.snapshot.value?.sourceLabel)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshot_dropsSamplesWithoutMonotonicFixTime() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.update(
            data = sampleFlightData(fixMonoMs = 0L, wallMs = 22_000L)
        )
        advanceUntilIdle()

        assertNull(source.snapshot.value)
        assertEquals(LiveFollowRuntimeMode.LIVE, source.runtimeMode.value)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun sampleFlightData(
        fixMonoMs: Long,
        wallMs: Long = 12_000L,
        gpsAccuracyMeters: Float = 6f
    ): CompleteFlightData {
        return CompleteFlightData(
            gps = GPSData(
                position = GeoPoint(latitude = -33.9, longitude = 151.2),
                altitude = AltitudeM(152.5),
                speed = SpeedMs(13.2),
                bearing = 182.0,
                accuracy = gpsAccuracyMeters,
                timestamp = wallMs,
                monotonicTimestampMillis = fixMonoMs
            ),
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(151.25),
            qnh = PressureHpa(1012.8),
            isQNHCalibrated = true,
            verticalSpeed = VerticalSpeedMs(1.8),
            pressureAltitude = AltitudeM(149.0),
            baroGpsDelta = AltitudeM(-1.25),
            baroConfidence = ConfidenceLevel.HIGH,
            qnhCalibrationAgeSeconds = 5,
            agl = AltitudeM(45.0),
            thermalAverage = VerticalSpeedMs(0.7),
            currentLD = 32.0f,
            netto = VerticalSpeedMs(0.6),
            timestamp = wallMs,
            dataQuality = "GPS+BARO",
            varioValid = true
        )
    }
}
